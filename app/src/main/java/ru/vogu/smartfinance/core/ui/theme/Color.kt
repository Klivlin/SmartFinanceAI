package ru.vogu.smartfinance.core.ui.theme

import androidx.compose.ui.graphics.Color

// Тёплая монохромная палитра — по образцу пользователя (кремовый фон, чёрный/графитовый
// текст, приглушённый бежевый акцент вместо яркого системного цвета; Bauhaus-минимализм,
// а не "ещё одно приложение из Material You"). Ровно 6 базовых оттенков переиспользуются
// между светлой и тёмной темой почти без остатка — см. комментарии у каждого блока.
val neutral_900 = Color(0xFF111111) // самый тёмный — текст на светлом / фон в тёмном
val neutral_700 = Color(0xFF333333) // карточки в тёмной теме
val neutral_500 = Color(0xFF777777) // вторичный текст — одинаков в обеих темах
val neutral_300 = Color(0xFFBEB8AD) // приглушённый бежевый акцент — одинаков в обеих темах
val neutral_100 = Color(0xFFE6E1D7) // вторичная поверхность в светлой теме
val neutral_050 = Color(0xFFF6F4EE) // кремовый фон / текст в тёмной теме

val md_theme_primary = neutral_900
val md_theme_onPrimary = neutral_050
val md_theme_primaryContainer = neutral_100
val md_theme_onPrimaryContainer = neutral_900

val md_theme_secondary = neutral_300
val md_theme_onSecondary = neutral_900
val md_theme_secondaryContainer = neutral_100
val md_theme_onSecondaryContainer = neutral_700

val md_theme_error = Color(0xFFB3423A)
val md_theme_onError = Color(0xFFFFFFFF)
val md_theme_errorContainer = Color(0xFFF3DEDB)
val md_theme_onErrorContainer = Color(0xFF5C1F1A)

val md_theme_background = neutral_050
val md_theme_onBackground = neutral_900
val md_theme_surface = Color(0xFFFFFFFF)
val md_theme_onSurface = neutral_900
val md_theme_surfaceVariant = neutral_100
val md_theme_onSurfaceVariant = neutral_500

val md_theme_outline = neutral_300

// "Hero"-карточка баланса — тёмный премиальный градиент (как физическая карта), а не
// цветной, чтобы не спорить с монохромной остальной палитрой.
val hero_gradient_start = neutral_700
val hero_gradient_end = neutral_900

// Семантика финансовых сумм — CLAUDE.md §5.1: доход зелёный, расход нейтральный. Приглушённые
// (не кислотные) оттенки — единственное цветовое пятно в остальном монохромном интерфейсе.
val income_color = Color(0xFF4F7358)
val expense_color = neutral_900
val warning_color = Color(0xFFA97C3D)

// Тёмная тема: те же 6 оттенков, роли инвертированы (900 становится фоном, 050 — текстом).
val md_theme_dark_primary = neutral_050
val md_theme_dark_onPrimary = neutral_900
val md_theme_dark_primaryContainer = neutral_700
val md_theme_dark_onPrimaryContainer = neutral_050

val md_theme_dark_secondary = neutral_300
val md_theme_dark_onSecondary = neutral_900
val md_theme_dark_secondaryContainer = neutral_700
val md_theme_dark_onSecondaryContainer = neutral_050

val md_theme_dark_error = Color(0xFFD9776E)
val md_theme_dark_onError = neutral_900
val md_theme_dark_errorContainer = Color(0xFF4A1712)
val md_theme_dark_onErrorContainer = Color(0xFFF3DEDB)

val md_theme_dark_background = neutral_900
val md_theme_dark_onBackground = neutral_050
val md_theme_dark_surface = neutral_700
val md_theme_dark_onSurface = neutral_050
val md_theme_dark_surfaceVariant = Color(0xFF262626)
val md_theme_dark_onSurfaceVariant = neutral_500

val md_theme_dark_outline = Color(0xFF474747)

// Тёплый бежевый градиент на тёмном фоне — тот же приём "премиальной карты", но инверсия.
val hero_gradient_dark_start = neutral_300
val hero_gradient_dark_end = neutral_100

val income_color_dark = Color(0xFF7FA588)
val expense_color_dark = neutral_050
val warning_color_dark = Color(0xFFCB9C5F)
