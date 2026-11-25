package com.plurali.app.sys

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.plurali.app.data.Prefs
import com.plurali.app.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Backup automático: grava um arquivo na pasta escolhida e mantém só os N mais recentes. */
object AutoBackup {
    private const val WORK = "auto-backup"
    const val PREFIX = "plurali-auto-"

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (Prefs.backupFreq == "off" || Prefs.backupUri == null) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val days = if (Prefs.backupFreq == "daily") 1L else 7L
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.UPDATE,
            // o primeiro backup é feito na hora pela tela; o automático começa depois de um período
            PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS).setInitialDelay(days, TimeUnit.DAYS).build(),
        )
    }

    /** Faz o backup agora. Retorna null se deu certo, ou a mensagem de erro. */
    fun runNow(ctx: Context): String? {
        val now = System.currentTimeMillis()
        val err = try {
            val uri = Prefs.backupUri ?: error("Nenhuma pasta escolhida")
            val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(uri)) ?: error("Pasta não encontrada")
            if (!tree.canWrite()) error("Sem permissão para gravar na pasta. Escolha a pasta de novo.")
            val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(now)) + ".json"
            val f = tree.createFile("application/json", name) ?: error("Não consegui criar o arquivo")
            ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { it.write(Store.exportJson().toByteArray()) }
            // apaga os automáticos mais antigos
            tree.listFiles().filter { it.name?.startsWith(PREFIX) == true }
                .sortedByDescending { it.name }
                .drop(Prefs.backupKeep.coerceAtLeast(1))
                .forEach { runCatching { it.delete() } }
            null
        } catch (e: Exception) {
            e.message ?: "Erro desconhecido"
        }
        Prefs.setBackupResult(now, err)
        return err
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        PluraliApplication.ensure(applicationContext)
        return if (AutoBackup.runNow(applicationContext) == null) Result.success() else Result.retry()
    }
}
