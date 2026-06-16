package com.bigotp.app.history

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

interface CodeCipher {
    data class CipherResult(val data: String, val iv: String)
    fun encrypt(plaintext: String): CipherResult
    fun decrypt(result: CipherResult): String
}

class KeystoreCodeCipher : CodeCipher {

    private fun getOrCreateKey(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return kg.generateKey()
    }

    override fun encrypt(plaintext: String): CodeCipher.CipherResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return CodeCipher.CipherResult(
            data = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            iv   = Base64.encodeToString(cipher.iv,   Base64.NO_WRAP)
        )
    }

    override fun decrypt(result: CodeCipher.CipherResult): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(result.iv, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(Base64.decode(result.data, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS      = "bigotp_history_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
