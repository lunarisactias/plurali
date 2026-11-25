package com.plurali.app.sys

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.plurali.app.MainActivity
import com.plurali.app.R
import com.plurali.app.data.Store
import com.plurali.app.data.currentFronts

/** Widget da tela inicial: quem está no front. */
class FrontWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        PluraliApplication.ensure(ctx)
        render(ctx, mgr, ids)
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FrontWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val d = Store.data.value
            val names = Notifier.frontNames(d)
            val since = d.currentFronts().minOfOrNull { it.start }
            val v = RemoteViews(ctx.packageName, R.layout.widget_front)
            v.setTextViewText(R.id.w_names, names.ifBlank { "Ninguém no front" })
            v.setTextViewText(
                R.id.w_since,
                if (since != null) "desde " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(since) + " · toque para alterar"
                else "toque para registrar",
            )
            v.setOnClickPendingIntent(R.id.w_root, Notifier.openApp(ctx, null, 98))
            runCatching { mgr.updateAppWidget(ids, v) }
        }
    }
}

/** Atalho nas configurações rápidas (puxar a barra de notificações). */
class FrontTileService : TileService() {
    override fun onStartListening() {
        PluraliApplication.ensure(this)
        val t = qsTile ?: return
        val names = Notifier.frontNames(Store.data.value)
        t.label = "Front"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = names.ifBlank { "ninguém" }
        t.state = if (names.isBlank()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 97, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    companion object {
        fun refresh(ctx: Context) {
            runCatching { requestListeningState(ctx, ComponentName(ctx, FrontTileService::class.java)) }
        }
    }
}
