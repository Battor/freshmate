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
import com.battor.freshmate.ui.main.MainContent

/**
 * 引导页：mock 数据渲染完整主页界面 + 全屏引导层。
 * 所有交互回调 no-op：引导层已拦截触摸，回调是第二道保险。
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
    val guideViewModel = remember(milkName, yogurtName, vegetableName) {
        GuideViewModel(milkName, yogurtName, vegetableName)
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
                state = guideViewModel.uiState,
                errorEvent = null,
                updateHint = null,
                onUpdateHintShown = {},
                onOpenUpdate = {},
                onOpenHistory = {},
                onOpenSettings = {},
                onStartEdit = {},
                onDelete = {},
                onUndoDelete = {},
                onDisperse = {},
                onStartNew = {},
                onSave = {},
                onBackToMethodSelection = {},
                onUpdateEditing = {},
                onPermissionRequested = {},
                onErrorShown = {},
                onRefreshNow = {},
                onConfirmPendingSave = {},
                onCancelPendingSave = {},
                guideFirstCardKey = "first_card",
                guideFirstBucketKey = "bucket_area",
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
