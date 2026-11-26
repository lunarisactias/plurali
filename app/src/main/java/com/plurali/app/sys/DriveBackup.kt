package com.plurali.app.sys

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Backup no Google Drive da própria pessoa, na pasta oculta do app ("appDataFolder"):
 * não aparece entre os arquivos dela, só o Plurali enxerga, e ocupa o espaço dela (não o do Firebase).
 * Só pede acesso a essa pasta (escopo drive.appdata), nada mais do Drive.
 */
object DriveBackup {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    const val PREFIX = "plurali-backup-"

    /** O Google precisa que a pessoa confirme (tela do próprio Google). */
    class NeedsConsent(val intent: PendingIntent) : Exception("Precisa autorizar o Google Drive")

    class DriveError(msg: String, val code: Int = 0) : IOException(msg)

    data class FileInfo(val id: String, val name: String, val size: Long, val time: Long, val members: Int, val device: String)

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Pede o token (fora da thread principal). Lança [NeedsConsent] se a pessoa ainda precisa autorizar. */
    fun token(ctx: Context): String {
        val r: AuthorizationResult = try {
            Tasks.await(Identity.getAuthorizationClient(ctx).authorize(request()), 60, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw explain(e.cause ?: e)
        }
        if (r.hasResolution()) throw NeedsConsent(r.pendingIntent ?: throw DriveError("O Google não abriu a tela de autorização."))
        return r.accessToken ?: throw DriveError("O Google não devolveu a autorização.")
    }

    /** Depois que a pessoa autorizou na tela do Google. */
    fun tokenFromResult(ctx: Context, data: Intent?): String {
        val r = try { Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data) } catch (e: Exception) { throw explain(e) }
        return r.accessToken ?: throw DriveError("Autorização cancelada.")
    }

    private fun explain(e: Throwable): Exception = when {
        e is ApiException && e.statusCode == 10 ->
            DriveError("Este Plurali ainda não está registrado no Google para usar o Drive (erro de configuração 10).")
        e is ApiException && e.statusCode == 7 -> DriveError("Sem internet.")
        e is ApiException -> DriveError("O Google recusou o acesso (código ${e.statusCode}).")
        e.message?.contains("Google Play", true) == true || e is ClassNotFoundException ->
            DriveError("Este celular não tem os serviços do Google. Use o backup em arquivo.")
        e is Exception -> e
        else -> DriveError(e.message ?: e.javaClass.simpleName)
    }

    private fun http(method: String, url: String, token: String, body: ByteArray? = null, json: Boolean = false,
                     headers: Map<String, String> = emptyMap()): Triple<Int, ByteArray, HttpURLConnection> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = 120_000
        c.setRequestProperty("Authorization", "Bearer $token")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) {
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.setRequestProperty("Content-Type", if (json) "application/json; charset=UTF-8" else "application/octet-stream")
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Triple(code, bytes, c)
    }

    private fun fail(what: String, code: Int, body: ByteArray): Nothing {
        val msg = runCatching { JSONObject(body.decodeToString()).getJSONObject("error").optString("message") }.getOrNull()
        throw DriveError("$what: " + (msg?.takeIf { it.isNotBlank() } ?: "erro $code"), code)
    }

    /** Envia um arquivo novo para a pasta oculta (envio "resumable": funciona com arquivos grandes). */
    fun upload(token: String, name: String, bytes: ByteArray, props: Map<String, String>): String {
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject(props as Map<*, *>))
        val (code, body, c) = http(
            "POST", "$UPLOAD/files?uploadType=resumable", token, meta.toString().toByteArray(), json = true,
            headers = mapOf("X-Upload-Content-Type" to "application/octet-stream", "X-Upload-Content-Length" to bytes.size.toString()),
        )
        if (code !in 200..299) fail("Envio ao Drive", code, body)
        val location = c.getHeaderField("Location") ?: throw DriveError("O Drive não respondeu para onde enviar.")
        c.disconnect()
        val (code2, body2, c2) = http("PUT", location, token, bytes)
        c2.disconnect()
        if (code2 !in 200..299) fail("Envio ao Drive", code2, body2)
        return JSONObject(body2.decodeToString()).optString("id")
    }

    /** Cópias guardadas (mais recente primeiro). */
    fun list(token: String): List<FileInfo> {
        val q = URLEncoder.encode("name contains '$PREFIX' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name,size,createdTime,appProperties)", "UTF-8")
        val (code, body, c) = http("GET", "$API/files?spaces=appDataFolder&q=$q&fields=$fields&orderBy=createdTime%20desc&pageSize=50", token)
        c.disconnect()
        if (code !in 200..299) fail("Lista do Drive", code, body)
        val a = JSONObject(body.decodeToString()).optJSONArray("files") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { f ->
            val p = f.optJSONObject("appProperties")
            FileInfo(
                f.getString("id"), f.optString("name"), f.optString("size").toLongOrNull() ?: 0L,
                p?.optString("time")?.toLongOrNull() ?: runCatching { java.time.Instant.parse(f.optString("createdTime")).toEpochMilli() }.getOrDefault(0L),
                p?.optString("members")?.toIntOrNull() ?: 0, p?.optString("device").orEmpty(),
            )
        }.sortedByDescending { it.time }
    }

    fun download(token: String, id: String): ByteArray {
        val (code, body, c) = http("GET", "$API/files/$id?alt=media", token)
        c.disconnect()
        if (code !in 200..299) fail("Download do Drive", code, body)
        return body
    }

    fun delete(token: String, id: String) {
        val (code, body, c) = http("DELETE", "$API/files/$id", token)
        c.disconnect()
        if (code !in 200..299 && code != 404) fail("Remoção no Drive", code, body)
    }
}
