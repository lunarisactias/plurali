package com.plurali.app.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.plurali.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lembretes sem servidor: calcula o próximo aviso (lembretes de horário, eventos, aniversários,
 * timers de front, enquetes com prazo) e agenda um único alarme do Android para ele.
 */
object Scheduler {
    private data class Due(
        val time: Long, val key: String, val title: String, val text: String, val channel: String, val route: String?,
        val med: Pair<String, Long>? = null, // remédio e horário da dose (para o botão "Tomei")
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun LocalDate.at(h: Int, m: Int) = atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun dueBetween(d: AppData, from: Long, to: Long): List<Due> {
        val out = mutableListOf<Due>()
        if (to <= from) return out
        val firstDay = from.toDate()
        val lastDay = to.toDate()

        // lembretes de horário
        d.reminders.filter { it.enabled }.forEach { r ->
            var day = firstDay
            while (day <= lastDay) {
                if (day.dayOfWeek.value in r.days) {
                    val t = day.at(r.hour, r.minute)
                    if (t in (from + 1)..to) {
                        val who = r.memberIds.mapNotNull { id -> d.members.find { it.id == id }?.name }
                        val text = listOf(r.text, if (who.isNotEmpty()) "Para: ${who.joinToString(", ")}" else "").filter { it.isNotBlank() }.joinToString("\n")
                        out += Due(t, "rem:${r.id}:$t", r.title, text.ifBlank { "Lembrete" }, Notifier.CH_REMIND, null)
                    }
                }
                day = day.plusDays(1)
            }
        }

        // eventos do calendário com lembrete (e aniversários às 9h)
        val events = d.events.filter { it.remindMin != null } +
            (if (Prefs.birthdayNotif) d.birthdayEvents().map { it.copy(remindMin = 0) } else emptyList())
        events.forEach { e ->
            val before = (e.remindMin ?: 0).toLong() * 60_000
            var day = firstDay
            val end = (to + before).toDate()
            while (day <= end) {
                if (e.occursOn(day)) {
                    val evTime = if (e.allDay) day.at(9, 0) else {
                        val lt = Instant.ofEpochMilli(e.start).atZone(zone).toLocalTime()
                        day.at(lt.hour, lt.minute)
                    }
                    val t = evTime - before
                    if (t in (from + 1)..to) {
                        val whenTxt = when {
                            before == 0L -> if (e.allDay) "Hoje" else "Agora"
                            before >= 86_400_000 -> "Amanhã"
                            else -> "Em ${before / 60_000} min"
                        }
                        out += Due(t, "ev:${e.id}:$t", e.title, "$whenTxt${if (e.description.isNotBlank()) " · ${e.description}" else ""}", Notifier.CH_REMIND, "calendar")
                    }
                }
                day = day.plusDays(1)
            }
        }

        // timers de front
        d.currentFronts().forEach { f ->
            val s = d.subject(f.subjectId) ?: return@forEach
            d.timers.filter { it.enabled && (it.subjectId == null || it.subjectId == f.subjectId) }.forEach { tm ->
                val t = f.start + tm.afterMin * 60_000L
                if (t in (from + 1)..to) {
                    val base = if (tm.afterMin == 0) "${s.name} entrou no front" else "${s.name} está no front há ${fmtMin(tm.afterMin)}"
                    out += Due(t, "tm:${tm.id}:${f.id}", base, tm.message.ifBlank { "Timer de front" }, Notifier.CH_REMIND, null)
                }
            }
        }

        // remédios: na hora da dose e, se ninguém registrou, de novo 30 min depois
        d.meds.filter { it.active }.forEach { m ->
            var day = firstDay.minusDays(1)
            while (day <= lastDay) {
                if (day.dayOfWeek.value in m.days) m.times.forEach { t ->
                    val slot = day.at(t / 60, t % 60)
                    if (d.medTakes.none { it.medId == m.id && it.slot == slot }) {
                        val hh = "%02d:%02d".format(t / 60, t % 60)
                        if (slot in (from + 1)..to) out += Due(
                            slot, "med:${m.id}:$slot", "Hora do remédio: ${m.name}",
                            listOf(m.dose, "Toque em “Tomei” quando tomar.").filter { it.isNotBlank() }.joinToString(" · "),
                            Notifier.CH_REMIND, "meds", m.id to slot,
                        )
                        val late = slot + 30 * 60_000L
                        if (late in (from + 1)..to) out += Due(
                            late, "med:${m.id}:$slot", "Ainda não registrado: ${m.name}",
                            "Era às $hh. Se já tomou, toque em “Tomei” para ninguém tomar de novo.", Notifier.CH_REMIND, "meds", m.id to slot,
                        )
                    }
                }
                day = day.plusDays(1)
            }
        }

        // enquetes com prazo
        if (Prefs.pollNotif) d.polls.forEach { p ->
            val t = p.endTime ?: return@forEach
            if (t in (from + 1)..to) {
                val leader = p.leader()
                val res = if (leader == null) "Sem votos" else "Mais votada: ${leader.name} (${p.votes.count { it.optionId == leader.id }} votos)"
                out += Due(t, "poll:${p.id}", "Enquete encerrada: ${p.title}", res, Notifier.CH_POLL, "poll/${p.id}")
            }
        }
        return out.sortedBy { it.time }
    }

    private fun fmtMin(m: Int) = when {
        m % 60 == 0 -> "${m / 60}h"
        m > 60 -> "${m / 60}h ${m % 60}min"
        else -> "$m min"
    }

    /** Mostra o que venceu desde a última conferência e agenda o próximo alarme. */
    @Synchronized
    fun fire(ctx: Context) {
        val now = System.currentTimeMillis()
        var from = Prefs.lastAlarmCheck
        if (from > now) from = now
        if (now - from > 6 * 3_600_000L) from = now - 6 * 3_600_000L // não inundar depois de muito tempo desligado
        val d = Store.data.value
        dueBetween(d, from, now).forEach { due ->
            val actions = due.med?.let { (medId, slot) ->
                val pi = PendingIntent.getBroadcast(
                    ctx, due.key.hashCode(),
                    Intent(ctx, MedActionReceiver::class.java).setAction(MedActionReceiver.TAKEN)
                        .putExtra("med", medId).putExtra("slot", slot).putExtra("nid", due.key.hashCode()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                listOf("Tomei" to pi)
            }.orEmpty()
            Notifier.post(ctx, due.key.hashCode(), due.channel, due.title, due.text, due.route, actions)
        }
        Prefs.lastAlarmCheck = now
        reschedule(ctx, d, now)
    }

    private fun reschedule(ctx: Context, d: AppData, now: Long) {
        val next = dueBetween(d, now, now + 7 * 86_400_000L).firstOrNull()?.time ?: (now + 12 * 3_600_000L)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            // alarme exato: lembrete de remédio não pode atrasar (USE_EXACT_ALARM no manifesto)
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, next, 60_000L, pi)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        PluraliApplication.ensure(ctx)
        Scheduler.fire(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        PluraliApplication.ensure(ctx)
        Scheduler.fire(ctx)
        Notifier.front(ctx, Store.data.value)
        AutoBackup.schedule(ctx)
        CloudBackupSync.schedule(ctx)
        CompanionInbox.schedule(ctx)
    }
}

/** Botões "⇄ Nome" da notificação fixa. */
class FrontActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        PluraliApplication.ensure(ctx)
        if (intent.action == SWITCH) {
            val id = intent.getStringExtra("id") ?: return
            Store.switchFront(id, intent.getBooleanExtra("custom", false))
        }
    }

    companion object { const val SWITCH = "com.plurali.app.SWITCH_FRONT" }
}

/** Botão "Tomei" da notificação do remédio: registra a dose com quem está no front. */
class MedActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        PluraliApplication.ensure(ctx)
        if (intent.action != TAKEN) return
        val medId = intent.getStringExtra("med") ?: return
        val slot = intent.getLongExtra("slot", 0L)
        val nid = intent.getIntExtra("nid", 0)
        val d = Store.data.value
        val m = d.meds.find { it.id == medId } ?: return
        val now = System.currentTimeMillis()
        val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val already = d.medTakes.lastOrNull { it.medId == medId && it.slot == slot && !it.skipped }
        if (already != null) {
            Notifier.post(ctx, nid, Notifier.CH_REMIND, "${m.name}: já estava registrado", "Tomado às ${hm.format(already.takenAt)} por ${already.byName}. Não tome de novo.", "meds")
            return
        }
        val byId = d.currentFronts().map { it.subjectId }.firstOrNull { id -> d.members.any { it.id == id } }
        val byName = d.members.find { it.id == byId }?.name ?: "Sistema"
        Store.addMedTake(MedTake(newId(), medId, slot, now, byId, byName))
        Notifier.post(ctx, nid, Notifier.CH_REMIND, "✓ ${m.name} registrado", "Tomado às ${hm.format(now)} por $byName.", "meds")
    }

    companion object { const val TAKEN = "com.plurali.app.MED_TAKEN" }
}
