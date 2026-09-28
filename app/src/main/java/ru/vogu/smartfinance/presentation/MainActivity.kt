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
 * CLAUDE.md §6). NavGraph подключает все экраны приложения (Chat/Scanner/Analytics/Budget/
 * Accounts — см. NavGraph.kt), реализация которых завершена по шагам CLAUDE.md §9.5.
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
