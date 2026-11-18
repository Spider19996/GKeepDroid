package com.gkeepdroid

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app's own language setting: a language tag such as "de", or empty to follow the system. */
object LocaleHelper {
    /** The languages the app is translated into: tag and the name in that language. */
    val languages = listOf(
        "en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "it" to "Italiano",
        "pt" to "Português", "nl" to "Nederlands", "pl" to "Polski", "ru" to "Русский", "tr" to "Türkçe",
        "zh" to "中文", "ja" to "日本語", "ko" to "한국어", "ar" to "العربية", "hi" to "हिन्दी",
    )

    private fun chosen(context: Context): Locale? =
        SettingsStore.loadGlobal(context).language.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it) }

    /** A context that uses the chosen language, for an activity's attachBaseContext. */
    fun wrap(base: Context): Context {
        val locale = chosen(base) ?: return base
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    /** Applies the language to the application's own resources, which widgets and notifications use. */
    @Suppress("DEPRECATION")
    fun applyToApp(context: Context) {
        val app = context.applicationContext
        val locale = chosen(app) ?: Locale.getDefault()
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
    }
}
