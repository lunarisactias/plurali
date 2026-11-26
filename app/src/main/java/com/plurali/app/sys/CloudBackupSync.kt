package com.plurali.app.sys

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plurali.app.data.*
import com.plurali.shared.FirebaseError
import com.plurali.shared.ShareCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Backup automático no Firebase, criptografado de ponta a ponta.
 *
 * Tudo sai de um único código de 20 letras (que só o sistema tem):
 *  - o caminho no banco  = shares/bk-<hash do código>  (as mesmas regras dos companions: só o dono grava)
 *  - a chave AES-256     = outro hash do código
 * O Google só guarda bytes embaralhados. Sem o código ninguém acha nem abre o backup.
 *
 * Guarda duas cópias alternadas (a / b): se algo der errado no meio de um envio, a anterior continua inteira.
 */
object CloudBackupSync {
    private const val WORK = "cloud-backup"
    private const val PART = 4_000_000 // cada texto no Realtime Database pode ter até 10 MB
    private const val ALPH = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sem 0/O e 1/I, para não confundir ao digitar
    private val rnd = SecureRandom()

    data class Slot(val id: String, val time: Long, val size: Int, val members: Int, val device: String)

    fun newCode(): String = (1..20).map { ALPH[rnd.nextInt(ALPH.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
    fun normalize(code: String): String = code.uppercase().filter { it in ALPH }
    fun isValid(code: String) = normalize(code).length == 20
    fun pretty(code: String) = normalize(code).chunked(4).joinToString("-")

    private fun pathOf(code: String) = "shares/bk-" + ShareCrypto.sha256("plurali-backup-id|" + normalize(code)).take(32)
    private fun keyOf(code: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(("plurali-backup-key|" + normalize(code)).toByteArray()), Base64.NO_WRAP,
    )

    private fun serverTime() = JSONObject().put(".sv", "timestamp").toString()

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val b = Store.data.value.cloudBackup
        if (b == null || !b.enabled) { wm.cancelUniqueWork(WORK); return }
        val hours = when (b.freq) { "1h" -> 1L; "24h" -> 24L; else -> 6L }
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudBackupWorker>(hours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (b.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Envia o backup. Sem mudanças desde o último envio, não manda nada (a não ser com force). null = deu certo. */
    @Synchronized
    fun runNow(ctx: Context, force: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val d = Store.data.value
        val b = d.cloudBackup ?: return "Backup na nuvem desligado"
        val cloud = d.cloud ?: return "Firebase não configurado"
        return try {
            val text = Store.exportJson(compact = true)
            val hash = ShareCrypto.sha256(text + b.code)
            if (!force && hash == Prefs.cloudBackupHash) {
                Prefs.setCloudBackupResult(now, null, Prefs.cloudBackupSize, hash)
                return null
            }
            if (b.target == "drive") {
                val size = uploadDrive(ctx, DriveBackup.token(ctx), b.code, text, now)
                Prefs.setCloudBackupResult(now, null, size, hash)
                return null
            }
            val fb = CompanionSync.client(cloud)
            fb.token()
            val uid = fb.uid ?: error("sem sessão")
            val sealed = ShareCrypto.seal(text, keyOf(b.code))
            val path = pathOf(b.code)
            val latest = fb.get("$path/latest")?.trim('"')
            val slot = if (latest == "a") "b" else "a"
            val meta = JSONObject().put("time", now).put("size", sealed.length).put("members", d.members.size)
                .put("device", Build.MANUFACTURER + " " + Build.MODEL)
            val data = JSONObject().put("parts", JSONArray(sealed.chunked(PART)))
            if (latest == null && fb.get("$path/owner") == null) {
                // primeira vez: cria tudo de uma vez (as regras exigem o campo owner)
                fb.put(
                    path,
                    JSONObject().put("owner", uid).put("v", 1).put("kind", "backup").put("latest", slot)
                        .put("updated", JSONObject().put(".sv", "timestamp"))
                        .put("meta", JSONObject().put(slot, meta)).put("slots", JSONObject().put(slot, data)).toString(),
                )
            } else {
                fb.put("$path/slots/$slot", data.toString()) // primeiro os dados...
                fb.put("$path/meta/$slot", meta.toString())  // ...depois a descrição...
                fb.put("$path/latest", JSONObject.quote(slot)) // ...e só então passa a ser a mais recente
                fb.put("$path/updated", serverTime())
            }
            Prefs.setCloudBackupResult(now, null, sealed.length, hash)
            null
        } catch (e: Throwable) {
            val msg = when {
                e is DriveBackup.NeedsConsent -> "Toque em “Enviar backup agora” para autorizar o Google Drive de novo."
                e is FirebaseError && (e.code == 401 || e.code == 403) ->
                    "O Firebase recusou o envio. Se este código veio de outro aparelho, gere um código novo."
                e is OutOfMemoryError -> "Backup grande demais para a memória do aparelho"
                else -> e.message ?: e.javaClass.simpleName
            }
            Prefs.setCloudBackupResult(now, msg, Prefs.cloudBackupSize, null)
            msg
        }
    }

    /** Lista as cópias guardadas para este código (mais recente primeiro). */
    fun slots(code: String): List<Slot> {
        val cloud = Store.data.value.cloud ?: error("Firebase não configurado")
        val fb = CompanionSync.client(cloud)
        val raw = fb.get(pathOf(code) + "/meta") ?: return emptyList()
        val o = JSONObject(raw)
        return o.keys().asSequence().map { k ->
            val m = o.getJSONObject(k)
            Slot(k, m.optLong("time"), m.optInt("size"), m.optInt("members"), m.optString("device"))
        }.sortedByDescending { it.time }.toList()
    }

    /** Baixa, abre e devolve o texto do backup (sem aplicar). */
    fun download(code: String, slot: String): String {
        val cloud = Store.data.value.cloud ?: error("Firebase não configurado")
        val fb = CompanionSync.client(cloud)
        val raw = fb.get(pathOf(code) + "/slots/$slot/parts") ?: error("Cópia não encontrada")
        val a = JSONArray(raw)
        val sealed = buildString { for (i in 0 until a.length()) append(a.getString(i)) }
        return ShareCrypto.open(sealed, keyOf(code)) ?: error("Não consegui abrir: o código está errado?")
    }

    /** Aplica um backup baixado: substitui todos os dados deste aparelho. */
    fun apply(ctx: Context, text: String) {
        Store.importJson(text)
        CompanionSync.resetClient() // o backup traz a sessão anônima original (o mesmo "dono" no Firebase)
        Prefs.forgetCloudBackup()
        PluraliApplication.ensure(ctx)
        schedule(ctx)
    }

    // ---------------- Google Drive ----------------

    private val MAGIC = "PLB1".toByteArray()

    /** Envia uma cópia ao Drive e deixa só as 2 mais recentes. Retorna o tamanho enviado. */
    fun uploadDrive(ctx: Context, token: String, code: String, text: String, now: Long = System.currentTimeMillis()): Int {
        val d = Store.data.value
        val bytes = MAGIC + ShareCrypto.sealRaw(ShareCrypto.gzip(text.toByteArray()), keyOf(code))
        val props = mapOf(
            "time" to now.toString(), "members" to d.members.size.toString(),
            "device" to (Build.MANUFACTURER + " " + Build.MODEL).take(60),
            "code" to ShareCrypto.sha256("plurali-backup-check|" + normalize(code)).take(12), // para achar as cópias deste código
        )
        DriveBackup.upload(token, DriveBackup.PREFIX + now + ".plb", bytes, props)
        runCatching { DriveBackup.list(token).drop(2).forEach { DriveBackup.delete(token, it.id) } }
        return bytes.size
    }

    fun driveFiles(token: String) = DriveBackup.list(token)

    /** Baixa e abre uma cópia do Drive (sem aplicar). */
    fun downloadDrive(token: String, id: String, code: String): String {
        val all = DriveBackup.download(token, id)
        if (all.size < MAGIC.size || !all.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) error("Arquivo de backup desconhecido")
        val gz = runCatching { ShareCrypto.openRaw(all.copyOfRange(MAGIC.size, all.size), keyOf(code)) }.getOrNull()
            ?: error("Não consegui abrir: o código está errado?")
        return ShareCrypto.gunzip(gz).decodeToString()
    }

    /** Apaga todas as cópias do Drive (ao desligar ou trocar de código). */
    fun deleteDrive(ctx: Context) {
        runCatching {
            val t = DriveBackup.token(ctx)
            DriveBackup.list(t).forEach { DriveBackup.delete(t, it.id) }
        }
    }

    /** Apaga as cópias deste código do Firebase (ao trocar de código ou desligar). */
    fun deleteRemote(code: String) {
        val cloud = Store.data.value.cloud ?: return
        runCatching { CompanionSync.client(cloud).delete(pathOf(code)) }
    }
}

class CloudBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PluraliApplication.ensure(applicationContext)
        return if (CloudBackupSync.runNow(applicationContext) == null) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
