package ru.vogu.smartfinance.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.domain.model.CategorySpending
import ru.vogu.smartfinance.domain.model.DailySpendingPoint
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NO_CATEGORY_COLOR = Color(0xFF9E9E9E)

/**
 * Круговая диаграмма расходов по категориям — CLAUDE.md §5.2 ("Круговая диаграмма расходов
 * (Pie Chart)"). Своя реализация на [Canvas]: в проекте нет отдельной библиотеки графиков
 * (см. gradle/libs.versions.toml — Шаг 1), а вводить новую зависимость ради одного экрана
 * ВКР-проекта означает выходить за рамки уже согласованного стека (CLAUDE.md §6).
 */
@Composable
fun CategoryPieChart(
    breakdown: List<CategorySpending>,
    baseCurrency: String,
    modifier: Modifier = Modifier
) {
    if (breakdown.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нет расходов за выбранный период",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = breakdown.fold(BigDecimal.ZERO) { sum, item -> sum + item.amountBase }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .aspectRatio(1f)
        ) {
            var startAngle = -90f
            val strokeWidth = size.minDimension * 0.28f
            breakdown.forEach { item ->
                val sweep = (item.percentage / 100.0 * 360.0).toFloat()
                drawArc(
                    color = item.category?.colorHex?.let { categoryColorFor(it) } ?: NO_CATEGORY_COLOR,
                    startAngle = startAngle,
                    sweepAngle = sweep.coerceAtLeast(0.6f),
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
                startAngle += sweep
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = "Всего: ${total.formatAsCurrency(baseCurrency)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            breakdown.take(6).forEach { item ->
                PieLegendRow(item)
            }
            if (breakdown.size > 6) {
                Text(
                    text = "и ещё ${breakdown.size - 6}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PieLegendRow(item: CategorySpending) {
    val color = item.category?.colorHex?.let { categoryColorFor(it) } ?: NO_CATEGORY_COLOR
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape)
        )
        Text(
            text = item.category?.name ?: "Без категории",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        )
        Text(
            text = "${item.percentage.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Линейный график динамики расходов по дням — CLAUDE.md §5.2 ("график динамики по дням").
 * Линия вместо столбцов сознательно: при периоде "Год" точек 365, и площадь под кривой
 * читается лучше плотных узких баров на маленьком экране телефона.
 */
@Composable
fun DailySpendingLineChart(
    points: List<DailySpendingPoint>,
    baseCurrency: String,
    modifier: Modifier = Modifier
) {
    val maxAmount = points.maxOfOrNull { it.amountBase } ?: BigDecimal.ZERO

    if (points.isEmpty() || maxAmount.signum() <= 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нет данных для графика",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillBrushColors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
    val maxAmountDouble = maxAmount.toDouble().coerceAtLeast(0.01)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
            val linePath = Path()
            val fillPath = Path()

            points.forEachIndexed { index, point ->
                val x = stepX * index
                val ratio = (point.amountBase.toDouble() / maxAmountDouble).toFloat().coerceIn(0f, 1f)
                val y = size.height - (size.height * ratio)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(stepX * (points.size - 1).coerceAtLeast(0), size.height)
            fillPath.close()

            drawPath(path = fillPath, brush = Brush.verticalGradient(fillBrushColors))
            drawPath(path = linePath, color = lineColor, style = Stroke(width = 4f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru", "RU"))
            Text(
                text = formatter.format(points.first().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "макс. ${maxAmount.formatAsCurrency(baseCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatter.format(points.last().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
