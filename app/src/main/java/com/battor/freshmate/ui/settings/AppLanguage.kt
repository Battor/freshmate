package com.battor.freshmate.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 应用语言（需求-4）：SYSTEM=跟随系统（默认），其余为 BCP-47 标签。
 * 经 AppCompatDelegate.setApplicationLocales 生效：API 33+ 走系统 LocaleManager，
 * API < 33 由 manifest 里 AppLocalesMetadataHolderService(autoStoreLocales) 自动持久化。
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SIMPLIFIED_CHINESE("zh-CN"),
    TRADITIONAL_CHINESE("zh-TW"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromLocales(locales: LocaleListCompat): AppLanguage {
            if (locales.isEmpty) return SYSTEM
            val locale = locales[0] ?: return SYSTEM
            return when {
                locale.language == "zh" &&
                    (locale.country.equals("TW", ignoreCase = true) || locale.script.equals("Hant", ignoreCase = true)) ->
                    TRADITIONAL_CHINESE
                locale.language == "zh" -> SIMPLIFIED_CHINESE
                locale.language == "en" -> ENGLISH
                else -> SYSTEM
            }
        }

        fun apply(language: AppLanguage) {
            val locales = language.tag?.let(LocaleListCompat::forLanguageTags)
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
