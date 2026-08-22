package com.battor.freshmate.inputmethod

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.ui.graphics.vector.ImageVector
import com.battor.freshmate.R

enum class InputMethodId { MANUAL, VOICE, IMAGE }

/** 输入方式的附加操作（语音/图片各多一个图标）；手动输入为 null。 */
data class ExtraAction(val icon: ImageVector, @StringRes val labelRes: Int)

/** 输入方式策略接口（设计文档 §5.3）。v1 语音/图片的 extraAction 行为由 UI 层占位实现。 */
interface InputMethod {
    val id: InputMethodId
    val menuIcon: ImageVector
    @get:StringRes val menuLabelRes: Int
    val extraAction: ExtraAction?
}

object ManualInputMethod : InputMethod {
    override val id = InputMethodId.MANUAL
    override val menuIcon = Icons.Filled.Edit
    override val menuLabelRes = R.string.input_manual
    override val extraAction: ExtraAction? = null
}

object VoiceInputMethod : InputMethod {
    override val id = InputMethodId.VOICE
    override val menuIcon = Icons.Filled.KeyboardVoice
    override val menuLabelRes = R.string.input_voice
    override val extraAction = ExtraAction(Icons.Filled.KeyboardVoice, R.string.extra_voice_hint)
}

object ImageInputMethod : InputMethod {
    override val id = InputMethodId.IMAGE
    override val menuIcon = Icons.Filled.Image
    override val menuLabelRes = R.string.input_image
    override val extraAction = ExtraAction(Icons.Filled.Image, R.string.extra_image_hint)
}

object InputMethods {
    val all: List<InputMethod> = listOf(ManualInputMethod, VoiceInputMethod, ImageInputMethod)
    fun byId(id: InputMethodId): InputMethod = all.first { it.id == id }
}
