package com.cinema.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cinema.data.api.ApiKeys

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

    fun get(context: Context): String =
        preferences(context)
            .getString(KEY_SUBDL, ApiKeys.SUBDL)
            .orEmpty()
            .ifBlank { ApiKeys.SUBDL }

    fun save(context: Context, key: String) {
        preferences(context)
            .edit()
            .putString(KEY_SUBDL, key.trim())
            .apply()
    }
}
