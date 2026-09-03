package com.battor.freshmate.ui.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 引导步骤推进（纯状态，UI 无关）：current 从 0 起，next() 在末步饱和。 */
class GuideStateMachine(private val stepCount: Int) {
    var current: Int by mutableIntStateOf(0)
        private set

    val isLast: Boolean get() = current == stepCount - 1

    fun next() {
        if (current < stepCount - 1) current++
    }
}
