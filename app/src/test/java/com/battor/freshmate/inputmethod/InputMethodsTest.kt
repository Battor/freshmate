package com.battor.freshmate.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputMethodsTest {
    @Test
    fun `手动输入没有附加操作`() {
        assertNull(ManualInputMethod.extraAction)
        assertEquals("手动输入", ManualInputMethod.menuLabel)
    }

    @Test
    fun `语音输入有附加操作`() {
        assertEquals("长按说话", VoiceInputMethod.extraAction?.label)
    }

    @Test
    fun `图片输入有附加操作`() {
        assertEquals("选择图片", ImageInputMethod.extraAction?.label)
    }
}
