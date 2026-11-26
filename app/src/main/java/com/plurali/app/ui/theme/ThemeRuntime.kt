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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plurali.app.data.*
import com.plurali.app.ui.Session
import com.plurali.app.ui.Who
import com.plurali.app.ui.rememberAvatar

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

/** Fundo do tema com a imagem de fundo escolhida (lida dos arquivos do app). */
@Composable
fun AppBackground(modifier: Modifier = Modifier) = ThemedBackground(modifier, rememberAvatar(LocalUi.current.a.wallpaper))
