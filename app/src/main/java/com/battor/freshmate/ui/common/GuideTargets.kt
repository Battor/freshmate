package com.battor.freshmate.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 聚光目标注册：目标控件挂 [guideTarget]，把自身 bounds（root 坐标）报进来。
 * 挂在 CompositionLocal 上——主流程里 LocalGuideState 为 null，guideTarget 原样返回，零开销。
 * 放 ui/common：被聚光的页面（ui.main）不反向依赖引导模块（ui.guide），只依赖这套中性机制。
 */
class GuideStateHolder {
    val targets = mutableStateMapOf<String, Rect>()
}

val LocalGuideState = compositionLocalOf<GuideStateHolder?> { null }

/** 聚光目标 key：MainScreen（挂点）与 GuideOverlay（消费点）共用，收口防拼错静默失效。 */
object GuideKeys {
    const val FAB = "fab"
    const val FIRST_CARD = "first_card"
    const val BUCKET_AREA = "bucket_area"
    const val TOPBAR = "topbar"
}

/**
 * @Composable 修饰符工厂：引导态把 bounds 报到 holder，主流程（holder 为 null）原样返回。
 * 必须是 @Composable 才能读 CompositionLocal，避免 composed{} 的性能与限制问题。
 * onDispose 摘除旧 bounds：目标控件销毁后不留过期 Rect（复用到可滚动/动态界面时的前提）。
 */
@Composable
fun Modifier.guideTarget(key: String): Modifier {
    val holder = LocalGuideState.current ?: return this
    DisposableEffect(holder, key) {
        onDispose { holder.targets.remove(key) }
    }
    return onGloballyPositioned { holder.targets[key] = it.boundsInRoot() }
}
