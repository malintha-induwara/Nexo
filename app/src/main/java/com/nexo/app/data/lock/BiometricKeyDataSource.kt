package com.nexo.app.data.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// The keystore key behind biometric unlock; enrolment changes invalidate it.
class BiometricKeyDataSource @Inject constructor(
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun createKey() {
        withContext(ioDispatcher) { generateKey() }
    }

    private fun generateKey() {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        generator.generateKey()
    }

    suspend fun deleteKey() {
        withContext(ioDispatcher) {
            runCatching {
                KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            }
        }
    }

    // Null when the key is missing or no longer valid.
    suspend fun cipher(): Cipher? = withContext(ioDispatcher) {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return@withContext null
        try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nexo_biometric_unlock"
        const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    }
}
