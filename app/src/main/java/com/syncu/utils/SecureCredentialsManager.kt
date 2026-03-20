package com.syncu.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure Credentials Manager
 * Stores API credentials in encrypted storage
 */
class SecureCredentialsManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "syncu_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_API_KEY = "intervals_api_key"
        private const val KEY_ATHLETE_ID = "intervals_athlete_id"
        private const val KEY_COACHWATTS_TOKEN = "coachwatts_token"
    }

    /**
     * Save intervals.icu credentials
     */
    fun saveCredentials(apiKey: String, athleteId: String): Boolean {
        return try {
            val success = sharedPreferences.edit().apply {
                putString(KEY_API_KEY, apiKey)
                putString(KEY_ATHLETE_ID, athleteId)
            }.commit()

            Log.d("CredentialsManager", "Save Intervals credentials result: $success")
            success
        } catch (e: Exception) {
            Log.e("CredentialsManager", "Error saving Intervals credentials", e)
            false
        }
    }

    /**
     * Save CoachWatts credentials
     */
    fun saveCoachWattsCredentials(token: String): Boolean {
        return try {
            val success = sharedPreferences.edit().apply {
                putString(KEY_COACHWATTS_TOKEN, token)
            }.commit()

            Log.d("CredentialsManager", "Save CoachWatts credentials result: $success")
            success
        } catch (e: Exception) {
            Log.e("CredentialsManager", "Error saving CoachWatts credentials", e)
            false
        }
    }

    /**
     * Get intervals.icu API key
     */
    fun getApiKey(): String? = sharedPreferences.getString(KEY_API_KEY, null)

    /**
     * Get intervals.icu Athlete ID
     */
    fun getAthleteId(): String? = sharedPreferences.getString(KEY_ATHLETE_ID, null)

    /**
     * Get CoachWatts Token
     */
    fun getCoachWattsToken(): String? = sharedPreferences.getString(KEY_COACHWATTS_TOKEN, null)

    /**
     * Check if intervals.icu credentials are saved
     */
    fun hasIntervalsCredentials(): Boolean {
        return !getApiKey().isNullOrEmpty() && !getAthleteId().isNullOrEmpty()
    }

    /**
     * Check if CoachWatts credentials are saved
     */
    fun hasCoachWattsCredentials(): Boolean {
        return !getCoachWattsToken().isNullOrEmpty()
    }

    /**
     * Check if at least one service is configured
     */
    fun hasCredentials(): Boolean = hasIntervalsCredentials() || hasCoachWattsCredentials()

    /**
     * Clear all credentials
     */
    fun clearCredentials() {
        sharedPreferences.edit().clear().commit()
        Log.d("CredentialsManager", "All credentials cleared")
    }
}
