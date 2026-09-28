package ru.vogu.smartfinance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.vogu.smartfinance.core.ui.theme.SmartFinanceTheme
import ru.vogu.smartfinance.presentation.navigation.SmartFinanceNavGraph

/**
 * Единственная Activity приложения (single-Activity архитектура, Navigation Compose —
 * CLAUDE.md §6). NavGraph подключает главный Chat-экран (Шаг 4); Scanner/Accounts/Analytics
 * добавляются в граф по мере готовности (Шаги 5-6 — см. NavGraph.kt).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFinanceTheme {
                SmartFinanceNavGraph()
            }
        }
    }
}
