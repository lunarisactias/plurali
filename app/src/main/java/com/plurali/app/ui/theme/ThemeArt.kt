package com.plurali.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Desenhos de cada tema, feitos só com formas (nada de imagens copiadas): fundo espalhado e enfeite da barra.
 * t = fase da animação (0..1); sem animação fica 0.
 */

private fun c(v: Long, a: Float) = Color(v).copy(alpha = (Color(v).alpha * a).coerceIn(0f, 1f))

/** Espalha enfeites numa grade com sorteio fixo (o desenho não muda a cada tela). */
private fun DrawScope.scatter(cellDp: Float, seed: Int, driftY: Float, block: DrawScope.(Random, Float) -> Unit) {
    val cell = cellDp * density
    val cols = (size.width / cell).toInt() + 2
    val rows = (size.height / cell).toInt() + 3
    val shift = (driftY * cell) % cell
    for (j in -1 until rows) for (i in -1 until cols) {
        val r = Random(seed * 7919 + i * 131 + j * 977)
        val x = i * cell + r.nextFloat() * cell * 0.8f + (if (j % 2 == 0) cell * 0.3f else 0f)
        val y = j * cell + r.nextFloat() * cell * 0.8f + shift
        val rot = r.nextFloat() * 360f
        translate(x, y) { rotate(rot, Offset.Zero) { block(r, rot) } }
    }
}

// ---------------- enfeites ----------------

private fun DrawScope.paw(s: Float, col: Color) {
    drawOval(col, Offset(-s * 0.28f, -s * 0.02f), Size(s * 0.56f, s * 0.46f))
    listOf(-0.33f to -0.2f, -0.12f to -0.38f, 0.12f to -0.38f, 0.33f to -0.2f).forEach { (x, y) ->
        drawCircle(col, s * 0.13f, Offset(x * s, y * s))
    }
}

private fun DrawScope.catHead(s: Float, col: Color, eye: Color) {
    val p = Path().apply {
        addOval(Rect(Offset(0f, s * 0.08f), s * 0.36f))
        moveTo(-s * 0.34f, -s * 0.05f); lineTo(-s * 0.3f, -s * 0.45f); lineTo(-s * 0.06f, -s * 0.22f); close()
        moveTo(s * 0.34f, -s * 0.05f); lineTo(s * 0.3f, -s * 0.45f); lineTo(s * 0.06f, -s * 0.22f); close()
    }
    drawPath(p, col)
    drawOval(eye, Offset(-s * 0.18f, s * 0.0f), Size(s * 0.1f, s * 0.14f))
    drawOval(eye, Offset(s * 0.08f, s * 0.0f), Size(s * 0.1f, s * 0.14f))
}

private fun DrawScope.moon(s: Float, col: Color) {
    val a = Path().apply { addOval(Rect(Offset.Zero, s * 0.4f)) }
    val b = Path().apply { addOval(Rect(Offset(s * 0.18f, -s * 0.1f), s * 0.36f)) }
    drawPath(Path().apply { op(a, b, PathOperation.Difference) }, col)
}

private fun DrawScope.sparkle(s: Float, col: Color) {
    val p = Path().apply {
        moveTo(0f, -s / 2); quadraticTo(s * 0.06f, -s * 0.06f, s / 2, 0f); quadraticTo(s * 0.06f, s * 0.06f, 0f, s / 2)
        quadraticTo(-s * 0.06f, s * 0.06f, -s / 2, 0f); quadraticTo(-s * 0.06f, -s * 0.06f, 0f, -s / 2); close()
    }
    drawPath(p, col)
}

private fun DrawScope.leaf(s: Float, col: Color) {
    val p = Path().apply { moveTo(0f, -s / 2); quadraticTo(s * 0.42f, 0f, 0f, s / 2); quadraticTo(-s * 0.42f, 0f, 0f, -s / 2); close() }
    drawPath(p, col)
    drawLine(col.copy(alpha = col.alpha * 0.5f), Offset(0f, -s * 0.4f), Offset(0f, s * 0.48f), strokeWidth = s * 0.04f)
}

private fun DrawScope.flower(s: Float, petal: Color, center: Color) {
    for (k in 0 until 5) {
        val a = 2 * PI.toFloat() * k / 5
        drawCircle(petal, s * 0.2f, Offset(cos(a) * s * 0.22f, sin(a) * s * 0.22f))
    }
    drawCircle(center, s * 0.13f)
}

private fun DrawScope.heart(s: Float, col: Color) {
    val p = Path().apply {
        moveTo(0f, s * 0.35f)
        cubicTo(-s * 0.55f, -s * 0.05f, -s * 0.3f, -s * 0.5f, 0f, -s * 0.18f)
        cubicTo(s * 0.3f, -s * 0.5f, s * 0.55f, -s * 0.05f, 0f, s * 0.35f); close()
    }
    drawPath(p, col)
}

private fun DrawScope.bunny(s: Float, col: Color, inner: Color) {
    drawCircle(col, s * 0.28f, Offset(0f, s * 0.12f))
    rotate(-12f, Offset(-s * 0.12f, -s * 0.2f)) {
        drawOval(col, Offset(-s * 0.2f, -s * 0.55f), Size(s * 0.16f, s * 0.48f))
        drawOval(inner, Offset(-s * 0.17f, -s * 0.48f), Size(s * 0.08f, s * 0.34f))
    }
    rotate(12f, Offset(s * 0.12f, -s * 0.2f)) {
        drawOval(col, Offset(s * 0.04f, -s * 0.55f), Size(s * 0.16f, s * 0.48f))
        drawOval(inner, Offset(s * 0.08f, -s * 0.48f), Size(s * 0.08f, s * 0.34f))
    }
}

private fun DrawScope.carrot(s: Float, body: Color, leaves: Color) {
    drawPath(Path().apply { moveTo(-s * 0.12f, -s * 0.22f); lineTo(s * 0.12f, -s * 0.22f); lineTo(0f, s * 0.45f); close() }, body)
    drawOval(leaves, Offset(-s * 0.16f, -s * 0.48f), Size(s * 0.1f, s * 0.28f))
    drawOval(leaves, Offset(-s * 0.05f, -s * 0.52f), Size(s * 0.1f, s * 0.3f))
    drawOval(leaves, Offset(s * 0.06f, -s * 0.48f), Size(s * 0.1f, s * 0.28f))
}

private fun DrawScope.mushroom(s: Float, cap: Color, dot: Color) {
    drawRoundRect(dot, Offset(-s * 0.09f, -s * 0.02f), Size(s * 0.18f, s * 0.36f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f))
    drawArc(cap, 180f, 180f, true, Offset(-s * 0.36f, -s * 0.32f), Size(s * 0.72f, s * 0.6f))
    drawCircle(dot, s * 0.06f, Offset(-s * 0.15f, -s * 0.14f)); drawCircle(dot, s * 0.05f, Offset(s * 0.12f, -s * 0.2f))
}

private fun DrawScope.wings(s: Float, col: Color) {
    val st = Stroke(s * 0.04f)
    drawOval(col, Offset(-s * 0.45f, -s * 0.35f), Size(s * 0.42f, s * 0.36f), style = st)
    drawOval(col, Offset(s * 0.03f, -s * 0.35f), Size(s * 0.42f, s * 0.36f), style = st)
    drawOval(col, Offset(-s * 0.32f, s * 0.0f), Size(s * 0.28f, s * 0.26f), style = st)
    drawOval(col, Offset(s * 0.04f, s * 0.0f), Size(s * 0.28f, s * 0.26f), style = st)
}

private fun DrawScope.ladybug(s: Float, red: Color, black: Color) {
    drawCircle(black, s * 0.14f, Offset(0f, -s * 0.28f))
    drawCircle(red, s * 0.3f)
    drawLine(black, Offset(0f, -s * 0.3f), Offset(0f, s * 0.3f), strokeWidth = s * 0.04f)
    listOf(-0.14f to -0.1f, 0.14f to -0.1f, -0.16f to 0.12f, 0.16f to 0.12f, -0.07f to 0.22f, 0.07f to 0.22f).forEach { (x, y) ->
        drawCircle(black, s * 0.05f, Offset(x * s, y * s))
    }
}

private fun DrawScope.bee(s: Float, yellow: Color, black: Color, wing: Color) {
    drawOval(wing, Offset(-s * 0.22f, -s * 0.38f), Size(s * 0.2f, s * 0.26f))
    drawOval(wing, Offset(s * 0.02f, -s * 0.38f), Size(s * 0.2f, s * 0.26f))
    drawOval(yellow, Offset(-s * 0.3f, -s * 0.18f), Size(s * 0.6f, s * 0.38f))
    drawRect(black, Offset(-s * 0.08f, -s * 0.17f), Size(s * 0.07f, s * 0.36f))
    drawRect(black, Offset(s * 0.08f, -s * 0.15f), Size(s * 0.07f, s * 0.32f))
}

private fun DrawScope.butterfly(s: Float, wing: Color, body: Color) {
    drawOval(wing, Offset(-s * 0.42f, -s * 0.36f), Size(s * 0.4f, s * 0.38f))
    drawOval(wing, Offset(s * 0.02f, -s * 0.36f), Size(s * 0.4f, s * 0.38f))
    drawOval(wing, Offset(-s * 0.3f, s * 0.0f), Size(s * 0.28f, s * 0.28f))
    drawOval(wing, Offset(s * 0.02f, s * 0.0f), Size(s * 0.28f, s * 0.28f))
    drawLine(body, Offset(0f, -s * 0.32f), Offset(0f, s * 0.3f), strokeWidth = s * 0.06f, cap = StrokeCap.Round)
}

private fun DrawScope.compass(s: Float, col: Color, north: Color) {
    drawCircle(col, s * 0.48f, style = Stroke(s * 0.03f))
    drawCircle(col, s * 0.42f, style = Stroke(s * 0.015f))
    for (k in 0 until 8) {
        val a = PI.toFloat() / 4 * k
        val len = if (k % 2 == 0) 0.46f else 0.28f
        val tip = Offset(sin(a) * s * len, -cos(a) * s * len)
        val l = Offset(sin(a + 1.4f) * s * 0.07f, -cos(a + 1.4f) * s * 0.07f)
        val r = Offset(sin(a - 1.4f) * s * 0.07f, -cos(a - 1.4f) * s * 0.07f)
        drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(r.x, r.y); close() }, if (k == 0) north else col)
    }
}

private fun DrawScope.mountain(s: Float, col: Color, snow: Color) {
    drawPath(Path().apply { moveTo(-s * 0.5f, s * 0.3f); lineTo(-s * 0.1f, -s * 0.35f); lineTo(s * 0.3f, s * 0.3f); close() }, col)
    drawPath(Path().apply { moveTo(-s * 0.1f, s * 0.3f); lineTo(s * 0.2f, -s * 0.12f); lineTo(s * 0.5f, s * 0.3f); close() }, col.copy(alpha = col.alpha * 0.7f))
    drawPath(Path().apply { moveTo(-s * 0.22f, -s * 0.15f); lineTo(-s * 0.1f, -s * 0.35f); lineTo(s * 0.02f, -s * 0.15f); close() }, snow)
}

private fun DrawScope.pencil(s: Float, body: Color, tip: Color, lead: Color, eraser: Color) {
    drawRect(eraser, Offset(-s * 0.5f, -s * 0.07f), Size(s * 0.12f, s * 0.14f))
    drawRect(body, Offset(-s * 0.38f, -s * 0.07f), Size(s * 0.6f, s * 0.14f))
    drawPath(Path().apply { moveTo(s * 0.22f, -s * 0.07f); lineTo(s * 0.42f, 0f); lineTo(s * 0.22f, s * 0.07f); close() }, tip)
    drawPath(Path().apply { moveTo(s * 0.35f, -s * 0.025f); lineTo(s * 0.42f, 0f); lineTo(s * 0.35f, s * 0.025f); close() }, lead)
}

private fun DrawScope.splat(s: Float, col: Color, r: Random) {
    drawCircle(col, s * 0.25f)
    repeat(6) {
        val a = r.nextFloat() * 2 * PI.toFloat(); val d = s * (0.25f + r.nextFloat() * 0.25f)
        drawCircle(col, s * (0.04f + r.nextFloat() * 0.07f), Offset(cos(a) * d, sin(a) * d))
    }
}

private fun DrawScope.scribble(s: Float, col: Color) {
    val p = Path().apply {
        moveTo(-s / 2, 0f)
        for (k in 0 until 5) {
            val x0 = -s / 2 + s * k / 5
            quadraticTo(x0 + s * 0.1f, if (k % 2 == 0) -s * 0.2f else s * 0.2f, x0 + s / 5, 0f)
        }
    }
    drawPath(p, col, style = Stroke(s * 0.035f, cap = StrokeCap.Round))
}

private fun DrawScope.spider(s: Float, col: Color) {
    for (k in 0 until 4) {
        val y = -s * 0.12f + s * 0.08f * k
        drawLine(col, Offset(-s * 0.1f, y), Offset(-s * 0.38f, y - s * 0.12f + s * 0.06f * k), strokeWidth = s * 0.035f)
        drawLine(col, Offset(s * 0.1f, y), Offset(s * 0.38f, y - s * 0.12f + s * 0.06f * k), strokeWidth = s * 0.035f)
    }
    drawCircle(col, s * 0.15f, Offset(0f, s * 0.05f)); drawCircle(col, s * 0.09f, Offset(0f, -s * 0.13f))
}

/** Teia saindo de um canto (cx, cy), abrindo de a0 até a1 graus. */
private fun DrawScope.web(cx: Float, cy: Float, radius: Float, a0: Float, a1: Float, col: Color) {
    val n = 9; val rings = 7
    val st = Stroke(1.1f * density)
    val angles = (0..n).map { (a0 + (a1 - a0) * it / n) * PI.toFloat() / 180f }
    angles.forEach { a -> drawLine(col, Offset(cx, cy), Offset(cx + cos(a) * radius, cy + sin(a) * radius), strokeWidth = st.width) }
    for (k in 1..rings) {
        val rr = radius * k / rings
        val p = Path()
        angles.forEachIndexed { i, a ->
            val pt = Offset(cx + cos(a) * rr, cy + sin(a) * rr)
            if (i == 0) p.moveTo(pt.x, pt.y) else {
                val mid = (angles[i - 1] + a) / 2
                val sag = rr * 0.86f
                p.quadraticTo(cx + cos(mid) * sag, cy + sin(mid) * sag, pt.x, pt.y)
            }
        }
        drawPath(p, col, style = st)
    }
}

private fun DrawScope.flyingBird(s: Float, col: Color) {
    val p = Path().apply {
        moveTo(-s / 2, 0f); quadraticTo(-s / 4, -s * 0.32f, 0f, 0f); quadraticTo(s / 4, -s * 0.32f, s / 2, 0f)
    }
    drawPath(p, col, style = Stroke(s * 0.08f, cap = StrokeCap.Round))
}

/** Passarinho redondo pousado (de lado). */
internal fun DrawScope.perchedBird(s: Float, body: Color, beak: Color, faceRight: Boolean) {
    val d = if (faceRight) 1f else -1f
    drawPath(Path().apply { moveTo(-d * s * 0.25f, s * 0.05f); lineTo(-d * s * 0.55f, -s * 0.05f); lineTo(-d * s * 0.5f, s * 0.15f); close() }, body)
    drawCircle(body, s * 0.3f, Offset(0f, s * 0.08f))
    drawCircle(body, s * 0.19f, Offset(d * s * 0.2f, -s * 0.2f))
    drawPath(Path().apply { moveTo(d * s * 0.36f, -s * 0.24f); lineTo(d * s * 0.52f, -s * 0.19f); lineTo(d * s * 0.36f, -s * 0.13f); close() }, beak)
    drawCircle(Color.Black.copy(alpha = body.alpha), s * 0.035f, Offset(d * s * 0.25f, -s * 0.24f))
    drawOval(Color.Black.copy(alpha = body.alpha * 0.18f), Offset(-s * 0.2f, s * 0.0f), Size(s * 0.32f, s * 0.2f))
    drawLine(beak, Offset(-s * 0.05f, s * 0.36f), Offset(-s * 0.05f, s * 0.44f), strokeWidth = s * 0.04f)
    drawLine(beak, Offset(s * 0.07f, s * 0.36f), Offset(s * 0.07f, s * 0.44f), strokeWidth = s * 0.04f)
}

private fun DrawScope.feather(s: Float, col: Color) {
    drawPath(Path().apply { moveTo(0f, -s / 2); quadraticTo(s * 0.22f, 0f, 0f, s * 0.42f); quadraticTo(-s * 0.22f, 0f, 0f, -s / 2); close() }, col)
    drawLine(col.copy(alpha = col.alpha * 0.6f), Offset(0f, -s * 0.45f), Offset(0f, s / 2), strokeWidth = s * 0.03f)
}

private fun DrawScope.cloud(s: Float, col: Color) {
    drawCircle(col, s * 0.22f, Offset(-s * 0.22f, s * 0.05f))
    drawCircle(col, s * 0.3f, Offset(0f, -s * 0.06f))
    drawCircle(col, s * 0.2f, Offset(s * 0.25f, s * 0.07f))
    drawRect(col, Offset(-s * 0.22f, s * 0.05f), Size(s * 0.47f, s * 0.22f))
}

/** Rajada de vento: linha que termina num caracol. */
private fun DrawScope.gust(s: Float, col: Color) {
    val st = Stroke(s * 0.04f, cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(-s / 2, 0f); cubicTo(-s * 0.2f, -s * 0.08f, s * 0.05f, s * 0.08f, s * 0.3f, 0f) }, col, style = st)
    drawArc(col, 90f, 270f, false, Offset(s * 0.18f, -s * 0.24f), Size(s * 0.24f, s * 0.24f), style = st)
    drawLine(col, Offset(-s * 0.35f, s * 0.14f), Offset(s * 0.12f, s * 0.14f), strokeWidth = st.width, cap = StrokeCap.Round)
}

private fun DrawScope.yoyo(s: Float, red: Color, black: Color, string: Color) {
    drawLine(string, Offset(0f, -s * 1.6f), Offset(0f, 0f), strokeWidth = s * 0.03f)
    drawCircle(red, s * 0.32f)
    drawLine(black, Offset(-s * 0.32f, 0f), Offset(s * 0.32f, 0f), strokeWidth = s * 0.03f)
    listOf(-0.14f to -0.14f, 0.15f to -0.12f, -0.12f to 0.15f, 0.13f to 0.14f).forEach { (x, y) -> drawCircle(black, s * 0.055f, Offset(x * s, y * s)) }
}

private fun DrawScope.eiffel(w: Float, hh: Float, col: Color) {
    val p = Path().apply {
        moveTo(-w / 2, 0f)
        quadraticTo(-w * 0.25f, -hh * 0.35f, -w * 0.12f, -hh * 0.6f)
        lineTo(-w * 0.03f, -hh); lineTo(w * 0.03f, -hh)
        lineTo(w * 0.12f, -hh * 0.6f)
        quadraticTo(w * 0.25f, -hh * 0.35f, w / 2, 0f)
        lineTo(w * 0.22f, 0f); quadraticTo(0f, -hh * 0.3f, -w * 0.22f, 0f); close()
    }
    drawPath(p, col)
    drawRect(col, Offset(-w * 0.3f, -hh * 0.28f), Size(w * 0.6f, hh * 0.03f))
    drawRect(col, Offset(-w * 0.17f, -hh * 0.6f), Size(w * 0.34f, hh * 0.025f))
    drawLine(col, Offset(0f, -hh), Offset(0f, -hh * 1.08f), strokeWidth = w * 0.02f)
}

// ---------------- fundo ----------------

/** Desenha o fundo do tema. alpha = intensidade escolhida (0 = nada). */
fun DrawScope.drawThemePattern(th: ThemeDef, intensity: Float, t: Float) {
    if (intensity <= 0f || th.pattern == "none") return
    val p = th.palette.pattern
    val a = th.palette.patternAlpha * intensity
    fun col(i: Int, mult: Float = 1f) = c(p[i % p.size], a * mult)
    val tw = { r: Random -> 0.55f + 0.45f * sin(2 * PI.toFloat() * (t * 2 + r.nextFloat())) } // piscar
    val dp = density
    when (th.pattern) {
        "cat" -> {
            scatter(120f, 1, t) { r, _ ->
                when (r.nextInt(5)) {
                    0, 1 -> paw(26 * dp, col(0))
                    2 -> catHead(34 * dp, col(0), col(1, 1.6f))
                    3 -> moon(30 * dp, col(2, 0.8f))
                    else -> sparkle(10 * dp, col(2, tw(r)))
                }
            }
        }
        "garden" -> {
            scatter(96f, 2, t * 0.5f) { r, _ ->
                when (r.nextInt(6)) {
                    0, 1, 2 -> leaf(26 * dp, col(0))
                    3 -> flower(30 * dp, col(1), col(2))
                    4 -> flower(24 * dp, col(3), col(2))
                    else -> drawCircle(col(2), 3 * dp)
                }
            }
            // grama no rodapé
            val g = col(0, 1.4f)
            var x = 0f; val rr = Random(5)
            while (x < size.width) {
                val h = (18 + rr.nextFloat() * 26) * dp
                drawPath(Path().apply {
                    moveTo(x, size.height); quadraticTo(x + 4 * dp, size.height - h * 0.6f, x + rr.nextFloat() * 10 * dp - 2 * dp, size.height - h)
                    quadraticTo(x + 6 * dp, size.height - h * 0.5f, x + 8 * dp, size.height); close()
                }, g)
                x += 7 * dp
            }
        }
        "spider" -> {
            web(size.width, 0f, size.width * 0.75f, 90f, 180f, col(0))
            web(0f, size.height, size.width * 0.55f, 270f, 360f, col(0, 0.8f))
            // aranhas penduradas
            listOf(0.22f to 0.18f, 0.7f to 0.42f, 0.4f to 0.7f).forEachIndexed { i, (fx, fy) ->
                val x = size.width * fx
                val y = size.height * fy + sin(2 * PI.toFloat() * (t + i * 0.3f)) * 14 * dp
                drawLine(col(0), Offset(x, 0f), Offset(x, y - 8 * dp), strokeWidth = 1f * dp)
                translate(x, y) { spider(30 * dp, col(1, 1.6f)) }
            }
        }
        "bunny" -> {
            scatter(110f, 3, t * 0.5f) { r, _ ->
                when (r.nextInt(5)) {
                    0, 1 -> bunny(40 * dp, col(0), col(3, 0.8f))
                    2 -> carrot(30 * dp, col(1), col(2))
                    else -> heart(18 * dp, col(3, tw(r)))
                }
            }
        }
        "inferno" -> {
            // brasas subindo (núcleo forte + halo)
            scatter(70f, 4, -t * 3f) { r, _ ->
                val rad = (1.4f + r.nextFloat() * 2.6f) * dp
                val k = tw(r) * intensity.coerceAtMost(1.5f)
                drawCircle(Color(p[0]).copy(alpha = 0.12f * k), rad * 4.5f)
                drawCircle(Color(p[1]).copy(alpha = 0.35f * k), rad * 2.2f)
                drawCircle(Color(p[2]).copy(alpha = 0.95f * k), rad)
            }
            // chamas no rodapé: três camadas (vermelho, laranja, amarelo), línguas largas que se sobrepõem
            val base = size.height
            listOf(Triple(3, 230f, 0.9f), Triple(0, 165f, 1.1f), Triple(2, 95f, 1.3f)).forEachIndexed { li, (ci, hDp, m) ->
                val w = (78 - li * 10) * dp
                val path = Path()
                var x = -w * (0.3f + li * 0.2f); var k = 0
                while (x < size.width + w) {
                    val flick = 0.78f + 0.22f * sin(2 * PI.toFloat() * (t * 3 + k * 0.37f + li))
                    val h = hDp * dp * (0.55f + 0.45f * abs(sin(k * 1.7f + li * 0.9f))) * flick
                    val lean = (sin(k * 2.3f + li) * 0.18f) * w
                    path.moveTo(x, base)
                    path.quadraticTo(x + w * 0.02f, base - h * 0.55f, x + w * 0.5f + lean, base - h)
                    path.quadraticTo(x + w * 0.98f, base - h * 0.55f, x + w, base)
                    path.close()
                    x += w * 0.55f; k++
                }
                val c0 = Color(p[ci])
                drawPath(path, Brush.verticalGradient(
                    listOf(c0.copy(alpha = 0f), c0.copy(alpha = (a * m * 1.6f).coerceAtMost(0.9f))),
                    startY = base - hDp * dp, endY = base,
                ))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col(0, 1.2f)), startY = size.height * 0.5f, endY = size.height))
        }
        "fairy" -> {
            scatter(88f, 6, t * 0.6f) { r, _ ->
                when (r.nextInt(7)) {
                    0, 1, 2 -> sparkle((10 + r.nextFloat() * 12) * dp, col(3, 1.4f * tw(r)))
                    3 -> wings(36 * dp, col(0, 1.3f))
                    4 -> mushroom(30 * dp, col(1), col(2, 0.9f))
                    else -> drawCircle(col(r.nextInt(4), tw(r)), (2 + r.nextFloat() * 2) * dp)
                }
            }
        }
        "geo" -> {
            // grade de latitude e longitude
            val lines = col(0, 0.7f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6 * dp, 6 * dp))
            var y = 60 * dp
            while (y < size.height) { drawLine(lines, Offset(0f, y), Offset(size.width, y), strokeWidth = dp, pathEffect = dash); y += 90 * dp }
            var x = 40 * dp
            while (x < size.width + 200 * dp) {
                val path = Path().apply { moveTo(x, 0f); quadraticTo(x - 60 * dp, size.height / 2, x, size.height) }
                drawPath(path, lines, style = Stroke(dp, pathEffect = dash)); x += 110 * dp
            }
            translate(size.width - 70 * dp, 110 * dp) { rotate(t * 360f * 0.05f, Offset.Zero) { compass(96 * dp, col(0, 1.3f), col(2, 1.6f)) } }
            // rota pontilhada com um X
            val route = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.35f)
                cubicTo(size.width * 0.5f, size.height * 0.25f, size.width * 0.2f, size.height * 0.65f, size.width * 0.7f, size.height * 0.7f)
            }
            drawPath(route, col(2, 1.3f), style = Stroke(2.2f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * dp, 7 * dp)), cap = StrokeCap.Round))
            translate(size.width * 0.7f, size.height * 0.7f) {
                drawLine(col(2, 1.6f), Offset(-8 * dp, -8 * dp), Offset(8 * dp, 8 * dp), strokeWidth = 3 * dp, cap = StrokeCap.Round)
                drawLine(col(2, 1.6f), Offset(8 * dp, -8 * dp), Offset(-8 * dp, 8 * dp), strokeWidth = 3 * dp, cap = StrokeCap.Round)
            }
            translate(size.width * 0.18f, size.height * 0.85f) { mountain(70 * dp, col(0, 1.1f), col(1, 0.6f)) }
            translate(size.width * 0.8f, size.height * 0.45f) { mountain(50 * dp, col(0, 0.9f), col(1, 0.5f)) }
        }
        "insects" -> {
            scatter(104f, 8, t * 0.4f) { r, _ ->
                when (r.nextInt(5)) {
                    0, 1 -> ladybug(28 * dp, col(0, 1.2f), col(1))
                    2 -> bee(32 * dp, col(2, 1.3f), col(1), col(4, 0.8f))
                    3 -> butterfly(34 * dp, col(4 - r.nextInt(2) * 2, 1.1f), col(1))
                    else -> leaf(24 * dp, col(3))
                }
            }
            // trilha de formigas
            val trail = Path().apply {
                moveTo(0f, size.height * 0.62f)
                cubicTo(size.width * 0.3f, size.height * 0.5f, size.width * 0.6f, size.height * 0.8f, size.width, size.height * 0.66f)
            }
            drawPath(trail, col(1, 0.9f), style = Stroke(3.2f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * dp, 4 * dp, 3 * dp, 9 * dp), t * 60 * dp), cap = StrokeCap.Round))
        }
        "birds" -> {
            scatter(112f, 11, t * 0.7f) { r, rot ->
                when (r.nextInt(6)) {
                    0, 1 -> rotate(-rot, Offset.Zero) { flyingBird((18 + r.nextFloat() * 14) * dp, col(0, 1.2f)) }
                    2 -> rotate(-rot, Offset.Zero) { cloud(56 * dp, Color.White.copy(alpha = (a * 2.4f).coerceAtMost(0.7f))) }
                    3 -> feather(28 * dp, col(1 + r.nextInt(2)))
                    4 -> rotate(-rot, Offset.Zero) { perchedBird(26 * dp, col(1 + r.nextInt(3), 1.1f), col(4, 1.2f), r.nextBoolean()) }
                    else -> rotate(-rot, Offset.Zero) { flyingBird(12 * dp, col(0)) }
                }
            }
        }
        "ladybug" -> {
            // bolinhas grandes (vermelhas e pretas) num céu de Paris à noite
            scatter(96f, 12, t * 0.4f) { r, rot ->
                when (r.nextInt(6)) {
                    0, 1 -> drawCircle(col(0, 1.1f), (5 + r.nextFloat() * 9) * dp)
                    2 -> drawCircle(col(1, 1.5f), (4 + r.nextFloat() * 6) * dp)
                    3 -> rotate(-rot, Offset.Zero) { yoyo(26 * dp, col(0, 1.6f), col(1, 2f), col(2, 1.2f)) }
                    4 -> sparkle(10 * dp, col(3, 1.3f * tw(r)))
                    else -> ladybug(24 * dp, col(0, 1.5f), col(1, 2f))
                }
            }
            translate(size.width * 0.8f, size.height) { eiffel(110 * dp, 260 * dp, col(2, 1.1f)) }
            translate(size.width * 0.18f, size.height) { eiffel(46 * dp, 110 * dp, col(2, 0.7f)) }
        }
        "wind" -> {
            scatter(118f, 13, -t * 1.5f) { r, rot ->
                rotate(-rot + (r.nextFloat() - 0.5f) * 30f, Offset.Zero) {
                    when (r.nextInt(5)) {
                        0, 1, 2 -> gust((60 + r.nextFloat() * 50) * dp, col(0, 1.2f))
                        3 -> rotate(rot, Offset.Zero) { leaf(20 * dp, col(1, 1.2f)) }
                        else -> rotate(rot, Offset.Zero) { leaf(14 * dp, col(2, 1.2f)) }
                    }
                }
            }
        }
        "artist" -> {
            // folha de caderno: linhas azuis e margem vermelha
            var y = 32 * dp
            while (y < size.height) { drawLine(col(0, 0.9f), Offset(0f, y), Offset(size.width, y), strokeWidth = dp); y += 30 * dp }
            drawLine(col(1, 1.1f), Offset(46 * dp, 0f), Offset(46 * dp, size.height), strokeWidth = 1.4f * dp)
            scatter(150f, 9, t * 0.2f) { r, _ ->
                when (r.nextInt(5)) {
                    0 -> pencil(70 * dp, col(3, 1.4f), c(0xFFF3D9B1, a * 1.6f), col(2, 1.6f), col(1, 1.3f))
                    1, 2 -> splat(34 * dp, col(4 + r.nextInt(3), 1.1f), r)
                    3 -> scribble(54 * dp, col(2, 1.1f))
                    else -> sparkle(20 * dp, col(4 + r.nextInt(3), 1.2f))
                }
            }
        }
    }
}

// ---------------- enfeite da barra de título ----------------

/** Faixa fina embaixo da barra de título (≈ 12dp de altura). */
fun DrawScope.drawThemeDecor(th: ThemeDef, t: Float) {
    if (th.decor == "none") return
    val dp = density
    val acc = Color(th.palette.accent)
    val p = th.palette.pattern
    fun pc(i: Int, a: Float = 0.85f) = c(p[i % p.size], a)
    val h = size.height; val mid = h / 2
    when (th.decor) {
        "paws" -> {
            var x = 14 * dp; var k = 0
            while (x < size.width) {
                translate(x + t * 30 * dp % (34 * dp), mid + if (k % 2 == 0) -2 * dp else 2 * dp) { rotate(90f, Offset.Zero) { paw(9 * dp, pc(0, 0.7f)) } }
                x += 34 * dp; k++
            }
        }
        "vine" -> {
            val path = Path().apply {
                moveTo(0f, mid); var x = 0f; var up = true
                while (x < size.width) { quadraticTo(x + 18 * dp, if (up) 0f else h, x + 36 * dp, mid); x += 36 * dp; up = !up }
            }
            drawPath(path, pc(0, 0.9f), style = Stroke(1.6f * dp))
            var x = 18 * dp; var k = 0
            while (x < size.width) {
                translate(x, if (k % 2 == 0) 3 * dp else h - 3 * dp) {
                    if (k % 3 == 2) flower(10 * dp, pc(1), pc(2)) else rotate(if (k % 2 == 0) -50f else 50f, Offset.Zero) { leaf(9 * dp, pc(0)) }
                }
                x += 36 * dp; k++
            }
        }
        "web" -> {
            val col = pc(0, 0.55f)
            drawLine(col, Offset(0f, 2 * dp), Offset(size.width, 2 * dp), strokeWidth = dp)
            var x = 0f
            while (x < size.width) {
                drawPath(Path().apply { moveTo(x, 2 * dp); quadraticTo(x + 12 * dp, h * 1.1f, x + 24 * dp, 2 * dp) }, col, style = Stroke(dp))
                drawLine(col, Offset(x + 12 * dp, 2 * dp), Offset(x + 12 * dp, h * 0.6f), strokeWidth = 0.8f * dp)
                x += 24 * dp
            }
            translate(size.width * 0.78f, h * 0.7f + sin(t * 2 * PI.toFloat()) * 2 * dp) { spider(11 * dp, acc) }
        }
        "hearts" -> {
            var x = 10 * dp; var k = 0
            while (x < size.width) {
                translate(x, mid) { if (k % 4 == 3) carrot(12 * dp, pc(1), pc(2)) else heart(8 * dp, pc(3, 0.8f)) }
                x += 22 * dp; k++
            }
        }
        "flames" -> {
            val path = Path(); var x = 0f; var k = 0
            path.moveTo(0f, 0f)
            while (x < size.width) {
                val hh = h * (0.55f + 0.45f * abs(sin(k * 1.3f + t * 6f)))
                path.quadraticTo(x + 5 * dp, hh * 0.4f, x + 8 * dp, hh); path.quadraticTo(x + 11 * dp, hh * 0.4f, x + 16 * dp, 0f)
                x += 16 * dp; k++
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(pc(0, 0.95f), pc(2, 0.8f)), 0f, h))
        }
        "sparkles" -> {
            drawLine(Brush.horizontalGradient(listOf(pc(0, 0.2f), pc(1, 0.8f), pc(2, 0.2f))), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.2f * dp)
            var x = 12 * dp; var k = 0
            while (x < size.width) {
                val s = (if (k % 3 == 0) 11f else 6f) * dp * (0.7f + 0.3f * sin(2 * PI.toFloat() * (t * 2 + k * 0.21f)))
                translate(x, mid) { sparkle(s, pc(3 - k % 2, 0.9f)) }
                x += 26 * dp; k++
            }
        }
        "scale" -> {
            // escala de mapa: segmentos alternados preto/branco + marcas
            val seg = 26 * dp; val y = mid - 2 * dp
            var x = 10 * dp; var k = 0
            while (x + seg < size.width - 10 * dp) {
                drawRect(if (k % 2 == 0) pc(0, 0.85f) else Color(th.palette.card), Offset(x, y), Size(seg, 4 * dp))
                drawLine(pc(0, 0.85f), Offset(x, y - 3 * dp), Offset(x, y + 7 * dp), strokeWidth = dp)
                x += seg; k++
            }
            drawRect(pc(0, 0.85f), Offset(10 * dp, y), Size(x - 10 * dp, 4 * dp), style = Stroke(0.8f * dp))
        }
        "ants" -> {
            var x = (t * 80 * dp) % (20 * dp) - 20 * dp; var k = 0
            while (x < size.width) {
                val y = mid + sin(k * 0.8f) * 2 * dp
                val col = pc(1, 0.8f)
                drawCircle(col, 1.6f * dp, Offset(x, y)); drawCircle(col, 2.2f * dp, Offset(x + 3.5f * dp, y)); drawCircle(col, 1.4f * dp, Offset(x + 6.8f * dp, y))
                x += 20 * dp; k++
            }
        }
        "pencil" -> {
            val path = Path().apply {
                moveTo(0f, mid); var x = 0f; val r = Random(3)
                while (x < size.width) { val nx = x + 14 * dp; quadraticTo(x + 7 * dp, mid + (r.nextFloat() - 0.5f) * 8 * dp, nx, mid + (r.nextFloat() - 0.5f) * 3 * dp); x = nx }
            }
            drawPath(path, Color(th.palette.text).copy(alpha = 0.55f), style = Stroke(1.4f * dp, cap = StrokeCap.Round))
            translate(size.width - 40 * dp - t * 10 * dp, mid) { rotate(-8f, Offset.Zero) { pencil(48 * dp, pc(3, 1f), c(0xFFF3D9B1, 1f), pc(2, 1f), pc(1, 1f)) } }
        }
        "wire" -> {
            // fio reto com passarinhos pousados
            val col = pc(0, 0.7f)
            drawLine(col, Offset(0f, h * 0.78f), Offset(size.width, h * 0.78f), strokeWidth = 1.2f * dp)
            var x = 28 * dp; var k = 0
            while (x < size.width) {
                if (k % 3 != 2) translate(x, h * 0.78f - 6 * dp) { perchedBird(15 * dp, pc(1 + k % 3, 0.95f), pc(4, 1f), k % 2 == 0) }
                x += 52 * dp + (k % 2) * 18 * dp; k++
            }
        }
        "dots" -> {
            var x = 10 * dp; var k = 0
            while (x < size.width) {
                drawCircle(pc(0, 0.95f), 5.5f * dp, Offset(x, mid))
                drawCircle(pc(1, 1f), 1.6f * dp, Offset(x - 2 * dp, mid - 1.5f * dp))
                drawCircle(pc(1, 1f), 1.4f * dp, Offset(x + 2.2f * dp, mid + 1.6f * dp))
                x += 22 * dp; k++
            }
        }
        "breeze" -> {
            for (j in 0 until 2) {
                val path = Path(); var x = -40 * dp + ((t * 120 * dp + j * 30 * dp) % (40 * dp))
                path.moveTo(x, mid + (j * 2 - 1) * 2.5f * dp)
                while (x < size.width + 40 * dp) {
                    path.relativeQuadraticTo(10 * dp, (if (j == 0) -4f else 4f) * dp, 20 * dp, 0f)
                    path.relativeQuadraticTo(10 * dp, (if (j == 0) 4f else -4f) * dp, 20 * dp, 0f)
                    x += 40 * dp
                }
                drawPath(path, pc(j, 0.55f), style = Stroke(1.4f * dp, cap = StrokeCap.Round))
            }
        }
    }
}

/** Escala usada pelas miniaturas de tema na tela de aparência. */
fun DrawScope.drawThemePreviewArt(th: ThemeDef) = scale(0.5f, Offset.Zero) {
    // dobra a área (inset negativo) e desenha pela metade: cabem mais enfeites na miniatura
    inset(0f, 0f, -size.width, -size.height) { drawThemePattern(th, 1.5f, 0f) }
}
