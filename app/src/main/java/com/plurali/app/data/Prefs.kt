package com.plurali.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Preferências deste aparelho (não vão para o backup): aparência, notificações e backup automático.
 * Os campos são estado do Compose, então a tela atualiza na hora.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    var theme by mutableStateOf("dark")            // dark | light | system
        private set
    var accent by mutableLongStateOf(0xFFD6AC5E)
        private set
    var textScale by mutableFloatStateOf(1f)
        private set
    var frontNotif by mutableStateOf(false)
        private set
    var msgNotif by mutableStateOf(true)
        private set
    var pollNotif by mutableStateOf(true)
        private set
    var birthdayNotif by mutableStateOf(true)
        private set
    var backupUri by mutableStateOf<String?>(null)
        private set
    var backupFreq by mutableStateOf("off")        // off | daily | weekly
        private set
    var backupKeep by mutableIntStateOf(10)
        private set
    var lastBackup by mutableLongStateOf(0L)
        private set
    var lastBackupError by mutableStateOf<String?>(null)
        private set

    /** Último momento em que os lembretes foram conferidos. */
    var lastAlarmCheck: Long
        get() = sp.getLong("lastAlarmCheck", 0L)
        set(v) = sp.edit().putLong("lastAlarmCheck", v).apply()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("plurali", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "dark") ?: "dark"
        accent = sp.getLong("accent", 0xFFD6AC5E)
        textScale = sp.getFloat("textScale", 1f)
        frontNotif = sp.getBoolean("frontNotif", false)
        msgNotif = sp.getBoolean("msgNotif", true)
        pollNotif = sp.getBoolean("pollNotif", true)
        birthdayNotif = sp.getBoolean("birthdayNotif", true)
        backupUri = sp.getString("backupUri", null)
        backupFreq = sp.getString("backupFreq", "off") ?: "off"
        backupKeep = sp.getInt("backupKeep", 10)
        lastBackup = sp.getLong("lastBackup", 0L)
        lastBackupError = sp.getString("lastBackupError", null)
        if (lastAlarmCheck == 0L) lastAlarmCheck = System.currentTimeMillis()
        loadCloudBackup()
    }

    fun putTheme(v: String) { theme = v; sp.edit().putString("theme", v).apply() }
    fun putAccent(v: Long) { accent = v; sp.edit().putLong("accent", v).apply() }
    fun putTextScale(v: Float) { textScale = v; sp.edit().putFloat("textScale", v).apply() }
    fun putFrontNotif(v: Boolean) { frontNotif = v; sp.edit().putBoolean("frontNotif", v).apply() }
    fun putMsgNotif(v: Boolean) { msgNotif = v; sp.edit().putBoolean("msgNotif", v).apply() }
    fun putPollNotif(v: Boolean) { pollNotif = v; sp.edit().putBoolean("pollNotif", v).apply() }
    fun putBirthdayNotif(v: Boolean) { birthdayNotif = v; sp.edit().putBoolean("birthdayNotif", v).apply() }
    fun putBackupUri(v: String?) { backupUri = v; sp.edit().putString("backupUri", v).apply() }
    fun putBackupFreq(v: String) { backupFreq = v; sp.edit().putString("backupFreq", v).apply() }
    fun putBackupKeep(v: Int) { backupKeep = v; sp.edit().putInt("backupKeep", v).apply() }
    // ---------- companions ----------
    var companionError by mutableStateOf<String?>(null)
        private set
    var companionTick by mutableLongStateOf(0L) // muda a cada envio, para a tela atualizar
        private set

    fun companionHash(id: String): String? = sp.getString("ch_$id", null)
    fun companionSynced(id: String): Long = sp.getLong("cs_$id", 0L)
    fun setCompanionSynced(id: String, hash: String, time: Long) {
        sp.edit().putString("ch_$id", hash).putLong("cs_$id", time).apply()
        companionTick = time
    }
    fun forgetCompanionHash(id: String) { sp.edit().remove("ch_$id").apply() }
    fun putCompanionError(e: String?) { companionError = e; companionTick = System.currentTimeMillis() }
    fun pendingDeletes(): Set<String> = sp.getStringSet("cdel", emptySet()) ?: emptySet()
    fun addPendingDelete(shareId: String) { sp.edit().putStringSet("cdel", pendingDeletes() + shareId).apply() }
    fun removePendingDelete(shareId: String) { sp.edit().putStringSet("cdel", pendingDeletes() - shareId).apply() }

    /** Fotos já enviadas ao Firebase (ids de shares/m-<id>), para não reenviar e apagar as que saíram de uso. */
    fun mediaUploaded(): Set<String> = sp.getStringSet("media", emptySet()) ?: emptySet()
    fun setMediaUploaded(s: Set<String>) { sp.edit().putStringSet("media", s.toSet()).apply() }
    /** Para qual Firebase/sessão as fotos foram enviadas (se mudar, reenvia tudo). */
    fun mediaDest(): String? = sp.getString("mediaDest", null)
    fun setMediaDest(s: String) { sp.edit().putString("mediaDest", s).apply() }

    /** Endereço ntfy de cada companion (vem do próprio companion, pelo Firebase): para avisar na hora que algo mudou. */
    fun companionPush(id: String): String? = sp.getString("cp_$id", null)
    fun setCompanionPush(id: String, e: String?) { sp.edit().apply { if (e == null) remove("cp_$id") else putString("cp_$id", e) }.apply() }

    // ---------- backup na nuvem ----------
    var lastCloudBackup by mutableLongStateOf(0L)
        private set
    var cloudBackupError by mutableStateOf<String?>(null)
        private set
    var cloudBackupSize by mutableIntStateOf(0)
        private set
    val cloudBackupHash: String? get() = sp.getString("cbHash", null)

    fun loadCloudBackup() {
        lastCloudBackup = sp.getLong("cbTime", 0L)
        cloudBackupError = sp.getString("cbErr", null)
        cloudBackupSize = sp.getInt("cbSize", 0)
    }

    fun setCloudBackupResult(time: Long, error: String?, size: Int, hash: String?) {
        if (error == null) { lastCloudBackup = time; cloudBackupSize = size }
        cloudBackupError = error
        sp.edit().putLong("cbTime", lastCloudBackup).putString("cbErr", error).putInt("cbSize", cloudBackupSize)
            .apply { if (error == null) putString("cbHash", hash) }.apply()
    }

    fun forgetCloudBackup() {
        lastCloudBackup = 0L; cloudBackupError = null; cloudBackupSize = 0
        sp.edit().remove("cbTime").remove("cbErr").remove("cbSize").remove("cbHash").apply()
    }

    fun setBackupResult(time: Long, error: String?) {
        if (error == null) lastBackup = time
        lastBackupError = error
        sp.edit().putLong("lastBackup", lastBackup).putString("lastBackupError", error).apply()
    }
}
