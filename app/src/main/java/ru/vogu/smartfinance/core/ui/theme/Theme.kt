package ru.vogu.smartfinance.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Реально применённая (а не системная!) тёмная/светлая тема. [androidx.compose.foundation.isSystemInDarkTheme]
 * читает настройку ОС напрямую — после появления переключателя темы в шапке [ru.vogu.smartfinance.presentation.chat.ChatScreen]
 * (см. [ru.vogu.smartfinance.presentation.ThemeViewModel]) она может отличаться от того, что
 * пользователь явно выбрал в приложении. Цвет фона — надёжный признак: тёмный фон означает,
 * что сейчас отрисована [DarkColors], независимо от системной настройки.
 */
@Composable
private fun isEffectivelyDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

// Полный набор ролей ColorScheme, а не только "основные" — начиная с Material3 1.2 многие
// стандартные компоненты (Switch, Snackbar, BottomSheet/Card containers) по умолчанию берут
// surfaceContainer*/inverseSurface/outlineVariant, а не surface/onSurface. Если их не задать,
// они остаются на встроенной Material-палитре (светлой) независимо от темы — и любой наш
// светлый текст, оказавшийся на такой "чужой" светлой подложке, становится нечитаемым.
private val LightColors = lightColorScheme(
    primary = md_theme_primary,
    onPrimary = md_theme_onPrimary,
    primaryContainer = md_theme_primaryContainer,
    onPrimaryContainer = md_theme_onPrimaryContainer,
    secondary = md_theme_secondary,
    onSecondary = md_theme_onSecondary,
    secondaryContainer = md_theme_secondaryContainer,
    onSecondaryContainer = md_theme_onSecondaryContainer,
    error = md_theme_error,
    onError = md_theme_onError,
    errorContainer = md_theme_errorContainer,
    onErrorContainer = md_theme_onErrorContainer,
    background = md_theme_background,
    onBackground = md_theme_onBackground,
    surface = md_theme_surface,
    onSurface = md_theme_onSurface,
    surfaceVariant = md_theme_surfaceVariant,
    onSurfaceVariant = md_theme_onSurfaceVariant,
    outline = md_theme_outline,
    outlineVariant = neutral_100,
    surfaceTint = neutral_900,
    inverseSurface = neutral_900,
    inverseOnSurface = neutral_050,
    inversePrimary = neutral_050,
    surfaceContainerLowest = md_theme_surface,
    surfaceContainerLow = md_theme_surface,
    surfaceContainer = neutral_100,
    surfaceContainerHigh = neutral_100,
    surfaceContainerHighest = neutral_300,
    surfaceBright = md_theme_surface,
    surfaceDim = neutral_100
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = neutral_700,
    surfaceTint = neutral_050,
    inverseSurface = neutral_050,
    inverseOnSurface = neutral_900,
    inversePrimary = neutral_900,
    surfaceContainerLowest = md_theme_dark_background,
    surfaceContainerLow = md_theme_dark_surface,
    surfaceContainer = md_theme_dark_surface,
    surfaceContainerHigh = md_theme_dark_surfaceVariant,
    surfaceContainerHighest = md_theme_dark_outline,
    surfaceBright = md_theme_dark_surfaceVariant,
    surfaceDim = md_theme_dark_background
)

/** Скромные, чёткие скругления — японский минимализм не "пилюлит" всё подряд крупным радиусом. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

/** Цвет суммы дохода/расхода в Action Card — не входит в стандартную M3 ColorScheme, поэтому отдельная функция. */
@Composable
fun amountColor(isIncome: Boolean): androidx.compose.ui.graphics.Color {
    val dark = isEffectivelyDarkTheme()
    return when {
        isIncome && dark -> income_color_dark
        isIncome && !dark -> income_color
        !isIncome && dark -> expense_color_dark
        else -> expense_color
    }
}

/** Цвет предупреждения о приближении к лимиту бюджета (80%+). */
@Composable
fun warningColor(): androidx.compose.ui.graphics.Color =
    if (isEffectivelyDarkTheme()) warning_color_dark else warning_color

/** Фирменный градиент "hero"-карточки баланса на главном экране чата. */
@Composable
fun heroGradientBrush(): Brush {
    val (start, end) = if (isEffectivelyDarkTheme()) {
        hero_gradient_dark_start to hero_gradient_dark_end
    } else {
        hero_gradient_start to hero_gradient_end
    }
    return Brush.linearGradient(colors = listOf(start, end))
}

/**
 * Цвет текста поверх [heroGradientBrush] — сама карточка сознательно "инвертируется" между
 * темами (тёмный премиальный градиент на светлой теме, светлый бежевый — на тёмной, см.
 * [hero_gradient_start]/[hero_gradient_dark_start]), поэтому текст на ней тоже должен
 * инвертироваться. Раньше он был жёстко Color.White — на тёмной теме это читалось как
 * "светлый текст на светлой (бежевой) карточке", то есть фактически нечитаемо.
 */
@Composable
fun heroContentColor(): androidx.compose.ui.graphics.Color =
    if (isEffectivelyDarkTheme()) neutral_900 else neutral_050

@Composable
fun SmartFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Фирменная палитра важнее Material You: динамический цвет из обоев делает интерфейс
    // визуально "случайным" и непредсказуемым между устройствами — отключаем по умолчанию.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
