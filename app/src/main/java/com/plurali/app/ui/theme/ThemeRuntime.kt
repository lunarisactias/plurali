package com.plurali.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plurali.app.data.*
import com.plurali.app.ui.Session
import com.plurali.app.ui.Who
import com.plurali.app.ui.rememberAvatar

/** Estilo em uso agora (tema + escolhas do perfil). */
data class UiStyle(
    val theme: ThemeDef,
    val a: Appearance,
    val cardShape: Shape,
    val avatarShape: Shape,
    val avatarId: String,
    val strip: Strip,
    val border: Border,
    val accent: Color,
) {
    /** Espaço extra que alguns formatos pedem (orelhinhas em cima, chamas embaixo). */
    val cardTopExtra: Dp get() = if (cardShape === CatCardShape) 9.dp else 0.dp
    val cardBottomExtra: Dp get() = if (cardShape === FlameCardShape) 6.dp else 0.dp
    val headerMark: String get() = if (a.headerMarks) theme.headerMark else ""
}

val LocalUi = compositionLocalOf { buildUi(Appearance(), dark = true) }

fun buildUi(a: Appearance, dark: Boolean): UiStyle {
    val th = themeOf(a.theme, dark)
    val avId = if (a.avatarShape == "theme") th.avatar else a.avatarShape
    return UiStyle(
        theme = th, a = a,
        cardShape = cardShapeOf(a.cardShape, th),
        avatarShape = avatarShapeOf(avId) ?: avatarShapeOf("rounded")!!,
        avatarId = avId,
        strip = when (a.strip) {
            "left" -> Strip.LEFT; "top" -> Strip.TOP; "border" -> Strip.BORDER; "glow" -> Strip.GLOW
            "corner" -> Strip.CORNER; "tape" -> Strip.TAPE; "spots" -> Strip.SPOTS; "none" -> Strip.NONE; else -> th.strip
        },
        border = when (a.border) {
            "none" -> Border.NONE; "solid" -> Border.SOLID; "dashed" -> Border.DASHED; "dotted" -> Border.DOTTED
            "double" -> Border.DOUBLE; "sketch" -> Border.SKETCH; "glow" -> Border.GLOW; else -> th.border
        },
        accent = Color(a.accent ?: th.palette.accent),
    )
}

// ---------------- qual aparência usar ----------------

fun appearanceKey(who: Who?): String = when (who) {
    is Who.Member -> who.id
    Who.Guest -> "guest"
    else -> "system"
}

/** Antes dos temas, a aparência era do aparelho: vira a do perfil Sistema. */
private fun legacy() = Appearance(
    mode = Prefs.theme,
    accent = Prefs.accent.takeIf { it != 0xFFD6AC5E },
    textScale = Prefs.textScale,
)

/** Tema padrão: tela de login, visitantes e quem ainda não escolheu um tema próprio (ex.: alter novo). */
fun defaultAppearance(): Appearance = legacy()

/**
 * Qual aparência usar. Só carrega o tema da pessoa depois que ela entra na conta:
 *  - tela "Quem está usando?" e visitante → padrão
 *  - membro logado → o dele (ou o padrão, se não escolheu)
 *  - Sistema (ou app sem logins) → o do Sistema
 */
fun resolveAppearance(d: AppData, who: Who?): Appearance {
    val sysMode = !d.loginsEnabled || who == Who.Admin
    if (sysMode) {
        val sys = d.appearance["system"] ?: defaultAppearance()
        if (sys.followFront) d.currentFronts().firstNotNullOfOrNull { d.appearance[it.subjectId] }?.let { return it }
        return sys
    }
    return when (who) {
        is Who.Member -> d.appearance[who.id] ?: defaultAppearance()
        else -> defaultAppearance()
    }
}

@Composable
fun currentAppearance(): Appearance {
    val d by Store.data.collectAsState()
    return resolveAppearance(d, Session.who)
}

fun Appearance.isDark(systemDark: Boolean): Boolean =
    if (theme == "classic") when (mode) { "light" -> false; "system" -> systemDark; else -> true }
    else themeOf(theme, true).palette.dark

@Composable
fun Appearance.isDark(): Boolean = isDark(isSystemInDarkTheme())

// ---------------- fundo e enfeites ----------------

@Composable
private fun animPhase(on: Boolean, ms: Int): State<Float> {
    if (!on) return remember { mutableFloatStateOf(0f) }
    val tr = rememberInfiniteTransition(label = "fundo")
    return tr.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "fase")
}

/** Fundo da tela: cor do tema, imagem escolhida (se houver) e os desenhos do tema. */
@Composable
fun ThemedBackground(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val bg = MaterialTheme.colorScheme.background
    val wp = rememberAvatar(ui.a.wallpaper)
    val t by animPhase(ui.a.animate && ui.a.pattern > 0f, 30_000)
    Box(modifier.background(bg)) {
        if (wp != null) {
            Image(wp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(bg.copy(alpha = ui.a.wallpaperDim)))
        }
        Canvas(Modifier.fillMaxSize()) { drawThemePattern(ui.theme, ui.a.pattern, t) }
    }
}

/** Faixa de enfeite embaixo da barra de título (patinhas, trepadeira, teia, chamas…). */
@Composable
fun ThemeDecorBand() {
    val ui = LocalUi.current
    if (!ui.a.decor || ui.theme.decor == "none") return
    val t by animPhase(ui.a.animate, 6_000)
    Canvas(Modifier.fillMaxWidth().height(12.dp)) { drawThemeDecor(ui.theme, t) }
}

// ---------------- cartões ----------------

private fun DrawScope.outlineOf(shape: Shape) = shape.createOutline(size, layoutDirection, this)

private fun DrawScope.strokeOutline(shape: Shape, color: Color, width: Float, effect: PathEffect? = null) =
    drawOutline(outlineOf(shape), color, style = Stroke(width, pathEffect = effect, cap = StrokeCap.Round))

/**
 * Cartão no estilo do tema: formato, fundo, cor do membro (faixa, borda, brilho, canto) e contorno.
 * memberColor = null para cartões sem dono (avisos, configurações…).
 */
fun Modifier.themedCard(ui: UiStyle, container: Color, memberColor: Color?, line: Color, text: Color): Modifier {
    val shape = ui.cardShape
    val pal = ui.theme.palette
    val glowColor = when {
        ui.border == Border.GLOW -> ui.accent
        ui.strip == Strip.GLOW && memberColor != null -> memberColor
        else -> null
    }
    val bw = ui.theme.borderWidth
    return this
        .drawBehind {
            // brilho por fora (antes de recortar)
            if (glowColor != null) {
                listOf(9f to 0.06f, 6f to 0.10f, 3.5f to 0.16f).forEach { (w, a) -> strokeOutline(shape, glowColor.copy(alpha = a), w * density) }
            }
        }
        .clip(shape)
        .then(
            if (pal.cardTop != null) Modifier.background(Brush.verticalGradient(listOf(Color(pal.cardTop), container)))
            else Modifier.background(container),
        )
        .drawBehind {
            val mc = memberColor ?: return@drawBehind
            when (ui.strip) {
                Strip.LEFT -> drawRect(mc, Offset.Zero, androidx.compose.ui.geometry.Size(5 * density, size.height))
                Strip.TOP -> drawRect(mc, Offset(0f, ui.cardTopExtra.toPx()), androidx.compose.ui.geometry.Size(size.width, 4 * density))
                Strip.CORNER -> {
                    // coraçãozinho na cor do membro, no canto de cima
                    val s = 9 * density
                    val cx = size.width - 18 * density
                    val cy = ui.cardTopExtra.toPx() + 16 * density
                    val heart = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy + s * 0.7f)
                        cubicTo(cx - s * 1.1f, cy - s * 0.1f, cx - s * 0.6f, cy - s, cx, cy - s * 0.36f)
                        cubicTo(cx + s * 0.6f, cy - s, cx + s * 1.1f, cy - s * 0.1f, cx, cy + s * 0.7f); close()
                    }
                    drawCircle(mc.copy(alpha = 0.18f), 16 * density, Offset(cx, cy))
                    drawPath(heart, mc)
                }
                Strip.SPOTS -> {
                    // faixa no topo com bolinhas pretas (joaninha)
                    val top = ui.cardTopExtra.toPx()
                    drawRect(mc, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, 9 * density))
                    var x = 10 * density; var k = 0
                    while (x < size.width) {
                        drawCircle(Color.Black.copy(alpha = 0.85f), (if (k % 2 == 0) 2.6f else 1.9f) * density, Offset(x, top + (if (k % 2 == 0) 3.6f else 5.6f) * density))
                        x += 17 * density; k++
                    }
                }
                Strip.TAPE -> {
                    // pedaço de fita adesiva atravessando o canto
                    rotate(-38f, Offset(14 * density, 10 * density)) {
                        drawRect(mc.copy(alpha = 0.55f), Offset(-16 * density, 2 * density), androidx.compose.ui.geometry.Size(62 * density, 15 * density))
                        for (k in 0 until 8) drawLine(Color.White.copy(alpha = 0.25f), Offset(-14 * density + k * 8 * density, 2 * density), Offset(-18 * density + k * 8 * density, 17 * density), strokeWidth = 2 * density)
                    }
                }
                Strip.GLOW -> drawRect(Brush.horizontalGradient(listOf(mc.copy(alpha = 0.30f), Color.Transparent), 0f, size.width * 0.45f))
                else -> {}
            }
        }
        .drawWithContent {
            drawContent()
            val w = bw * density
            when (ui.border) {
                Border.SOLID -> strokeOutline(shape, line, w * 1.6f)
                Border.DASHED -> strokeOutline(shape, line, w * 1.6f, PathEffect.dashPathEffect(floatArrayOf(7 * density, 5 * density)))
                Border.DOTTED -> strokeOutline(shape, ui.accent.copy(alpha = 0.55f), w * 1.6f, PathEffect.dashPathEffect(floatArrayOf(0.1f, 6 * density)))
                Border.DOUBLE -> {
                    strokeOutline(shape, text.copy(alpha = 0.45f), w * 2f)
                    val k = 4 * density
                    scale((size.width - 2 * k) / size.width, (size.height - 2 * k) / size.height) { strokeOutline(shape, text.copy(alpha = 0.35f), w) }
                }
                Border.SKETCH -> {
                    strokeOutline(shape, text.copy(alpha = 0.75f), w * 1.4f)
                    translate(1.2f * density, -0.8f * density) { rotate(0.5f) { strokeOutline(shape, text.copy(alpha = 0.35f), w) } }
                }
                Border.GLOW -> strokeOutline(shape, ui.accent.copy(alpha = 0.55f), w)
                Border.NONE -> {}
            }
            if (ui.strip == Strip.BORDER && memberColor != null) strokeOutline(shape, memberColor, 2.4f * density)
        }
}
