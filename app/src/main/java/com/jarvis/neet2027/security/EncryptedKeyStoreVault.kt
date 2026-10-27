package com.jarvis.neet2027.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptedKeyStoreVault {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "jarvis_neet2027_aes_master_key"
    private const val PREFS_FILE = "jarvis_encrypted_api_vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    const val KEY_GEMINI_API = "gemini_api_key"
    const val KEY_PICOVOICE_ACCESS = "picovoice_access_key"
    const val KEY_GITHUB_PAT = "github_fine_grained_pat"
    const val KEY_CLOUD_DATA_SHARING_OFF = "cloud_prompts_disabled"

    private fun getOrCreateHardwareKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveSecret(context: Context, keyName: String, plainValue: String) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (plainValue.isBlank()) {
            prefs.edit().remove(keyName).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateHardwareKey())
        val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherBytes = cipher.doFinal(plainValue.toByteArray(Charsets.UTF_8))
        val cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        prefs.edit().putString(keyName, "$ivBase64:$cipherBase64").apply()
    }

    fun readSecret(context: Context, keyName: String): String {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val packed = prefs.getString(keyName, null) ?: return ""
        val parts = packed.split(":")
        if (parts.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateHardwareKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun maskSecret(secret: String): String {
        if (secret.isBlank()) return "Not configured"
        if (secret.length <= 8) return "••••••••"
        return "${secret.take(4)}••••••••${secret.takeLast(4)}"
    }

    fun isOfflinePrivacyLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CLOUD_DATA_SHARING_OFF, false)
    }

    fun setOfflinePrivacyLock(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CLOUD_DATA_SHARING_OFF, enabled)
            .apply()
    }
}
