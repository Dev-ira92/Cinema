package com.cinema.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SubtitleKeyStore {

    private const val FILE_NAME = "cinema_secure_settings"
    private const val KEY_SUBDL = "subdl_api_key"

    private fun preferences(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun get(context: Context): String {
        return try {
            preferences(context)
                .getString(KEY_SUBDL, null)
                ?.trim()
                .orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun save(context: Context, key: String) {
        require(key.isNotBlank()) {
            "SubDL API key cannot be empty."
        }

        preferences(context)
            .edit()
            .putString(KEY_SUBDL, key.trim())
            .apply()
    }

    fun clear(context: Context) {
        preferences(context)
            .edit()
            .remove(KEY_SUBDL)
            .apply()
    }

    fun hasKey(context: Context): Boolean {
        return get(context).isNotBlank()
    }
}
