package com.plurali.app.sys

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.plurali.app.MainActivity
import com.plurali.app.R
import com.plurali.app.data.*

object Notifier {
    const val CH_FRONT = "front"
    const val CH_REMIND = "lembretes"
    const val CH_MSG = "mensagens"
    const val CH_POLL = "enquetes"
    private const val FRONT_ID = 1

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FRONT, "Quem está no front", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notificação fixa com quem está no front e atalhos para trocar"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lembretes de horário, eventos do calendário e timers de front"
        })
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Mensagens", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Mensagens não lidas quando alguém entra no front"
        })
        nm.createNotificationChannel(NotificationChannel(CH_POLL, "Enquetes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Enquetes encerradas"
        })
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(ctx: Context, route: String? = null, req: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra("route", route)
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun post(ctx: Context, id: Int, channel: String, title: String, text: String, route: String? = null, actions: List<Pair<String, PendingIntent>> = emptyList()) {
        if (!canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, channel)
            .apply { actions.forEach { (label, pi) -> addAction(0, label, pi) } }
            .setSmallIcon(R.drawable.ic_stat_plurali)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, route, id))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat_plurali)
                    .setContentTitle("Plurali").setContentText("Toque para ver").build()
            )
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun frontNames(d: AppData): String =
        d.currentFronts().mapNotNull { d.subject(it.subjectId)?.name }.joinToString(", ")

    /** Notificação fixa com quem está no front e até 2 atalhos para trocar. */
    fun front(ctx: Context, d: AppData) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!Prefs.frontNotif || !canPost(ctx)) { nm.cancel(FRONT_ID); return }
        val current = d.currentFronts()
        val names = frontNames(d)
        val since = current.minOfOrNull { it.start }
        val fronting = current.map { it.subjectId }.toSet()
        // atalhos: quem esteve no front mais recentemente e não está agora
        val recent = d.fronts.filter { it.end != null }.sortedByDescending { it.end }
            .map { it.subjectId }.distinct().filter { it !in fronting }
            .mapNotNull { d.subject(it) }.filter { s -> d.members.find { it.id == s.id }?.archived != true }
            .take(2)
        val b = NotificationCompat.Builder(ctx, CH_FRONT)
            .setSmallIcon(R.drawable.ic_stat_plurali)
            .setContentTitle(if (names.isBlank()) "Ninguém no front" else "No front: $names")
            .setContentText(if (since != null) "Desde ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(since)} · toque para alterar" else "Toque para registrar quem está no front")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openApp(ctx, null, 99))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, CH_FRONT).setSmallIcon(R.drawable.ic_stat_plurali)
                    .setContentTitle("Plurali").setContentText("Toque para abrir").build()
            )
        recent.forEachIndexed { i, s ->
            val pi = PendingIntent.getBroadcast(
                ctx, 200 + i,
                Intent(ctx, FrontActionReceiver::class.java).setAction(FrontActionReceiver.SWITCH)
                    .putExtra("id", s.id).putExtra("custom", s.custom),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "⇄ ${s.name}", pi)
        }
        runCatching { nm.notify(FRONT_ID, b.build()) }
    }
}
