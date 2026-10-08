package ru.vogu.smartfinance.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Flatware
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.toColorInt
import ru.vogu.smartfinance.core.ui.icons.AppIcons

/**
 * Сопоставление [ru.vogu.smartfinance.domain.model.Category.iconName] (см. DefaultCategories —
 * CLAUDE.md §3.3, §7) с иконками Material — используется в Action Card и BottomSheet-редакторе.
 * Незнакомое имя (пользовательская категория без явной иконки) → нейтральная [AppIcons.Other].
 */
fun categoryIconFor(iconName: String): ImageVector =
    CATEGORY_ICON_CHOICES.firstOrNull { it.first == iconName }?.second ?: AppIcons.Other

/** Парсит [ru.vogu.smartfinance.domain.model.Category.colorHex] ("#RRGGBB"); при повреждённых данных — нейтральный серый. */
fun categoryColorFor(colorHex: String): Color = try {
    Color(colorHex.toColorInt())
} catch (e: IllegalArgumentException) {
    Color(0xFF9E9E9E)
}

/**
 * Полный набор иконок, доступных пользователю в редакторе внешнего вида категории
 * (CLAUDE.md §5.2 "Управление категориями"). Порядок — как в сетке выбора.
 */
val CATEGORY_ICON_CHOICES: List<Pair<String, ImageVector>> = listOf(
    "restaurant" to Icons.Filled.Restaurant,
    "shopping_cart" to Icons.Filled.ShoppingCart,
    "shopping_bag" to Icons.Filled.ShoppingBag,
    "directions_car" to Icons.Filled.DirectionsCar,
    "local_gas_station" to Icons.Filled.LocalGasStation,
    "local_shipping" to Icons.Filled.LocalShipping,
    "flight" to Icons.Filled.Flight,
    "local_cafe" to Icons.Filled.LocalCafe,
    "local_bar" to Icons.Filled.LocalBar,
    "local_movies" to Icons.Filled.LocalMovies,
    "sports_esports" to Icons.Filled.SportsEsports,
    "theater_comedy" to Icons.Filled.TheaterComedy,
    "explore" to Icons.Filled.Explore,
    "bolt" to Icons.Filled.Bolt,
    "home" to Icons.Filled.Home,
    "favorite" to Icons.Filled.Favorite,
    "local_hospital" to Icons.Filled.LocalHospital,
    "local_pharmacy" to Icons.Filled.LocalPharmacy,
    "fitness_center" to Icons.Filled.FitnessCenter,
    "pets" to Icons.Filled.Pets,
    "school" to Icons.Filled.School,
    "work" to Icons.Filled.Work,
    "smartphone" to Icons.Filled.Smartphone,
    "smart_toy" to Icons.Filled.SmartToy,
    "card_giftcard" to Icons.Filled.CardGiftcard,
    "redeem" to Icons.Filled.Redeem,
    "savings" to Icons.Filled.Savings,
    "payments" to Icons.Filled.Payments,
    "swap_horiz" to Icons.Filled.SwapHoriz,
    "account_balance" to Icons.Filled.AccountBalance,
    "account_balance_wallet" to Icons.Filled.AccountBalanceWallet,
    "trending_up" to Icons.Filled.TrendingUp,
    "handshake" to Icons.Filled.Handshake,
    "diamond" to Icons.Filled.Diamond,
    "star" to Icons.Filled.Star,
    "volunteer_activism" to Icons.Filled.VolunteerActivism,
    "coffee" to Icons.Filled.Coffee,
    "flatware" to Icons.Filled.Flatware,
    "directions_bus" to Icons.Filled.DirectionsBus,
    "hotel" to Icons.Filled.Hotel,
    "checkroom" to Icons.Filled.Checkroom,
    "child_care" to Icons.Filled.ChildCare,
    "face" to Icons.Filled.Face,
    "spa" to Icons.Filled.Spa,
    "cleaning_services" to Icons.Filled.CleaningServices,
    "build" to Icons.Filled.Build,
    "laptop" to Icons.Filled.Laptop,
    "book" to Icons.Filled.Book,
    "music_note" to Icons.Filled.MusicNote,
    "sports_basketball" to Icons.Filled.SportsBasketball,
    "park" to Icons.Filled.Park,
    "local_florist" to Icons.Filled.LocalFlorist,
    "water_drop" to Icons.Filled.WaterDrop,
    "category" to AppIcons.Other
)

/**
 * Палитра цветов для редактора категории — приглушённые "земляные" тона вместо ярких
 * Material-цветов, в тон остальному монохромному интерфейсу (core/ui/theme/Color.kt,
 * DefaultCategories.seed). Читаемы и в светлой, и в тёмной теме.
 */
val CATEGORY_COLOR_CHOICES: List<String> = listOf(
    "#C17A56", "#B97C77", "#A67F94", "#8E7CA6", "#7C8FB0",
    "#6D8CA0", "#6B9FA3", "#6F9E8D", "#7C9473", "#6F8F5C",
    "#9BA05A", "#BFA05A", "#B0895A", "#8D6E63", "#8A8378",
    "#9E9891", "#A3978A", "#B0AFA6",
    "#A0522D", "#C2703D", "#B85C5C", "#9C5A8A", "#5D7A9E",
    "#4F8A8B", "#5A8F6E", "#8A9A4E", "#A88A3E", "#6E6259"
)
