package com.battor.freshmate.inputmethod

import com.battor.freshmate.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputMethodsTest {
    @Test
    fun `手动输入没有附加操作`() {
        assertNull(ManualInputMethod.extraAction)
        assertEquals(R.string.input_manual, ManualInputMethod.menuLabelRes)
    }

    @Test
    fun `语音输入有附加操作`() {
        assertEquals(R.string.extra_voice_hint, VoiceInputMethod.extraAction?.labelRes)
    }

    @Test
    fun `图片输入有附加操作`() {
        assertEquals(R.string.extra_image_hint, ImageInputMethod.extraAction?.labelRes)
    }
}
