package com.nexo.app.core.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Biometric unlock backed by a keystore key that the platform invalidates when enrolments change. */
object BiometricHelper {
    private const val KEY_ALIAS = "nexo_biometric_unlock"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun createKey() {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        generator.generateKey()
    }

    fun deleteKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    sealed interface CipherResult {
        data class Ready(val cipher: Cipher) : CipherResult
        data object Invalidated : CipherResult
        data object Missing : CipherResult
    }

    fun cipher(): CipherResult {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return CipherResult.Missing
        return try {
            CipherResult.Ready(Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) })
        } catch (_: KeyPermanentlyInvalidatedException) {
            CipherResult.Invalidated
        } catch (_: Exception) {
            CipherResult.Invalidated
        }
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        negative: String,
        cipher: Cipher,
        onSuccess: () -> Unit,
        onError: (code: Int) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
