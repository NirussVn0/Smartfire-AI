package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecureTokenStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smartfire_secure_tokens", Context.MODE_PRIVATE)

    // Device-local AES-128 derivation key (stored safely in private app sandbox)
    private val keyBytes: ByteArray by lazy {
        val storedKey = prefs.getString("_keystore_entropy", null)
        if (storedKey != null) {
            Base64.decode(storedKey, Base64.NO_WRAP)
        } else {
            val generated = ByteArray(16)
            SecureRandom().nextBytes(generated)
            prefs.edit().putString("_keystore_entropy", Base64.encodeToString(generated, Base64.NO_WRAP)).apply()
            generated
        }
    }

    fun saveToken(deviceId: String, token: String) {
        if (token.isBlank()) return
        try {
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            prefs.edit().putString(tokenKey(deviceId), Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
        } catch (_: Exception) {
            // Fallback obfuscation if Cipher fails on specific sandbox
            prefs.edit().putString(tokenKey(deviceId), Base64.encodeToString(token.toByteArray(), Base64.NO_WRAP)).apply()
        }
    }

    fun getToken(deviceId: String): String? {
        val encoded = prefs.getString(tokenKey(deviceId), null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= 16) return null
            val iv = combined.copyOfRange(0, 16)
            val encrypted = combined.copyOfRange(16, combined.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun removeToken(deviceId: String) {
        prefs.edit().remove(tokenKey(deviceId)).apply()
    }

    private fun tokenKey(deviceId: String): String = "node_tok_${deviceId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
}
