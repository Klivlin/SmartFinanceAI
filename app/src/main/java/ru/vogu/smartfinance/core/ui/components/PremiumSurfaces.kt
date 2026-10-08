package ru.vogu.smartfinance.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Единый стиль карточки для всего приложения (Action Card, счета, категории, бюджет) —
 * CLAUDE.md §6 (единообразный UI). Плоская "бумажная" поверхность без тени (референс
 * пользователя: японский минимализм держит глубину тонкой обводкой, а не elevation) —
 * hairline-граница читается на любом фоне и не утяжеляет карточку.
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        content()
    }
}

/**
 * Иконка категории/счёта — плоский однотонный кружок (без градиента/объёма), в духе
 * референса пользователя: мягкие круглые icon-button без теней и "стеклянных" эффектов.
 */
@Composable
fun DuotoneIconBadge(
    color: Color,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Логотип приложения — тонкое кольцо с ромбом в центре, тот же мотив, что и в [TitleRule]
 * (единственный повторяющийся графический акцент интерфейса, поэтому и выбран как основа
 * логотипа — а не отдельная, нигде больше не встречающаяся картинка). CLAUDE.md список
 * доработок: "убрать водяной знак, создать логотип и центрировать его на странице" — раньше
 * здесь была крупная иконка экрана в углу почти невидимой альфой; теперь — предметный знак
 * по центру. Тот же рисунок (кольцо + ромб, цвета primary/onBackground) переиспользован для
 * иконки приложения — см. res/drawable/ic_launcher_foreground.xml.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val ringColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
    val diamondColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.02f
        drawCircle(
            color = ringColor,
            radius = this.size.minDimension / 2f - strokeWidth,
            style = Stroke(width = strokeWidth)
        )
        val half = this.size.minDimension * 0.16f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val diamond = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, center.y - half)
            lineTo(center.x + half, center.y)
            lineTo(center.x, center.y + half)
            lineTo(center.x - half, center.y)
            close()
        }
        drawPath(path = diamond, color = diamondColor)
    }
}

/**
 * Декоративный разделитель под заголовком экрана — тонкая линия с маленьким ромбом по центру,
 * распространённый японский графический акцент (см. референс пользователя). Чисто визуальный
 * элемент, не несёт функции — используется под TopAppBar на второстепенных экранах.
 */
@Composable
fun TitleRule(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(6.dp)
                .rotate(45f)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    }
}
