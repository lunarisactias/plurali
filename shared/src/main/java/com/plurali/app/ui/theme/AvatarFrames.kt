package com.plurali.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Molduras do perfil (como as decorações de avatar do Discord): desenhadas pelo app, em volta do contorno
 * de verdade da foto (que muda conforme o tema: círculo, quadrado arredondado, orelhas…). Algumas são animadas.
 * A moldura pode passar um pouco para fora da foto, sem mudar o tamanho que a foto ocupa na tela.
 */

data class FrameDef(val id: String, val name: String)

val AVATAR_FRAMES = listOf(
    FrameDef("neon", "Neon"),
    FrameDef("vines", "Vinhas"),
    FrameDef("circuit", "Eletrônica"),
    FrameDef("rainbow", "Arco-íris"),
    FrameDef("stars", "Estrelas"),
    FrameDef("fire", "Fogo"),
    FrameDef("ice", "Gelo"),
    FrameDef("flowers", "Flores"),
    FrameDef("hearts", "Corações"),
    FrameDef("crown", "Coroa"),
    FrameDef("catears", "Gatinho"),
    FrameDef("pixel", "Pixel"),
)

fun frameName(id: String?) = AVATAR_FRAMES.find { it.id == id }?.name ?: "Nenhuma"

/** Foto com moldura. Sem moldura (ou em tamanhos muito pequenos), é só a foto. */
@Composable
fun FramedAvatar(frame: String?, size: Dp, color: Long, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val def = AVATAR_FRAMES.find { it.id == frame }
    if (def == null || size.value < 28f) {
        Box(modifier.size(size)) { content() }
        return
    }
    val shape = LocalUi.current.avatarShape
    val tr = rememberInfiniteTransition(label = "moldura")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart), label = "fase")
    Box(modifier.size(size)) {
        content()
        Canvas(Modifier.matchParentSize()) {
            val path = outlinePath(shape, this)
            drawFrame(def.id, path, Color(color), t)
        }
    }
}

// ---------------- contorno ----------------

private fun outlinePath(shape: Shape, d: DrawScope): Path = when (val o = shape.createOutline(d.size, d.layoutDirection, d)) {
    is Outline.Generic -> o.path
    is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(o.rect) }
}

/** Pontos ao longo do contorno, já um pouco para fora ([out] px), com a direção para fora. */
private class Along(path: Path, val center: Offset, val out: Float) {
    private val pm = PathMeasure().apply { setPath(path, true) }
    val length = pm.length

    /** Posição a uma fração [f] (0..1) do contorno, empurrada [extra] px para fora. */
    fun at(f: Float, extra: Float = 0f): Offset {
        val p = pm.getPosition(((f % 1f + 1f) % 1f) * length)
        val dir = (p - center).let { v -> val m = v.getDistance().coerceAtLeast(0.001f); v / m }
        return p + dir * (out + extra)
    }

    /** Ângulo (graus) apontando para fora do centro, para girar enfeites. */
    fun angle(f: Float): Float {
        val p = pm.getPosition(((f % 1f + 1f) % 1f) * length)
        return (atan2(p.y - center.y, p.x - center.x) * 180f / PI.toFloat()) + 90f
    }
}

/** Desenha o contorno levemente maior (anel em volta da foto). */
private fun DrawScope.ring(path: Path, gap: Float, brush: Brush, width: Float, alpha: Float = 1f) {
    val s = (min(size.width, size.height) + 2 * gap) / min(size.width, size.height)
    scale(s, s, pivot = center) { drawPath(path, brush, alpha = alpha, style = Stroke(width / s, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
}

private fun DrawScope.ring(path: Path, gap: Float, color: Color, width: Float, alpha: Float = 1f) =
    ring(path, gap, SolidColor(color), width, alpha)

private fun wave(t: Float, speed: Float = 1f, phase: Float = 0f) = sin((t * speed + phase) * 2f * PI.toFloat())

// ---------------- molduras ----------------

private fun DrawScope.drawFrame(id: String, path: Path, color: Color, t: Float) {
    val s = min(size.width, size.height)
    val w = s * 0.045f // espessura base
    when (id) {
        "neon" -> {
            // luz que pulsa e muda entre a cor da pessoa, rosa e ciano
            val pulse = 0.55f + 0.45f * (0.5f + 0.5f * wave(t, 2f))
            val c = lerp(lerp(color, Color(0xFFFF3DD6), 0.5f + 0.5f * wave(t)), Color(0xFF29F3FF), 0.5f + 0.5f * wave(t, 1f, 0.33f))
            for (i in 5 downTo 1) ring(path, w * 1.2f, c, w * i * 1.4f, alpha = 0.10f * pulse)
            ring(path, w * 1.2f, c, w * 1.1f, alpha = pulse)
            ring(path, w * 1.2f, Color.White, w * 0.35f, alpha = 0.8f * pulse)
        }

        "rainbow" -> {
            val cols = listOf(0xFFFF5252, 0xFFFFB74D, 0xFFFFEE58, 0xFF66BB6A, 0xFF42A5F5, 0xFFAB47BC, 0xFFFF5252).map { Color(it) }
            rotate(t * 360f) { ring(path, w * 1.3f, Brush.sweepGradient(cols, center), w * 1.6f) }
        }

        "vines" -> {
            val al = Along(path, center, w * 0.7f)
            val stem = Color(0xFF3E8E41); val leaf = Color(0xFF66BB6A); val leafDark = Color(0xFF2E7D32)
            // caule ondulado em volta
            val vine = Path()
            val n = 120
            for (i in 0..n) {
                val f = i / n.toFloat()
                val p = al.at(f, w * 0.55f * sin(f * 2f * PI.toFloat() * 9f + wave(t) * 0.6f))
                if (i == 0) vine.moveTo(p.x, p.y) else vine.lineTo(p.x, p.y)
            }
            drawPath(vine, stem, style = Stroke(w * 0.8f, cap = StrokeCap.Round))
            // folhas que balançam
            for (i in 0 until 14) {
                val f = (i + 0.5f) / 14f
                val p = al.at(f, if (i % 2 == 0) w * 1.0f else -w * 0.1f)
                val ang = al.angle(f) + (if (i % 2 == 0) 35f else -35f) + 8f * wave(t, 1f, f)
                rotate(ang, p) {
                    drawOval(if (i % 3 == 0) leafDark else leaf, topLeft = Offset(p.x - w * 0.7f, p.y - w * 1.7f), size = Size(w * 1.4f, w * 2.0f))
                }
            }
            // florzinhas
            for (i in 0 until 3) {
                val p = al.at(0.1f + i * 0.33f, w * 0.8f)
                flower(p, w * 1.1f, Color(0xFFFFF59D), Color(0xFFFFB300))
            }
        }

        "circuit" -> {
            val al = Along(path, center, w * 1.1f)
            val trace = Color(0xFF1DE9B6); val board = Color(0xFF00352C)
            ring(path, w * 1.1f, board, w * 1.6f)
            ring(path, w * 1.1f, trace, w * 0.45f)
            // trilhas saindo para fora com terminais
            for (i in 0 until 12) {
                val f = i / 12f + 0.02f
                val a = al.at(f); val b = al.at(f, w * (if (i % 2 == 0) 2.2f else 1.4f))
                val c = al.at(f + 0.025f, w * (if (i % 2 == 0) 2.2f else 1.4f))
                drawLine(trace, a, b, w * 0.35f, StrokeCap.Round)
                drawLine(trace, b, c, w * 0.35f, StrokeCap.Round)
                drawCircle(board, w * 0.55f, c)
                drawCircle(trace, w * 0.55f, c, style = Stroke(w * 0.3f))
            }
            // pulsos de energia correndo pelo contorno
            for (k in 0 until 3) {
                val f = t + k / 3f
                for (j in 0 until 6) {
                    val p = al.at(f - j * 0.008f)
                    drawCircle(Color(0xFFB9FFF0), w * (0.75f - j * 0.1f), p, alpha = 1f - j * 0.15f)
                }
            }
        }

        "stars" -> {
            val al = Along(path, center, w * 1.3f)
            ring(path, w * 1.3f, Color(0xFFFFF59D), w * 0.4f, alpha = 0.7f)
            for (i in 0 until 7) {
                val f = i / 7f + t * 0.15f
                val tw = 0.55f + 0.45f * wave(t, 3f, i * 0.37f)
                sparkle(al.at(f, w * 0.6f * (i % 3)), w * (1.9f + 0.9f * (i % 2)) * (0.7f + 0.3f * tw), Color(0xFFFFF9C4), tw)
            }
        }

        "fire" -> {
            val al = Along(path, center, w * 0.6f)
            ring(path, w * 0.8f, Color(0xFFFF6D00), w * 0.9f, alpha = 0.9f)
            // chamas maiores embaixo, menores dos lados, nenhuma em cima
            for (i in 0 until 20) {
                val f = i / 26f
                val p = al.at(f)
                val down = ((p.y - center.y) / (s / 2f)).coerceIn(-1f, 1f) // -1 em cima, 1 embaixo
                if (down < -0.35f) continue
                val h = w * (2.6f + 3.4f * (down + 0.35f) / 1.35f) * (0.75f + 0.25f * wave(t, 4f, i * 0.21f))
                flame(p, al.angle(f), w * 2.0f, h)
            }
        }

        "ice" -> {
            val al = Along(path, center, w * 1.0f)
            val shimmer = 0.6f + 0.4f * wave(t, 1f)
            ring(path, w * 1.0f, Brush.sweepGradient(listOf(Color(0xFFB3E5FC), Color.White, Color(0xFF81D4FA), Color(0xFFE1F5FE), Color(0xFFB3E5FC)), center), w * 1.1f)
            for (i in 0 until 11) {
                val f = i / 11f
                val base = al.at(f); val len = w * (1.3f + (i % 3) * 0.6f)
                rotate(al.angle(f), base) {
                    val tri = Path().apply {
                        moveTo(base.x - w * 0.55f, base.y); lineTo(base.x, base.y - len); lineTo(base.x + w * 0.55f, base.y); close()
                    }
                    drawPath(tri, Color(0xFFE1F5FE), alpha = 0.95f)
                    drawPath(tri, Color(0xFF4FC3F7), style = Stroke(w * 0.18f))
                }
            }
            sparkle(al.at(t, w * 1.2f), w * 1.3f, Color.White, shimmer)
        }

        "flowers" -> {
            val al = Along(path, center, w * 1.1f)
            ring(path, w * 1.1f, Color(0xFF81C784), w * 0.45f)
            val petals = listOf(0xFFF48FB1, 0xFFCE93D8, 0xFFFFF176, 0xFF90CAF9, 0xFFFFAB91).map { Color(it) }
            for (i in 0 until 12) {
                val f = i / 12f
                val bob = 1f + 0.08f * wave(t, 1f, i / 12f)
                flower(al.at(f, w * 0.3f), w * 2.0f * bob * (if (i % 2 == 0) 1f else 0.8f), petals[i % petals.size], Color(0xFFFFE082))
            }
        }

        "hearts" -> {
            val al = Along(path, center, w * 1.2f)
            ring(path, w * 1.2f, Color(0xFFFF80AB), w * 0.4f, alpha = 0.8f)
            for (i in 0 until 9) {
                val f = i / 9f + t * 0.05f
                val beat = 1f + 0.18f * maxOf(0f, wave(t, 2f, i * 0.11f))
                heart(al.at(f, w * 0.6f), w * 2.6f * beat * (if (i % 2 == 0) 1f else 0.75f), if (i % 3 == 0) Color(0xFFE91E63) else Color(0xFFFF4081))
            }
        }

        "crown" -> {
            val gold = Brush.linearGradient(listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFFE082)))
            ring(path, w * 0.9f, gold, w * 0.8f)
            val cw = s * 0.62f; val ch = s * 0.30f
            val top = -ch * 0.62f
            val left = center.x - cw / 2f
            val crown = Path().apply {
                moveTo(left, top + ch)
                lineTo(left, top + ch * 0.35f)
                lineTo(left + cw * 0.25f, top + ch * 0.65f)
                lineTo(left + cw * 0.5f, top)
                lineTo(left + cw * 0.75f, top + ch * 0.65f)
                lineTo(left + cw, top + ch * 0.35f)
                lineTo(left + cw, top + ch)
                close()
            }
            rotate(-8f, Offset(center.x, top + ch)) {
                drawPath(crown, gold)
                drawPath(crown, Color(0xFFB7791F), style = Stroke(w * 0.35f, join = StrokeJoin.Round))
                val gems = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047))
                gems.forEachIndexed { i, g -> drawCircle(g, w * 0.7f, Offset(left + cw * (0.25f + i * 0.25f), top + ch * 0.8f)) }
                drawCircle(Color.White, w * 0.45f, Offset(left + cw * 0.5f, top), alpha = 0.6f + 0.4f * wave(t, 2f))
            }
        }

        "catears" -> {
            val fur = lerp(color, Color.Black, 0.25f); val inner = Color(0xFFF8BBD0)
            fun ear(cx: Float, tilt: Float) = rotate(tilt, Offset(cx, s * 0.12f)) {
                val e = Path().apply { moveTo(cx - s * 0.17f, s * 0.14f); lineTo(cx - s * 0.03f, -s * 0.2f); lineTo(cx + s * 0.13f, s * 0.08f); close() }
                drawPath(e, fur)
                val i = Path().apply { moveTo(cx - s * 0.1f, s * 0.11f); lineTo(cx - s * 0.03f, -s * 0.1f); lineTo(cx + s * 0.06f, s * 0.07f); close() }
                drawPath(i, inner)
            }
            val twitch = 4f * maxOf(0f, wave(t, 1f)) // uma orelha mexe de vez em quando
            ear(s * 0.27f, -18f - twitch)
            ear(s * 0.73f, 18f)
            ring(path, w * 0.8f, fur, w * 0.5f, alpha = 0.6f)
        }

        "pixel" -> {
            // borda quadriculada estilo 8 bits, em volta do quadrado da foto
            val px = s / 14f
            val c1 = color; val c2 = lerp(color, Color.White, 0.55f); val c3 = lerp(color, Color.Black, 0.45f)
            val off = (t * 14).toInt()
            for (i in -1..14) for (j in listOf(-1, 14)) {
                val k = (i + off) % 3
                drawRect(if (k == 0) c2 else if (k == 1) c1 else c3, Offset(i * px, j * px), Size(px, px))
                drawRect(if (k == 0) c2 else if (k == 1) c1 else c3, Offset(j * px, i * px), Size(px, px))
            }
            drawRect(Color.Black, Offset(-px, -px), Size(16 * px, 16 * px), style = Stroke(px * 0.25f), alpha = 0.5f)
        }
    }
}

// ---------------- enfeites ----------------

private fun DrawScope.flower(c: Offset, r: Float, petal: Color, middle: Color) {
    for (k in 0 until 5) {
        val a = k * 72f * PI.toFloat() / 180f
        drawCircle(petal, r * 0.5f, Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f))
    }
    drawCircle(middle, r * 0.38f, c)
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color, alpha: Float) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticBezierTo(c.x, c.y, c.x + r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawCircle(color, r * 0.9f, c, alpha = 0.18f * alpha.coerceIn(0f, 1f))
    drawPath(p, color, alpha = alpha.coerceIn(0.15f, 1f))
}

private fun DrawScope.heart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.45f)
        cubicTo(c.x - r * 0.9f, c.y - r * 0.15f, c.x - r * 0.45f, c.y - r * 0.85f, c.x, c.y - r * 0.35f)
        cubicTo(c.x + r * 0.45f, c.y - r * 0.85f, c.x + r * 0.9f, c.y - r * 0.15f, c.x, c.y + r * 0.45f)
        close()
    }
    drawPath(p, color)
    drawCircle(Color.White, r * 0.12f, Offset(c.x - r * 0.28f, c.y - r * 0.3f), alpha = 0.7f)
}

private fun DrawScope.flame(base: Offset, angle: Float, wid: Float, h: Float) = rotate(angle, base) {
    val p = Path().apply {
        moveTo(base.x - wid / 2f, base.y)
        quadraticBezierTo(base.x - wid * 0.7f, base.y - h * 0.5f, base.x, base.y - h)
        quadraticBezierTo(base.x + wid * 0.7f, base.y - h * 0.5f, base.x + wid / 2f, base.y)
        close()
    }
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFE53935)), base.y - h, base.y))
    val inner = Path().apply {
        moveTo(base.x - wid / 4f, base.y)
        quadraticBezierTo(base.x - wid * 0.3f, base.y - h * 0.3f, base.x, base.y - h * 0.55f)
        quadraticBezierTo(base.x + wid * 0.3f, base.y - h * 0.3f, base.x + wid / 4f, base.y)
        close()
    }
    drawPath(inner, Color(0xFFFFF9C4), alpha = 0.85f)
}
