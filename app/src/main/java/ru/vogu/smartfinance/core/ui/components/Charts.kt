package ru.vogu.smartfinance.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.vogu.smartfinance.core.common.formatAsCurrency
import ru.vogu.smartfinance.domain.model.CategorySpending
import ru.vogu.smartfinance.domain.model.DailySpendingPoint
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    // Диаграмма "открывается" по тапу — CLAUDE.md список доработок ("посмотреть подробности"):
    // свёрнутый вид показывает топ-6 категорий, как и раньше, а тап по кольцу или подписи
    // "Показать все" разворачивает список целиком, без ухода с экрана аналитики.
    var isExpanded by remember { mutableStateOf(false) }
    val visibleItems = if (isExpanded) breakdown else breakdown.take(6)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .aspectRatio(1f)
            ) {
                var startAngle = -90f
                // Тонкое кольцо вместо толстого "бублика" — японский минимализм: акцент линией
                // и воздухом, а не массой заливки.
                val strokeWidth = size.minDimension * 0.12f
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
                visibleItems.forEach { item ->
                    PieLegendRow(item)
                }
                if (!isExpanded && breakdown.size > 6) {
                    Text(
                        text = "Показать все (ещё ${breakdown.size - 6}) →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { isExpanded = true }
                    )
                }
            }
        }
        if (isExpanded) {
            Text(
                text = "Свернуть ↑",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { isExpanded = false }
            )
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
    val fillBrushColors = listOf(lineColor.copy(alpha = 0.10f), lineColor.copy(alpha = 0f))
    val maxAmountDouble = maxAmount.toDouble().coerceAtLeast(0.01)

    // Пинч-зум/пан по графику — CLAUDE.md список доработок ("приближать и отдалять"). scale —
    // во сколько раз сужено видимое окно точек; panFraction — левый край этого окна как доля
    // от всего диапазона (0..1). Оба сбрасываются при смене набора точек (смена периода) —
    // remember(points) пересоздаёт state вместо переноса зума с недели на год.
    var scale by remember(points) { mutableFloatStateOf(1f) }
    var panFraction by remember(points) { mutableFloatStateOf(0f) }

    val visibleCount = max(2, (points.size / scale).roundToInt())
    val maxStartIndex = max(0, points.size - visibleCount)
    val startIndex = (panFraction * maxStartIndex).roundToInt().coerceIn(0, maxStartIndex)
    val visiblePoints = points.subList(startIndex, min(points.size, startIndex + visibleCount))

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(points) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 8f)
                        scale = newScale
                        val newMaxStart = max(0, points.size - max(2, (points.size / newScale).roundToInt()))
                        if (newMaxStart > 0) {
                            val panDelta = -pan.x / size.width.toFloat()
                            panFraction = (panFraction + panDelta).coerceIn(0f, 1f)
                        } else {
                            panFraction = 0f
                        }
                    }
                }
        ) {
            val stepX = if (visiblePoints.size > 1) size.width / (visiblePoints.size - 1) else 0f
            val linePath = Path()
            val fillPath = Path()

            visiblePoints.forEachIndexed { index, point ->
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
            fillPath.lineTo(stepX * (visiblePoints.size - 1).coerceAtLeast(0), size.height)
            fillPath.close()

            drawPath(path = fillPath, brush = Brush.verticalGradient(fillBrushColors))
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            // Мелкие точки на каждом значении — деликатный графический акцент вместо голой
            // линии (референс пользователя: точки-разделители, тонкая ручная графика).
            visiblePoints.forEachIndexed { index, point ->
                val x = stepX * index
                val ratio = (point.amountBase.toDouble() / maxAmountDouble).toFloat().coerceIn(0f, 1f)
                val y = size.height - (size.height * ratio)
                drawCircle(color = lineColor, radius = 2.6f, center = Offset(x, y))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru", "RU"))
            Text(
                text = formatter.format(visiblePoints.first().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "макс. ${maxAmount.formatAsCurrency(baseCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatter.format(visiblePoints.last().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (scale > 1f) {
            Text(
                text = "Увеличено ×${"%.1f".format(scale)} — сведите пальцы, чтобы вернуть весь период",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
