package com.nexo.app.core.lock

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 secret records: "pbkdf2-sha256$iterations$salt$key" (LU BR-4). */
object PinHasher {
    private const val ALGORITHM = "pbkdf2-sha256"
    const val ITERATIONS = 100_000

    fun hash(secret: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = derive(secret, salt, iterations)
        return listOf(ALGORITHM, iterations.toString(), b64(salt), b64(key)).joinToString("$")
    }

    fun verify(secret: String, record: String): Boolean {
        val parts = record.split('$')
        if (parts.size != 4 || parts[0] != ALGORITHM) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = Base64.decode(parts[2], Base64.NO_WRAP)
        val expected = Base64.decode(parts[3], Base64.NO_WRAP)
        return MessageDigest.isEqual(derive(secret, salt, iterations), expected)
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = secret.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
