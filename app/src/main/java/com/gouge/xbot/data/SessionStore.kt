package com.gouge.xbot.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun getAccessToken(): String? {
        val encrypted = preferences.getString(TokenKey, null) ?: return null
        val iv = preferences.getString(TokenIvKey, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(TagLengthBits, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        }.getOrElse {
            clear()
            null
        }
    }

    fun saveAccessToken(token: String) {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(TokenKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(TokenIvKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PreferencesName = "xbot_secure_session"
        const val TokenKey = "access_token_ciphertext"
        const val TokenIvKey = "access_token_iv"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "xbot_access_token_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val TagLengthBits = 128
    }
}
