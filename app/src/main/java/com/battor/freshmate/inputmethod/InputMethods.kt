package com.battor.freshmate.inputmethod

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.ui.graphics.vector.ImageVector

enum class InputMethodId { MANUAL, VOICE, IMAGE }

/** 输入方式的附加操作（语音/图片各多一个图标）；手动输入为 null。 */
data class ExtraAction(val icon: ImageVector, val label: String)

/** 输入方式策略接口（设计文档 §5.3）。v1 语音/图片的 extraAction 行为由 UI 层占位实现。 */
interface InputMethod {
    val id: InputMethodId
    val menuIcon: ImageVector
    val menuLabel: String
    val extraAction: ExtraAction?
}

object ManualInputMethod : InputMethod {
    override val id = InputMethodId.MANUAL
    override val menuIcon = Icons.Filled.Edit
    override val menuLabel = "手动输入"
    override val extraAction: ExtraAction? = null
}

object VoiceInputMethod : InputMethod {
    override val id = InputMethodId.VOICE
    override val menuIcon = Icons.Filled.KeyboardVoice
    override val menuLabel = "语音输入"
    override val extraAction = ExtraAction(Icons.Filled.KeyboardVoice, "长按说话")
}

object ImageInputMethod : InputMethod {
    override val id = InputMethodId.IMAGE
    override val menuIcon = Icons.Filled.Image
    override val menuLabel = "图片输入"
    override val extraAction = ExtraAction(Icons.Filled.Image, "选择图片")
}

object InputMethods {
    val all: List<InputMethod> = listOf(ManualInputMethod, VoiceInputMethod, ImageInputMethod)
    fun byId(id: InputMethodId): InputMethod = all.first { it.id == id }
}
