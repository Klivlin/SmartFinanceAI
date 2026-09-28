package ru.vogu.smartfinance.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.toColorInt

/**
 * Сопоставление [ru.vogu.smartfinance.domain.model.Category.iconName] (см. DefaultCategories —
 * CLAUDE.md §3.3, §7) с иконками Material — используется в Action Card и BottomSheet-редакторе.
 * Незнакомое имя (пользовательская категория без явной иконки) → нейтральная [Icons.Filled.Category].
 */
fun categoryIconFor(iconName: String): ImageVector = when (iconName) {
    "restaurant" -> Icons.Filled.Restaurant
    "directions_car" -> Icons.Filled.DirectionsCar
    "local_cafe" -> Icons.Filled.LocalCafe
    "bolt" -> Icons.Filled.Bolt
    "theater_comedy" -> Icons.Filled.TheaterComedy
    "favorite" -> Icons.Filled.Favorite
    "swap_horiz" -> Icons.Filled.SwapHoriz
    "payments" -> Icons.Filled.Payments
    else -> Icons.Filled.Category
}

/** Парсит [ru.vogu.smartfinance.domain.model.Category.colorHex] ("#RRGGBB"); при повреждённых данных — нейтральный серый. */
fun categoryColorFor(colorHex: String): Color = try {
    Color(colorHex.toColorInt())
} catch (e: IllegalArgumentException) {
    Color(0xFF9E9E9E)
}
