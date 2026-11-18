package app.danebrain.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App settings. The API key is encrypted with a key held in the Android Keystore. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var vaultPath: String
        get() = prefs.getString("vault_path", "") ?: ""
        set(v) = prefs.edit().putString("vault_path", v.trim()).apply()

    var visionModel: String
        get() = prefs.getString("vision_model", DEFAULT_VISION_MODEL) ?: DEFAULT_VISION_MODEL
        set(v) = prefs.edit().putString("vision_model", v.trim()).apply()

    var chatModel: String
        get() = prefs.getString("chat_model", DEFAULT_CHAT_MODEL) ?: DEFAULT_CHAT_MODEL
        set(v) = prefs.edit().putString("chat_model", v.trim()).apply()

    /** Unsaved text on the capture screen, kept so a killed app never loses dictation. */
    var draft: String
        get() = prefs.getString("draft", "") ?: ""
        set(v) = prefs.edit().putString("draft", v).apply()

    /** [THEME_SYSTEM], [THEME_LIGHT] or [THEME_DARK]. */
    var themeMode: String
        get() = prefs.getString("theme_mode", THEME_SYSTEM) ?: THEME_SYSTEM
        set(v) = prefs.edit().putString("theme_mode", v).apply()

    /** Notes this app has written, as [app.danebrain.core.SyncLog] text. */
    var syncLog: String
        get() = prefs.getString("sync_log", "") ?: ""
        set(v) = prefs.edit().putString("sync_log", v).apply()

    var apiKey: String
        get() {
            val stored = prefs.getString("api_key_enc", null) ?: return ""
            return try {
                decrypt(stored)
            } catch (e: Exception) {
                ""
            }
        }
        set(v) {
            val trimmed = v.trim()
            if (trimmed.isEmpty()) prefs.edit().remove("api_key_enc").apply()
            else prefs.edit().putString("api_key_enc", encrypt(trimmed)).apply()
        }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_VISION_MODEL = "anthropic/claude-haiku-5.5"
        const val DEFAULT_CHAT_MODEL = "anthropic/claude-sonnet-5.5"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val KEY_ALIAS = "danebrain_api_key"
    }
}
