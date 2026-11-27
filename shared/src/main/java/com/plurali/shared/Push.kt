package com.plurali.shared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Avisos instantâneos pelo ntfy (gratuito, sem conta): em vez de conferir o Firebase a cada 15 min,
 * quem muda algo manda um "mudou" (sem conteúdo nenhum) para o endereço secreto do outro lado,
 * que só então baixa os dados (criptografados) do Firebase.
 *
 * Dois jeitos de receber:
 *  - NTFY: pelo app ntfy instalado no celular (protocolo UnifiedPush). Quase não gasta bateria.
 *  - OWN:  conexão própria com o ntfy.sh, com uma notificação fixa (não precisa instalar nada).
 */
object Push {
    const val OFF = "off"
    const val NTFY = "ntfy"
    const val OWN = "own"
    const val NTFY_PACKAGE = "io.heckel.ntfy"
    const val SERVER = "https://ntfy.sh"

    private const val UP_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val UP_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    internal const val UP_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"

    // ---- configurado por cada app (no Application.onCreate) ----
    /** Chegou um aviso: buscar os dados novos. */
    var onPing: ((Context) -> Unit)? = null
    /** A conexão própria (re)abriu: confere uma vez, sem contar como aviso recebido (se null, usa [onPing]). */
    var onReconnect: ((Context) -> Unit)? = null
    /** O endereço deste aparelho mudou: contar para o outro lado. */
    var onEndpoint: ((Context) -> Unit)? = null
    var notifIcon: Int = android.R.drawable.stat_notify_sync
    var openApp: ((Context) -> Intent)? = null
    /**
     * Na conexão própria, os canais a ouvir além do canal deste aparelho. O Companion ouve um canal por sistema,
     * calculado a partir do convite ([topicFor]): o Plurali sabe esse canal sem precisar ler nada no Firebase,
     * então ele não fica desatualizado (nem ao reinstalar o app).
     */
    var extraTopics: ((Context) -> List<String>)? = null

    /** Canal secreto do companion de um convite (só quem tem o convite consegue calcular). */
    fun topicFor(shareId: String, key: String) = "plurali_" + ShareCrypto.sha256("plurali-push|$shareId|$key").take(32)
    fun urlFor(shareId: String, key: String) = "$SERVER/" + topicFor(shareId, key)

    /** Os canais da conexão própria mudaram (ex.: passou a acompanhar outro sistema): reconecta. */
    fun restartOwn(ctx: Context) {
        val app = ctx.applicationContext
        if (mode(app) != OWN) return
        app.stopService(Intent(app, PushService::class.java))
        startService(app)
    }

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun mode(ctx: Context): String = sp(ctx).getString("mode", OFF) ?: OFF

    /** Endereço para onde os outros mandam o aviso (null = ainda não há). */
    fun endpoint(ctx: Context): String? = if (mode(ctx) == OFF) null else sp(ctx).getString("endpoint", null)

    /** Avisos instantâneos funcionando de fato (modo ligado e endereço pronto). */
    fun active(ctx: Context) = endpoint(ctx) != null

    fun error(ctx: Context): String? = sp(ctx).getString("error", null)

    fun ntfyInstalled(ctx: Context): Boolean = distributors(ctx).isNotEmpty()

    private fun distributors(ctx: Context): List<String> =
        ctx.packageManager.queryBroadcastReceivers(Intent(UP_REGISTER), 0).map { it.activityInfo.packageName }.distinct()
            .filter { it != ctx.packageName }

    internal fun upToken(ctx: Context): String = sp(ctx).getString("upToken", null)
        ?: java.util.UUID.randomUUID().toString().also { sp(ctx).edit().putString("upToken", it).apply() }

    internal fun distributor(ctx: Context): String? = sp(ctx).getString("distributor", null)

    /** Liga, troca ou desliga o jeito de receber avisos. */
    fun setMode(ctx: Context, mode: String) {
        val app = ctx.applicationContext
        val old = mode(app)
        if (old == NTFY && mode != NTFY) unregisterUp(app)
        if (old == OWN && mode != OWN) app.stopService(Intent(app, PushService::class.java))
        sp(app).edit().putString("mode", mode).remove("error").apply()
        when (mode) {
            OWN -> {
                setEndpoint(app, "$SERVER/" + ownTopic(app))
                startService(app)
            }
            NTFY -> {
                sp(app).edit().remove("endpoint").apply()
                registerUp(app)
            }
            else -> setEndpoint(app, null)
        }
    }

    /** Ao abrir o app / ligar o celular: garante que a conexão ou o registro continuam de pé. */
    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        when (mode(app)) {
            OWN -> startService(app)
            NTFY -> if (sp(app).getString("endpoint", null) == null || distributors(app).isEmpty()) registerUp(app)
        }
    }

    /** Manda um "mudou" para o endereço de outra pessoa. Sem conteúdo: os dados vão criptografados pelo Firebase. */
    fun send(endpoint: String?): Boolean {
        if (endpoint.isNullOrBlank() || !endpoint.startsWith("https://")) return false
        return runCatching {
            val c = URL(endpoint).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.doOutput = true
            val body = "1".toByteArray()
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val ok = c.responseCode in 200..299
            android.util.Log.i("PluraliPush", "aviso enviado: " + c.responseCode)
            c.disconnect()
            ok
        }.getOrDefault(false)
    }

    /** Pede para o sistema não pausar a conexão própria (sem isso o Android pode cortá-la com a tela desligada). */
    fun batteryOk(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    fun batteryIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))

    // ---------------- interno ----------------

    private fun ownTopic(ctx: Context): String = sp(ctx).getString("topic", null)
        ?: ("plurali_" + ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) })
            .also { sp(ctx).edit().putString("topic", it).apply() }

    internal fun setEndpoint(ctx: Context, e: String?) {
        val old = sp(ctx).getString("endpoint", null)
        sp(ctx).edit().apply { if (e == null) remove("endpoint") else putString("endpoint", e) }.remove("error").apply()
        if (old != e) onEndpoint?.invoke(ctx)
    }

    internal fun setError(ctx: Context, msg: String?) = sp(ctx).edit().putString("error", msg).apply()

    internal fun ping(ctx: Context) = onPing?.invoke(ctx.applicationContext)

    private fun startService(ctx: Context) {
        val i = Intent(ctx, PushService::class.java)
        runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
            .onFailure { setError(ctx, "O Android não deixou abrir a conexão agora. Abra o app para tentar de novo.") }
    }

    /** O PendingIntent só serve para o app ntfy saber, com segurança, qual app está pedindo. */
    private fun authIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 0, Intent("com.plurali.push.AUTH").setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)

    private fun registerUp(ctx: Context) {
        val d = distributors(ctx).let { l -> l.firstOrNull { it == NTFY_PACKAGE } ?: l.firstOrNull() }
        if (d == null) { setError(ctx, "Instale o app ntfy (gratuito) para receber os avisos por ele."); return }
        sp(ctx).edit().putString("distributor", d).apply()
        ctx.sendBroadcast(
            Intent(UP_REGISTER).setPackage(d)
                .putExtra("token", upToken(ctx))
                .putExtra("application", ctx.packageName)
                .putExtra("pi", authIntent(ctx))
                .putExtra("message", "Avisos do " + ctx.applicationInfo.loadLabel(ctx.packageManager)),
        )
    }

    private fun unregisterUp(ctx: Context) {
        val d = distributor(ctx) ?: return
        runCatching {
            ctx.sendBroadcast(
                Intent(UP_UNREGISTER).setPackage(d)
                    .putExtra("token", upToken(ctx)).putExtra("application", ctx.packageName).putExtra("pi", authIntent(ctx)),
            )
        }
        sp(ctx).edit().remove("endpoint").apply()
    }
}

/** Mensagens do app ntfy (protocolo UnifiedPush). */
class UpReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.getStringExtra("token") != Push.upToken(ctx)) return
        when (i.action) {
            "org.unifiedpush.android.connector.NEW_ENDPOINT" -> i.getStringExtra("endpoint")?.let {
                android.util.Log.i("PluraliPush", "app ntfy deu um endereço")
                Push.setEndpoint(ctx, it)
            }
            "org.unifiedpush.android.connector.REGISTRATION_FAILED" ->
                Push.setError(ctx, "O app ntfy recusou o registro" + (i.getStringExtra("message")?.let { ": $it" } ?: "."))
            "org.unifiedpush.android.connector.UNREGISTERED" -> Push.setEndpoint(ctx, null)
            "org.unifiedpush.android.connector.MESSAGE" -> {
                android.util.Log.i("PluraliPush", "aviso recebido pelo app ntfy")
                i.getStringExtra("id")?.let { id ->
                    Push.distributor(ctx)?.let { d ->
                        runCatching { ctx.sendBroadcast(Intent(Push.UP_ACK).setPackage(d).putExtra("token", Push.upToken(ctx)).putExtra("id", id)) }
                    }
                }
                Push.ping(ctx)
            }
        }
    }
}

/** Religa a conexão própria depois de reiniciar o celular ou atualizar o app. */
class PushBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) = Push.ensure(ctx)
}

/**
 * Conexão própria com o ntfy.sh: fica ouvindo o tópico secreto deste aparelho.
 * O ntfy manda um "keepalive" a cada ~45 s, então a conexão caída é percebida e refeita.
 */
class PushService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null
    @Volatile private var conn: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "Conexão para avisos", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Mantém a conexão que recebe os avisos instantâneos"
                setShowBadge(false)
            }
        )
        val open = Push.openApp?.invoke(this)?.let { PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE) }
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(Push.notifIcon)
            .setContentTitle("Avisos instantâneos ligados")
            .setContentText("Conectado ao ntfy.sh. Dá para esconder esta notificação nas configurações do Android.")
            .setOngoing(true)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)

        val endpoint = Push.endpoint(this)
        if (Push.mode(this) != Push.OWN || endpoint == null) { stopSelf(); return START_NOT_STICKY }
        if (!running) {
            running = true
            // o canal deste aparelho + os canais extras (no Companion, um por sistema acompanhado), numa conexão só
            val topics = (listOf(endpoint.substringAfterLast('/')) + (Push.extraTopics?.invoke(this) ?: emptyList())).distinct()
            worker = thread(name = "ntfy") { loop(Push.SERVER + "/" + topics.joinToString(",")) }
        }
        return START_STICKY
    }

    private fun loop(endpoint: String) {
        var wait = 5_000L
        while (running) {
            try {
                val c = URL("$endpoint/json").openConnection() as HttpURLConnection
                conn = c
                c.connectTimeout = 20_000
                c.readTimeout = 120_000 // sem nem o keepalive em 2 min = conexão caída
                c.setRequestProperty("Accept", "application/x-ndjson")
                if (c.responseCode !in 200..299) throw java.io.IOException("ntfy respondeu ${c.responseCode}")
                android.util.Log.i("PluraliPush", "conexão própria aberta")
                // ao (re)conectar, confere uma vez: pode ter mudado algo enquanto estava desconectado
                (Push.onReconnect ?: Push.onPing)?.invoke(applicationContext)
                wait = 5_000L
                c.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        val ev = runCatching { JSONObject(line).optString("event") }.getOrNull()
                        if (ev == "message") { android.util.Log.i("PluraliPush", "aviso recebido"); Push.ping(this) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.i("PluraliPush", "conexão própria caiu: " + e.javaClass.simpleName)
            } finally {
                runCatching { conn?.disconnect() }
            }
            if (!running) break
            try { Thread.sleep(wait) } catch (_: InterruptedException) { break }
            wait = (wait * 2).coerceAtMost(5 * 60_000L)
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { conn?.disconnect() }
        worker?.interrupt()
        super.onDestroy()
    }

    companion object {
        private const val CH = "push-conn"
        private const val ID = 7301
    }
}
