package com.plurali.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* Formatos próprios de cada tema: avatares com orelhas, hexágonos, selos, folhas, rabiscos… */

private fun pathShape(build: Path.(Size, Density) -> Unit) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { build(size, density) })
}

/** Círculo com duas orelhas triangulares de gato. */
val CatEarsShape: Shape = pathShape { s, _ ->
    val w = s.width; val h = s.height
    val r = min(w, h) * 0.40f
    val cx = w / 2; val cy = h * 0.58f
    val face = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
    val ears = Path().apply {
        moveTo(cx - r * 0.95f, cy - r * 0.25f); lineTo(cx - r * 0.80f, h * 0.02f); lineTo(cx - r * 0.15f, cy - r * 0.80f); close()
        moveTo(cx + r * 0.95f, cy - r * 0.25f); lineTo(cx + r * 0.80f, h * 0.02f); lineTo(cx + r * 0.15f, cy - r * 0.80f); close()
    }
    op(face, ears, PathOperation.Union)
}

/** Círculo com duas orelhas compridas de coelho. */
val BunnyEarsShape: Shape = pathShape { s, _ ->
    val w = s.width; val h = s.height
    val r = min(w, h) * 0.34f
    val cx = w / 2; val cy = h * 0.64f
    val face = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
    val ew = r * 0.52f
    val ears = Path().apply {
        addOval(Rect(cx - r * 0.62f - ew / 2, 0f, cx - r * 0.62f + ew / 2, cy))
        addOval(Rect(cx + r * 0.62f - ew / 2, 0f, cx + r * 0.62f + ew / 2, cy))
    }
    op(face, ears, PathOperation.Union)
}

/** Polígono regular (hexágono = 6). */
fun polygonShape(sides: Int, rotationDeg: Float = 0f): Shape = pathShape { s, _ ->
    val cx = s.width / 2; val cy = s.height / 2; val r = min(s.width, s.height) / 2
    for (i in 0 until sides) {
        val a = (rotationDeg + 360f * i / sides - 90f) * PI.toFloat() / 180f
        val x = cx + r * cos(a); val y = cy + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
val HexagonShape: Shape = polygonShape(6, 30f)

/** Flor de 8 pétalas (bordas onduladas). */
val FlowerShape: Shape = pathShape { s, _ ->
    val cx = s.width / 2; val cy = s.height / 2; val r = min(s.width, s.height) / 2
    val n = 8; val steps = 96
    for (i in 0..steps) {
        val t = 2 * PI.toFloat() * i / steps
        val rr = r * (0.86f + 0.14f * cos(n * t))
        val x = cx + rr * cos(t); val y = cy + rr * sin(t)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Escudo: topo reto, ponta embaixo. */
val ShieldShape: Shape = pathShape { s, _ ->
    val w = s.width; val h = s.height
    moveTo(w * 0.06f, h * 0.08f)
    quadraticTo(w * 0.5f, -h * 0.02f, w * 0.94f, h * 0.08f)
    lineTo(w * 0.94f, h * 0.48f)
    cubicTo(w * 0.94f, h * 0.78f, w * 0.66f, h * 0.92f, w * 0.5f, h)
    cubicTo(w * 0.34f, h * 0.92f, w * 0.06f, h * 0.78f, w * 0.06f, h * 0.48f)
    close()
}

/** Selo postal: quadrado com a borda serrilhada. */
val StampShape: Shape = pathShape { s, d ->
    val w = s.width; val h = s.height
    val base = Path().apply { addRect(Rect(0f, 0f, w, h)) }
    val holeR = min(w, h) / 22f
    val holes = Path()
    val n = 7
    for (i in 0..n) {
        val x = w * i / n; val y = h * i / n
        holes.addOval(Rect(Offset(x, 0f), holeR)); holes.addOval(Rect(Offset(x, h), holeR))
        holes.addOval(Rect(Offset(0f, y), holeR)); holes.addOval(Rect(Offset(w, y), holeR))
    }
    op(base, holes, PathOperation.Difference)
}

/** Folha: dois cantos bem redondos e dois quase retos. */
val LeafShape: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 8, bottomEndPercent = 50, bottomStartPercent = 8)

/** Quadrado "desenhado à mão": cantos e lados levemente tortos. */
val SketchShape: Shape = pathShape { s, _ ->
    val w = s.width; val h = s.height; val k = min(w, h)
    moveTo(k * 0.10f, k * 0.03f)
    quadraticTo(w * 0.5f, -k * 0.015f, w - k * 0.07f, k * 0.05f)
    quadraticTo(w + k * 0.01f, k * 0.08f, w - k * 0.02f, k * 0.14f)
    quadraticTo(w + k * 0.015f, h * 0.5f, w - k * 0.04f, h - k * 0.09f)
    quadraticTo(w - k * 0.06f, h + k * 0.005f, w - k * 0.15f, h - k * 0.02f)
    quadraticTo(w * 0.5f, h + k * 0.02f, k * 0.08f, h - k * 0.04f)
    quadraticTo(-k * 0.005f, h - k * 0.08f, k * 0.03f, h - k * 0.16f)
    quadraticTo(-k * 0.02f, h * 0.5f, k * 0.02f, k * 0.12f)
    quadraticTo(k * 0.03f, k * 0.04f, k * 0.10f, k * 0.03f)
    close()
}

/** Bilhete/ingresso: cantos pouco redondos e dois recortes no meio das laterais. */
val TicketShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 6.dp.toPx() }
        val notch = with(density) { 7.dp.toPx() }
        val base = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r))) }
        val cut = Path().apply {
            addOval(Rect(Offset(0f, size.height / 2), notch))
            addOval(Rect(Offset(size.width, size.height / 2), notch))
        }
        return Outline.Generic(Path().apply { op(base, cut, PathOperation.Difference) })
    }
}

/** Cartão com duas orelhinhas de gato no canto superior direito. */
val CatCardShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val ear = with(density) { 9.dp.toPx() }
        val r = with(density) { 16.dp.toPx() }
        val body = Path().apply { addRoundRect(RoundRect(Rect(0f, ear, size.width, size.height), CornerRadius(r))) }
        val x = size.width - with(density) { 64.dp.toPx() }
        val ears = Path().apply {
            moveTo(x, ear + 2); lineTo(x + ear * 0.7f, 0f); lineTo(x + ear * 1.8f, ear + 2); close()
            moveTo(x + ear * 2.6f, ear + 2); lineTo(x + ear * 3.7f, 0f); lineTo(x + ear * 4.4f, ear + 2); close()
        }
        return Outline.Generic(Path().apply { op(body, ears, PathOperation.Union) })
    }
}

/** Cartão com a borda de baixo em "chamas" (pontas). */
val FlameCardShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tip = with(density) { 6.dp.toPx() }
        val cut = with(density) { 10.dp.toPx() }
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(cut, 0f); lineTo(w - cut, 0f); lineTo(w, cut); lineTo(w, h - tip)
            val n = (w / with(density) { 22.dp.toPx() }).toInt().coerceAtLeast(4)
            val step = w / n
            for (i in n downTo 1) {
                val x0 = step * i; val xm = x0 - step / 2
                quadraticTo(x0 - step * 0.1f, h - tip * 0.2f, xm, h)
                quadraticTo(xm - step * 0.4f, h - tip * 0.2f, x0 - step, h - tip)
            }
            lineTo(0f, cut); close()
        }
        return Outline.Generic(p)
    }
}

/** Ovo: mais estreito em cima. */
val EggShape: Shape = pathShape { s, _ ->
    val w = s.width; val h = s.height
    moveTo(w / 2, 0f)
    cubicTo(w * 0.86f, 0f, w, h * 0.5f, w, h * 0.64f)
    cubicTo(w, h * 0.88f, w * 0.78f, h, w / 2, h)
    cubicTo(w * 0.22f, h, 0f, h * 0.88f, 0f, h * 0.64f)
    cubicTo(0f, h * 0.5f, w * 0.14f, 0f, w / 2, 0f)
    close()
}

/** Formatos de avatar escolhíveis na tela de aparência (id → nome, forma). */
val AVATAR_SHAPES: List<Triple<String, String, Shape>> = listOf(
    Triple("rounded", "Arredondado", RoundedCornerShape(20)),
    Triple("circle", "Círculo", CircleShape),
    Triple("square", "Quadrado", RoundedCornerShape(4)),
    Triple("hexagon", "Hexágono", HexagonShape),
    Triple("cat", "Orelhas de gato", CatEarsShape),
    Triple("bunny", "Orelhas de coelho", BunnyEarsShape),
    Triple("flower", "Flor", FlowerShape),
    Triple("shield", "Escudo", ShieldShape),
    Triple("stamp", "Selo", StampShape),
    Triple("leaf", "Folha", LeafShape),
    Triple("sketch", "Rabisco", SketchShape),
    Triple("octagon", "Octógono", CutCornerShape(30)),
    Triple("egg", "Ovo", EggShape),
)

fun avatarShapeOf(id: String): Shape? = AVATAR_SHAPES.find { it.first == id }?.third
