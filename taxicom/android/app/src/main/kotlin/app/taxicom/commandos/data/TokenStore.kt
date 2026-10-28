package app.taxicom.commandos.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Refresh/access tokens encrypted with an AES-GCM key that never leaves the Android Keystore. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun save(session: Session) {
        prefs.edit()
            .putString("access", encrypt(session.accessToken))
            .putString("refresh", encrypt(session.refreshToken))
            .putString("user_id", session.userId)
            .putString("device_id", session.deviceId)
            .putString("email", session.email)
            .apply()
    }

    fun load(): Session? {
        val access = prefs.getString("access", null)?.let(::decrypt) ?: return null
        val refresh = prefs.getString("refresh", null)?.let(::decrypt) ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            userId = prefs.getString("user_id", null) ?: return null,
            deviceId = prefs.getString("device_id", null) ?: return null,
            email = prefs.getString("email", null).orEmpty(),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    private companion object {
        const val ALIAS = "taxicom_session"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val deviceId: String,
    val email: String,
)
