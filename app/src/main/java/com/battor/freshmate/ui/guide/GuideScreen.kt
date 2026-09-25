package com.battor.freshmate.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import com.battor.freshmate.R
import com.battor.freshmate.ui.common.GuideKeys
import com.battor.freshmate.ui.common.GuideStateHolder
import com.battor.freshmate.ui.common.LocalGuideState
import com.battor.freshmate.ui.main.MainContent
import com.battor.freshmate.ui.main.NoopMainActions

/**
 * 引导页：mock 数据渲染完整主页界面 + 全屏引导层。
 * 交互回调全走 NoopMainActions：引导层已拦截触摸，空实现是第二道保险。
 * 完成/跳过/返回键都走 onExit(markCompletedOnExit)。
 */
@Composable
fun GuideScreen(
    markCompletedOnExit: Boolean,
    onExit: (Boolean) -> Unit,
) {
    // mock 名称按当前语言解析（语言切换重建 Activity，remember 键随之重算）
    val milkName = stringResource(R.string.guide_mock_milk)
    val yogurtName = stringResource(R.string.guide_mock_yogurt)
    val vegetableName = stringResource(R.string.guide_mock_vegetable)
    val mock = remember(milkName, yogurtName, vegetableName) {
        GuideMockContent(milkName, yogurtName, vegetableName)
    }
    val holder = remember { GuideStateHolder() }
    val machine = remember { GuideStateMachine(GuideSteps.size) }
    val context = LocalContext.current
    // 条件文案：通知被关才在第 4 步追加权限说明（读真实系统权限，非用户数据）
    val showPermissionNote = remember {
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exit = { onExit(markCompletedOnExit) }

    BackHandler(onBack = exit)

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalGuideState provides holder) {
            MainContent(
                state = mock.uiState,
                errorEvent = null,
                updateHint = null,
                actions = NoopMainActions,
                guideFirstCardKey = GuideKeys.FIRST_CARD,
                guideFirstBucketKey = GuideKeys.BUCKET_AREA,
                handleSystemBack = false,
            )
        }
        GuideOverlay(
            holder = holder,
            stepIndex = machine.current,
            showPermissionNote = showPermissionNote,
            onNext = { if (machine.isLast) exit() else machine.next() },
            onSkip = exit,
        )
    }
}
