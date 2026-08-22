package com.battor.freshmate.ui.settings

import androidx.core.os.LocaleListCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `空列表映射为跟随系统`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocales(LocaleListCompat.getEmptyLocaleList()))
    }

    @Test
    fun `语言标签映射`() {
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("zh-CN")),
        )
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("zh-TW")),
        )
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("en")))
        // 系统可能回传带地区的英语
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("en-US")))
    }

    @Test
    fun `不认识的语言映射为跟随系统`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocales(LocaleListCompat.forLanguageTags("ja")))
    }
}
