package ru.vogu.smartfinance.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Собственный набор тонких контурных иконок — CLAUDE.md §6 (единообразный UI). Material Icons
 * читаются как типовой Android-виджет; здесь — деликатные line-art силуэты без заливки, в духе
 * присланного пользователем японского минималистичного референса (тонкая линия, воздух, без
 * "стеклянных" эффектов и тяжёлых заливок). Icon() применяет tint поверх stroke-цвета ниже как
 * color filter — заданный тут Color.Black не имеет значения для итогового цвета на экране,
 * важна только форма контура. Все иконки — единая тонкая линия (STROKE_WIDTH), без fill.
 */
object AppIcons {

    private const val STROKE_WIDTH = 1.3f

    private fun icon(
        name: String,
        fillType: PathFillType = PathFillType.NonZero,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = fillType,
                pathBuilder = block
            )
        }.build()

    private fun filledIcon(
        name: String,
        fillType: PathFillType = PathFillType.NonZero,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = fillType, pathBuilder = block)
        }.build()

    val Chat: ImageVector by lazy {
        icon("AppIconChat") {
            moveTo(4.5f, 5.5f)
            lineTo(19.5f, 5.5f)
            lineTo(19.5f, 15.5f)
            lineTo(10.2f, 15.5f)
            lineTo(7f, 18.7f)
            lineTo(7f, 15.5f)
            lineTo(4.5f, 15.5f)
            close()
        }
    }

    val Analytics: ImageVector by lazy {
        icon("AppIconAnalytics") {
            moveTo(4.5f, 19.5f)
            lineTo(4.5f, 15f)
            moveTo(9.5f, 19.5f)
            lineTo(9.5f, 10.5f)
            moveTo(14.5f, 19.5f)
            lineTo(14.5f, 6.5f)
            moveTo(19.5f, 19.5f)
            lineTo(19.5f, 4.5f)
        }
    }

    val Wallet: ImageVector by lazy {
        icon("AppIconWallet") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            lineTo(20f, 18f)
            lineTo(4f, 18f)
            close()
            moveTo(15f, 11.2f)
            lineTo(20f, 11.2f)
            lineTo(20f, 14.3f)
            lineTo(15f, 14.3f)
            close()
        }
    }

    val Categories: ImageVector by lazy {
        icon("AppIconCategories") {
            moveTo(4.5f, 4.5f); lineTo(10.2f, 4.5f); lineTo(10.2f, 10.2f); lineTo(4.5f, 10.2f); close()
            moveTo(13.8f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 10.2f); lineTo(13.8f, 10.2f); close()
            moveTo(4.5f, 13.8f); lineTo(10.2f, 13.8f); lineTo(10.2f, 19.5f); lineTo(4.5f, 19.5f); close()
            moveTo(13.8f, 13.8f); lineTo(19.5f, 13.8f); lineTo(19.5f, 19.5f); lineTo(13.8f, 19.5f); close()
        }
    }

    val Settings: ImageVector by lazy {
        icon("AppIconSettings") {
            // Три тонких "слайдера" — минималистичная замена шестерёнке.
            moveTo(4f, 6.5f); lineTo(20f, 6.5f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17.5f); lineTo(20f, 17.5f)
            moveTo(14.5f, 4.8f)
            arcTo(1.7f, 1.7f, 0f, true, true, 14.5f, 8.2f)
            arcTo(1.7f, 1.7f, 0f, true, true, 14.5f, 4.8f)
            close()
            moveTo(9f, 10.3f)
            arcTo(1.7f, 1.7f, 0f, true, true, 9f, 13.7f)
            arcTo(1.7f, 1.7f, 0f, true, true, 9f, 10.3f)
            close()
            moveTo(16.5f, 15.8f)
            arcTo(1.7f, 1.7f, 0f, true, true, 16.5f, 19.2f)
            arcTo(1.7f, 1.7f, 0f, true, true, 16.5f, 15.8f)
            close()
        }
    }

    val Camera: ImageVector by lazy {
        icon("AppIconCamera") {
            moveTo(3.5f, 7f)
            lineTo(8f, 7f)
            lineTo(9.5f, 4.5f)
            lineTo(14.5f, 4.5f)
            lineTo(16f, 7f)
            lineTo(20.5f, 7f)
            lineTo(20.5f, 19f)
            lineTo(3.5f, 19f)
            close()
            moveTo(15.5f, 13f)
            arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 13f)
            arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 13f)
            close()
        }
    }

    /** Тонкий шеврон вместо стандартной Material-стрелки — единый back-button на всех экранах. */
    val Back: ImageVector by lazy {
        icon("AppIconBack") {
            moveTo(14.5f, 5.5f)
            lineTo(8f, 12f)
            lineTo(14.5f, 18.5f)
        }
    }

    /** Такой же тонкий шеврон, развёрнутый вправо — переход внутрь (категория, счёт и т.п.). */
    val Forward: ImageVector by lazy {
        icon("AppIconForward") {
            moveTo(9.5f, 5.5f)
            lineTo(16f, 12f)
            lineTo(9.5f, 18.5f)
        }
    }

    /** Плюс тонкой линией — вместо жирного Material FAB-плюса. */
    val Plus: ImageVector by lazy {
        icon("AppIconPlus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    /**
     * "Без категории" / "Другое" — три маленькие закрашенные точки вместо стандартной
     * Material-иконки "Category" (треугольник+квадрат+круг), которая режет глаз, повторяясь
     * на каждой неопознанной операции. Каждая точка — заполненная окружность (двойной arcTo,
     * тот же проверенный приём, что и объектив камеры/слайдеры настроек выше).
     */
    val Other: ImageVector by lazy {
        filledIcon("AppIconOther") {
            moveTo(5f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 8f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 5f, 12f)
            close()
            moveTo(10.5f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 12f)
            close()
            moveTo(16f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 19f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 16f, 12f)
            close()
        }
    }

    /** Полумесяц — тема оформления. Кольцевая area-разница двух окружностей (EvenOdd). */
    /**
     * Полумесяц (тёмная тема). Две прошлые версии (два наложенных круга с EvenOdd-вычитанием)
     * на устройстве рендерились тонким кольцом, а не заливкой серпа — судя по всему, для двух
     * ПОЛНОСТЬЮ замкнутых перекрывающихся контуров с одинаковым направлением обхода EvenOdd
     * в используемом рендерере вырождается не в "круг минус круг", а в вырожденную тонкую
     * область на стыке контуров. Вместо дальнейшей вслепую правки геометрии — проверенный,
     * широко используемый контур серпа (Feather/Lucide "moon", единый незамкнутый-до-close
     * путь из двух дуг разного направления обхода, без наложения двух отдельных фигур), через
     * обычный [icon] (штрих, как и все остальные иконки набора) вместо [filledIcon].
     */
    val Moon: ImageVector by lazy {
        icon("AppIconMoon") {
            moveTo(21f, 12.79f)
            arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
            arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
            close()
        }
    }

    /**
     * Колокольчик — напоминания. Трапеция корпуса + язычок снизу, только прямые линии и дуга.
     * Центрирован по вертикали относительно viewport (0..24) — тело занимает y 7.3..16.5.
     */
    val Bell: ImageVector by lazy {
        icon("AppIconBell") {
            moveTo(12f, 1.8f); lineTo(12f, 3.5f)
            moveTo(7f, 16.5f)
            lineTo(8.2f, 7.3f)
            lineTo(15.8f, 7.3f)
            lineTo(17f, 16.5f)
            close()
            moveTo(10.2f, 16.5f)
            arcTo(1.8f, 1.8f, 0f, false, false, 13.8f, 16.5f)
        }
    }

    /**
     * Ключ — API-ключ. Кольцо-бородка вместо литературной формы Material-ключа. Центр кольца
     * на y=12 (было y=15.2 — заметно "съезжало" вниз относительно других иконок в ряду).
     */
    val Key: ImageVector by lazy {
        icon("AppIconKey") {
            moveTo(6.5f, 8.8f)
            arcTo(3.2f, 3.2f, 0f, true, true, 6.5f, 15.2f)
            arcTo(3.2f, 3.2f, 0f, true, true, 6.5f, 8.8f)
            close()
            moveTo(9.5f, 12f); lineTo(20f, 12f)
            moveTo(17f, 12f); lineTo(17f, 15f)
            moveTo(14f, 12f); lineTo(14f, 14f)
        }
    }

    /** Стрелка вниз в лоток — экспорт данных. */
    val Download: ImageVector by lazy {
        icon("AppIconDownload") {
            moveTo(12f, 4f); lineTo(12f, 15f)
            moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
            moveTo(4.5f, 19f); lineTo(19.5f, 19f)
        }
    }

    /** Три точки по вертикали — контекстное меню, тот же приём, что и [Other]. Равномерно
     *  распределены и центрированы вокруг y=12 (было 5/10.5/16 — сдвинуто вверх). */
    val MoreVertical: ImageVector by lazy {
        filledIcon("AppIconMoreVertical") {
            moveTo(10.5f, 6f)
            arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 6f)
            arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 6f)
            close()
            moveTo(10.5f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 12f)
            arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 12f)
            close()
            moveTo(10.5f, 18f)
            arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 18f)
            arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 18f)
            close()
        }
    }

    /**
     * Стопка монет — накопительный счёт, вместо мультяшной копилки. Центрирована по горизонтали
     * (x 7..17) — раньше была сдвинута в левую половину viewport (x 2..12), из-за чего казалась
     * "съехавшей" рядом с другими, симметричными иконками.
     */
    val Coins: ImageVector by lazy {
        icon("AppIconCoins") {
            moveTo(12f, 6.2f)
            arcTo(5f, 2f, 0f, true, true, 12f, 10.2f)
            arcTo(5f, 2f, 0f, true, true, 12f, 6.2f)
            close()
            moveTo(7f, 8.2f); lineTo(7f, 15.8f)
            moveTo(17f, 8.2f); lineTo(17f, 15.8f)
            moveTo(7f, 15.8f)
            arcTo(5f, 2f, 0f, false, false, 17f, 15.8f)
            moveTo(7f, 12f)
            arcTo(5f, 2f, 0f, false, false, 17f, 12f)
        }
    }

    /** Две противоположные стрелки — перевод между счетами. */
    val Swap: ImageVector by lazy {
        icon("AppIconSwap") {
            moveTo(4f, 8f); lineTo(18f, 8f)
            moveTo(14.5f, 4.5f); lineTo(18f, 8f); lineTo(14.5f, 11.5f)
            moveTo(20f, 16f); lineTo(6f, 16f)
            moveTo(9.5f, 12.5f); lineTo(6f, 16f); lineTo(9.5f, 19.5f)
        }
    }

    /** Карта — дебетовый/кредитный счёт. Прямоугольник + полоса, тот же язык, что и [Wallet]. */
    val Card: ImageVector by lazy {
        icon("AppIconCard") {
            moveTo(3.5f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 18f); lineTo(3.5f, 18f); close()
            moveTo(3.5f, 10f); lineTo(20.5f, 10f)
        }
    }

    /** Монета с чертой — наличные. */
    val Cash: ImageVector by lazy {
        icon("AppIconCash") {
            moveTo(12f, 4.5f)
            arcTo(7.5f, 7.5f, 0f, true, true, 12f, 19.5f)
            arcTo(7.5f, 7.5f, 0f, true, true, 12f, 4.5f)
            close()
            moveTo(12f, 8.5f); lineTo(12f, 15.5f)
            moveTo(9.7f, 10f); lineTo(14.3f, 10f)
        }
    }

    /** Тонкий шеврон вверх — переупорядочивание списков. */
    val ChevronUp: ImageVector by lazy {
        icon("AppIconChevronUp") {
            moveTo(6f, 14.5f); lineTo(12f, 8f); lineTo(18f, 14.5f)
        }
    }

    /** Тонкий шеврон вниз — переупорядочивание списков. */
    val ChevronDown: ImageVector by lazy {
        icon("AppIconChevronDown") {
            moveTo(6f, 9.5f); lineTo(12f, 16f); lineTo(18f, 9.5f)
        }
    }

    /** Галочка — выбор в сетке иконок/цветов. */
    val Check: ImageVector by lazy {
        icon("AppIconCheck") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
        }
    }

    /** Шестиугольник с диагональным крестом — криптовалютный счёт (узел блокчейн-сети). */
    val Crypto: ImageVector by lazy {
        icon("AppIconCrypto") {
            moveTo(12f, 3.3f)
            lineTo(19.5f, 7.65f)
            lineTo(19.5f, 16.35f)
            lineTo(12f, 20.7f)
            lineTo(4.5f, 16.35f)
            lineTo(4.5f, 7.65f)
            close()
            moveTo(9f, 9.5f); lineTo(15f, 14.5f)
            moveTo(15f, 9.5f); lineTo(9f, 14.5f)
        }
    }

    /** Корзина — удаление операции. */
    val Trash: ImageVector by lazy {
        icon("AppIconTrash") {
            moveTo(5f, 7f); lineTo(19f, 7f)
            moveTo(9f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(15f, 7f)
            moveTo(7f, 7f); lineTo(7.8f, 19f); lineTo(16.2f, 19f); lineTo(17f, 7f)
            moveTo(10.3f, 10.3f); lineTo(10.7f, 16f)
            moveTo(13.7f, 10.3f); lineTo(13.3f, 16f)
        }
    }
}
