package com.plurali.shared

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografia de ponta a ponta do que vai para o companion:
 * o Firebase só guarda bytes embaralhados; a chave só existe no Plurali e no convite (QR).
 */
object ShareCrypto {
    private val rnd = SecureRandom()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    /** Chave AES-256 aleatória (base64). */
    fun newKey(): String = b64(ByteArray(32).also { rnd.nextBytes(it) })

    /** Identificador impossível de adivinhar para o caminho no banco. */
    fun newShareId(): String = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Comprime + criptografa (AES-GCM). Saída: base64(iv + texto cifrado). */
    fun seal(plain: String, keyB64: String): String {
        val gz = ByteArrayOutputStream().also { bo -> GZIPOutputStream(bo).use { it.write(plain.toByteArray()) } }.toByteArray()
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(unb64(keyB64), "AES"), GCMParameterSpec(128, iv))
        return b64(iv + c.doFinal(gz))
    }

    /** Retorna null se a chave estiver errada ou os dados corrompidos. */
    fun open(sealed: String, keyB64: String): String? = runCatching {
        val all = unb64(sealed)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(unb64(keyB64), "AES"), GCMParameterSpec(128, all, 0, 12))
        val gz = c.doFinal(all, 12, all.size - 12)
        GZIPInputStream(ByteArrayInputStream(gz)).use { it.readBytes().decodeToString() }
    }.getOrNull()
}
