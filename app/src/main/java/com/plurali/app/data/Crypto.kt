package com.plurali.app.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val ITER = 120_000
    private val rnd = SecureRandom()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun pbkdf2(password: String, salt: ByteArray, bits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITER, bits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun hash(password: String): Secret {
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        return Secret(b64(salt), b64(pbkdf2(password, salt, 256)))
    }

    fun verify(password: String, s: Secret?): Boolean {
        if (s == null) return false
        val h = pbkdf2(password, unb64(s.salt), 256)
        return MessageDigest.isEqual(h, unb64(s.hash))
    }

    fun encrypt(plain: String, password: String): Vault {
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val key = SecretKeySpec(pbkdf2(password, salt, 256), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Vault(b64(salt), b64(iv), b64(c.doFinal(plain.toByteArray())))
    }

    /** Retorna null se a senha estiver errada. */
    fun decrypt(v: Vault, password: String): String? = runCatching {
        val key = SecretKeySpec(pbkdf2(password, unb64(v.salt), 256), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(v.iv)))
        String(c.doFinal(unb64(v.data)))
    }.getOrNull()
}
