package com.plurali.app

import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.plurali.app.data.Appearance
import com.plurali.app.data.Prefs
import com.plurali.app.ui.theme.LocalUi
import com.plurali.app.ui.theme.buildUi
import com.plurali.app.ui.theme.currentAppearance
import com.plurali.app.ui.theme.isDark
import com.plurali.app.data.Store
import com.plurali.app.sys.PluraliApplication
import com.plurali.app.ui.PluraliApp
import com.plurali.app.ui.Session

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PluraliApplication.ensure(this)
        handle(intent)
        setContent {
            val app = currentAppearance()
            val dark = app.isDark()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                )
            }
            PluraliTheme(app) { PluraliApp() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i?.getStringExtra("route")?.let { Session.pendingRoute = it }
    }

    override fun onStop() {
        super.onStop()
        Session.stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // bloqueia de novo se ficou mais de 5 min fora do app
        if (Store.data.value.loginsEnabled && Session.stoppedAt > 0 &&
            System.currentTimeMillis() - Session.stoppedAt > Session.LOCK_AFTER_MS
        ) Session.who = null
    }
}

/** Paleta medida a partir das telas do SimplyPlural (tema escuro). */
object Sp {
    val bg = Color(0xFF1D2228)
    val card = Color(0xFF232732)
    val field = Color(0xFF2B2E35)
    val pill = Color(0xFF384048)
    val text = Color(0xFFECECEE)
    val muted = Color(0xFFA0A3AB)
    val pronoun = Color(0xFF7C84A6)
    val line = Color(0xFF3B3E47)
}

/** Cores extras que mudam entre claro e escuro. */
data class ExtraColors(val dark: Boolean, val frontButton: Color, val timer: Color, val myBubble: Color)

val LocalExtra = staticCompositionLocalOf { ExtraColors(true, Color(0xFF22222C), Color(0xFF12131A), Color(0xFF384048)) }

private fun family(id: String): FontFamily = when (id) {
    "serif" -> FontFamily.Serif
    "cursive" -> FontFamily.Cursive
    "mono" -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

/** Tema do app montado a partir da aparência do perfil: cores, letras, formatos e enfeites. */
@Composable
fun PluraliTheme(a: Appearance, content: @Composable () -> Unit) {
    val dark = a.isDark()
    val ui = buildUi(a, dark)
    val p = ui.theme.palette
    val accent = ui.accent
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF1D2228) else Color.White
    fun c(v: Long) = Color(v)
    val scheme = (if (p.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = c(p.pill), onPrimaryContainer = c(p.text),
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = c(p.pill), onSecondaryContainer = c(p.text),
        tertiary = c(p.pronoun),
        background = c(p.bg), onBackground = c(p.text),
        surface = c(p.bg), onSurface = c(p.text),
        surfaceVariant = c(p.field), onSurfaceVariant = c(p.muted),
        surfaceContainerLowest = c(p.bg), surfaceContainerLow = c(p.card), surfaceContainer = c(p.card),
        surfaceContainerHigh = c(p.field), surfaceContainerHighest = c(p.field),
        surfaceTint = Color.Transparent,
        outline = c(p.line), outlineVariant = c(p.line),
        error = if (p.dark) Color(0xFFEF6B6B) else Color(0xFFC62828),
    )
    val titleFam = family(if (a.titleFont == "theme") ui.theme.titleFont else a.titleFont)
    val bodyFam = family(a.bodyFont)
    val w = FontWeight(ui.theme.titleWeight)
    val base = Typography()
    val typo = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = titleFam, fontWeight = w),
        displayMedium = base.displayMedium.copy(fontFamily = titleFam, fontWeight = w),
        displaySmall = base.displaySmall.copy(fontFamily = titleFam, fontWeight = w),
        headlineLarge = base.headlineLarge.copy(fontFamily = titleFam, fontWeight = w),
        headlineMedium = base.headlineMedium.copy(fontFamily = titleFam, fontWeight = w),
        headlineSmall = base.headlineSmall.copy(fontFamily = titleFam, fontWeight = w),
        titleLarge = base.titleLarge.copy(fontFamily = titleFam, fontWeight = w),
        titleMedium = base.titleMedium.copy(fontFamily = bodyFam),
        titleSmall = base.titleSmall.copy(fontFamily = titleFam, fontWeight = w),
        bodyLarge = base.bodyLarge.copy(fontFamily = bodyFam),
        bodyMedium = base.bodyMedium.copy(fontFamily = bodyFam),
        bodySmall = base.bodySmall.copy(fontFamily = bodyFam),
        labelLarge = base.labelLarge.copy(fontFamily = bodyFam),
        labelMedium = base.labelMedium.copy(fontFamily = bodyFam),
        labelSmall = base.labelSmall.copy(fontFamily = bodyFam),
    )
    val th = ui.theme
    val shapes = Shapes(extraSmall = th.small, small = th.small, medium = th.medium, large = th.large, extraLarge = th.large)
    val extra = ExtraColors(p.dark, c(p.frontButton), c(p.timer), c(p.bubble))
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalExtra provides extra,
        LocalUi provides ui,
        LocalDensity provides Density(density.density, density.fontScale * a.textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typo, shapes = shapes, content = content)
    }
}
