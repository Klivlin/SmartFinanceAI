package ru.vogu.smartfinance.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import ru.vogu.smartfinance.core.common.Constants
import ru.vogu.smartfinance.core.ui.theme.SmartFinanceTheme
import ru.vogu.smartfinance.core.ui.theme.md_theme_background
import ru.vogu.smartfinance.core.ui.theme.md_theme_dark_background
import ru.vogu.smartfinance.core.work.ExpenseReminderWorker
import ru.vogu.smartfinance.domain.repository.ReminderSettingsRepository
import ru.vogu.smartfinance.presentation.navigation.SmartFinanceNavGraph
import javax.inject.Inject

/**
 * Единственная Activity приложения (single-Activity архитектура, Navigation Compose —
 * CLAUDE.md §6). NavGraph подключает все экраны приложения (Chat/Scanner/Analytics/Budget/
 * Accounts — см. NavGraph.kt), реализация которых завершена по шагам CLAUDE.md §9.5.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // По Activity, а не по экрану — чтобы переключатель темы в ChatScreen мгновенно
    // перекрашивал вообще всё дерево, включая другие вкладки нижней навигации.
    private val themeViewModel: ThemeViewModel by viewModels()

    @Inject
    lateinit var reminderSettingsRepository: ReminderSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Ежедневное напоминание "внести расходы" — CLAUDE.md §2.4/§5.2. Реагирует на
            // переключатель в "Настройках": KEEP-политика делает повторный scheduleDaily
            // безопасным (не пересоздаёт уже идущую работу), а cancelUniqueWork снимает её,
            // если пользователь явно выключил напоминания — без этой проверки следующий же
            // запуск Activity тут же вернул бы их обратно.
            val reminderEnabled by reminderSettingsRepository.isReminderEnabled.collectAsState(initial = true)
            LaunchedEffect(reminderEnabled) {
                val workManager = WorkManager.getInstance(applicationContext)
                if (reminderEnabled) {
                    ExpenseReminderWorker.scheduleDaily(workManager)
                } else {
                    workManager.cancelUniqueWork(Constants.WORK_TAG_EXPENSE_REMINDER)
                }
            }

            val darkThemeOverride by themeViewModel.darkThemeOverride.collectAsState()
            val effectiveDark = darkThemeOverride ?: isSystemInDarkTheme()

            // Фон ОКНА (не Compose-фон) должен совпадать с реальной темой приложения, а не
            // только с системной (values-night/) — иначе при ручном переключении темы, когда
            // она расходится с системной, между экранами на долю кадра проглядывает белый
            // windowBackground при смене конфигурации/пересоздании окна.
            LaunchedEffect(effectiveDark) {
                window.decorView.setBackgroundColor(
                    (if (effectiveDark) md_theme_dark_background else md_theme_background).toArgb()
                )
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { /* Отказ — просто не будет напоминаний; не блокируем остальной функционал. */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            SmartFinanceTheme(darkTheme = effectiveDark) {
                SmartFinanceNavGraph(
                    isDarkTheme = effectiveDark,
                    onToggleTheme = { themeViewModel.toggleTheme(effectiveDark) }
                )
            }
        }
    }
}
