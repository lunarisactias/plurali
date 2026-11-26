package com.plurali.app.sys

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.plurali.app.data.*

class PluraliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensure(this)
        Effects.start(this)
    }

    companion object {
        /** Garante que dados e preferências estão carregados (receivers e workers podem rodar sem a tela aberta). */
        fun ensure(ctx: Context) {
            val app = ctx.applicationContext
            Prefs.init(app)
            Store.init(app)
            Notifier.channels(app)
            if (Store.onChange == null) Store.onChange = { Effects.onChange(app) }
            // usa o projeto Firebase do Plurali (troca também a configuração do simulador de testes)
            val c = Store.data.value.cloud
            if (c == null || c.dbUrl.startsWith("http://")) {
                Store.update(notify = false) { it.copy(cloud = CloudConfig(FirebaseProject.DB_URL, FirebaseProject.API_KEY)) }
            }
        }
    }
}

/** Efeitos de cada alteração nos dados: notificação fixa, widget, atalho, lembretes e aviso de mensagens. */
object Effects {
    private val main = Handler(Looper.getMainLooper())
    private var lastFront: Set<String>? = null
    private var pending: Runnable? = null

    fun start(ctx: Context) {
        lastFront = Store.data.value.currentFronts().map { it.subjectId }.toSet()
        apply(ctx)
    }

    /** Agrupa alterações seguidas (ex.: digitação) num único processamento. */
    fun onChange(ctx: Context) {
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable { apply(ctx) }
        pending = r
        main.postDelayed(r, 400)
    }

    private fun apply(ctx: Context) {
        val d = Store.data.value
        val cur = d.currentFronts().map { it.subjectId }.toSet()
        val prev = lastFront
        lastFront = cur
        if (prev != null && Prefs.msgNotif) {
            (cur - prev).forEach { id ->
                val m = d.members.find { it.id == id } ?: return@forEach
                val n = d.unreadFor(m.id)
                if (n > 0) Notifier.post(
                    ctx, ("msg:" + m.id).hashCode(), Notifier.CH_MSG,
                    "${m.name}, você tem $n ${if (n == 1) "mensagem não lida" else "mensagens não lidas"}",
                    "Toque para abrir as conversas", "chats",
                )
            }
        }
        // recados deixados para quem acabou de entrar no front
        if (prev != null) (cur - prev).forEach { id ->
            val m = d.members.find { it.id == id } ?: return@forEach
            val notes = d.frontNotes.filter { it.doneAt == null && (it.targetId == id || (it.targetId == null && it.authorId != id)) }
            if (notes.isNotEmpty()) Notifier.post(
                ctx, ("note:" + id).hashCode(), Notifier.CH_MSG,
                if (notes.size == 1) "Recado para ${m.name}" else "${notes.size} recados para ${m.name}",
                notes.first().text, "home",
            )
        }
        Notifier.front(ctx, d)
        FrontWidget.refresh(ctx)
        PhotoWidgets.refresh(ctx)
        FrontTileService.refresh(ctx)
        Scheduler.fire(ctx)
        CompanionSync.request(ctx)
    }
}
