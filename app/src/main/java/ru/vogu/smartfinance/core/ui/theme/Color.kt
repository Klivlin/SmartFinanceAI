package ru.vogu.smartfinance.core.ui.theme

import androidx.compose.ui.graphics.Color

// Базовая палитра. Финальная система цветов (в т.ч. семантика "доход/расход" для
// Action Card транзакций) уточняется в Шаге 4 вместе с ChatScreen.

val md_theme_primary = Color(0xFF1B6B4A)
val md_theme_onPrimary = Color(0xFFFFFFFF)
val md_theme_primaryContainer = Color(0xFFA6F2CB)
val md_theme_onPrimaryContainer = Color(0xFF002114)

val md_theme_secondary = Color(0xFF4C6358)
val md_theme_onSecondary = Color(0xFFFFFFFF)
val md_theme_secondaryContainer = Color(0xFFCEE9D9)
val md_theme_onSecondaryContainer = Color(0xFF092017)

val md_theme_error = Color(0xFFBA1A1A)
val md_theme_onError = Color(0xFFFFFFFF)
val md_theme_errorContainer = Color(0xFFFFDAD6)
val md_theme_onErrorContainer = Color(0xFF410002)

val md_theme_background = Color(0xFFFBFDF9)
val md_theme_onBackground = Color(0xFF191C1A)
val md_theme_surface = Color(0xFFFBFDF9)
val md_theme_onSurface = Color(0xFF191C1A)
val md_theme_surfaceVariant = Color(0xFFDCE5DD)
val md_theme_onSurfaceVariant = Color(0xFF404943)

val md_theme_outline = Color(0xFF707973)

// Семантика финансовых сумм — CLAUDE.md §5.1: доход зелёный, расход дефолтный (нейтральный).
val income_color = Color(0xFF1B8A4A)
val expense_color = Color(0xFF191C1A)

// Тёмная тема
val md_theme_dark_primary = Color(0xFF8AD6AF)
val md_theme_dark_onPrimary = Color(0xFF00391F)
val md_theme_dark_primaryContainer = Color(0xFF00522F)
val md_theme_dark_onPrimaryContainer = Color(0xFFA6F2CB)

val md_theme_dark_background = Color(0xFF191C1A)
val md_theme_dark_onBackground = Color(0xFFE1E3DF)
val md_theme_dark_surface = Color(0xFF191C1A)
val md_theme_dark_onSurface = Color(0xFFE1E3DF)

val income_color_dark = Color(0xFF7FDB9E)
val expense_color_dark = Color(0xFFE1E3DF)
