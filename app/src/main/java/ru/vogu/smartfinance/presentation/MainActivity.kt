package ru.vogu.smartfinance.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import ru.vogu.smartfinance.core.ui.theme.SmartFinanceTheme

/**
 * Единственная Activity приложения (single-Activity архитектура, Navigation Compose).
 *
 * NavGraph с полноценным чат-экраном, сканером и аналитикой подключается в Шаге 4
 * (presentation/navigation/NavGraph.kt) — сейчас здесь временная точка входа,
 * подтверждающая, что DI/Theme/Manifest корректно связаны.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFinanceTheme {
                SmartFinanceAppRoot()
            }
        }
    }
}

@Composable
private fun SmartFinanceAppRoot() {
    Scaffold { innerPadding ->
        Text(
            text = "SmartFinance AI — Шаг 1 готов. NavGraph и ChatScreen — Шаг 4.",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        )
    }
}
