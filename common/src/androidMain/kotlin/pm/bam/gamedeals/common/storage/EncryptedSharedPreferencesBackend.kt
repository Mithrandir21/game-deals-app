package pm.bam.gamedeals.common.storage

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.WorkerThread
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A [KeyValueBackend] that encrypts each value with an Android Keystore-wrapped AES/GCM key before
 * writing it to [prefs]. The secure store for secrets like the ITAD auth token (#239) — chosen over the
 * deprecated `androidx.security:security-crypto` EncryptedSharedPreferences.
 *
 * Keys are stored in plaintext; only values are encrypted. The Keystore key never leaves the TEE/StrongBox,
 * so the persisted blob is useless off-device. A value that can't be decrypted (e.g. the key was
 * invalidated by a device-credential reset) reads as null — i.e. "absent" — which for the refreshable
 * auth token simply means a re-login.
 *
 * Stored form per value: Base64( 12-byte GCM IV ‖ ciphertext+tag ).
 */
internal class EncryptedSharedPreferencesBackend(
    private val prefs: SharedPreferences,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : KeyValueBackend {

    override fun readString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
            Cipher.getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)) }
                .doFinal(cipherText)
                .decodeToString()
        } catch (t: Throwable) {
            // Undecryptable (key rotated/invalidated, corrupt blob): treat as absent.
            null
        }
    }

    @WorkerThread
    override fun writeString(key: String, value: String): Boolean {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val packed = cipher.iv + cipher.doFinal(value.encodeToByteArray())
        return prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    @WorkerThread
    override fun remove(key: String): Boolean = prefs.edit().remove(key).commit()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "gamedeals_secure_storage_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
