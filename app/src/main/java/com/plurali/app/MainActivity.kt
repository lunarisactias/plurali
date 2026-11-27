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
        com.plurali.app.sys.CompanionInbox.schedule(this)
        com.plurali.shared.Push.ensure(this)
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
        (Session.who as? com.plurali.app.ui.Who.Member)?.let { Store.setLastSeen(it.id, System.currentTimeMillis()) }
    }

    override fun onStart() {
        super.onStart()
        // bloqueia de novo se ficou mais de 5 min fora do app
        if (Store.data.value.loginsEnabled && Session.stoppedAt > 0 &&
            System.currentTimeMillis() - Session.stoppedAt > Session.LOCK_AFTER_MS
        ) Session.who = null
        // ao abrir o app, confere se chegou recado de algum companion (no máximo uma vez por minuto)
        val now = System.currentTimeMillis()
        if (Store.data.value.companions.isNotEmpty() && now - inboxCheckedAt > 60_000L) {
            inboxCheckedAt = now
            com.plurali.app.sys.CompanionInbox.now(this)
        }
    }

    companion object {
        private var inboxCheckedAt = 0L
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

