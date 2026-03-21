package com.syncu.api

import android.util.Log
import com.google.gson.Gson
import com.syncu.data.DailySummary
import com.syncu.data.CoachWattsWellnessData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * CoachWatts API Client
 * Handles wellness data exchange with coachwatts.com using X-API-Key authentication
 */
class CoachWattsApiClient(private val token: String) {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val cleanToken = token.trim()
            
            val request = chain.request().newBuilder()
                .addHeader("X-API-Key", cleanToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "SyncU/1.0")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor { message ->
            Log.d("CoachWattsApi", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val gson = Gson()
    private val baseUrl = "https://coachwatts.com/api"

    /**
     * Get wellness data for a specific date
     */
    suspend fun getWellnessForDate(date: LocalDate): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)
            val url = "$baseUrl/wellness/$dateStr"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val json = response.body?.string()
                Log.d("CoachWattsApi", "GET success for $dateStr: $json")
                Result.success(json)
            } else if (response.code == 404) {
                Log.d("CoachWattsApi", "GET 404 (No data) for $dateStr")
                Result.success(null)
            } else {
                val errorMsg = response.body?.string() ?: response.message
                Log.e("CoachWattsApi", "GET failed (${response.code}) for $dateStr: $errorMsg")
                Result.failure(IOException("HTTP ${response.code}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e("CoachWattsApi", "Network error on GET", e)
            Result.failure(e)
        }
    }

    /**
     * Upload wellness data to CoachWatts
     */
    suspend fun uploadWellnessData(summary: DailySummary): Result<String> = withContext(Dispatchers.IO) {
        try {
            val wellnessData = summary.toCoachWattsWellness()
            val json = gson.toJson(wellnessData)
            
            Log.d("CoachWattsApi", "Uploading for ${summary.date}: $json")
            
            val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/wellness")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.i("CoachWattsApi", "Upload success for ${summary.date}: $responseBody")
                Result.success(responseBody)
            } else {
                val errorBody = response.body?.string() ?: ""
                Log.e("CoachWattsApi", "Upload failed (${response.code}) for ${summary.date}: $errorBody")
                Result.failure(IOException("Upload failed: ${response.code} $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("CoachWattsApi", "Upload exception", e)
            Result.failure(e)
        }
    }
}

/**
 * Extension to convert DailySummary to CoachWatts format
 * Matches the API expectation as shown in the curl example
 */
fun DailySummary.toCoachWattsWellness(): Map<String, Any?> {
    val effectiveSleepMinutes = this.sleep?.asleepDurationMinutes ?: this.sleep?.durationMinutes
    
    return mapOf(
        "date" to date.format(DateTimeFormatter.ISO_DATE),
        "weight" to weightKg,
        "bodyFat" to bodyFatPercentage,
        "restingHr" to restingHR,
        "hrv" to hrvMs,
        "spO2" to spo2Percentage,
        "systolic" to systolicBP,
        "diastolic" to diastolicBP,
        "glucose" to glucoseMmol,
        "vo2max" to vo2Max,
        "activeCaloriesBurned" to caloriesBurned?.toInt(),
        "totalCaloriesBurned" to null,
        "steps" to steps,
        "sleepSecs" to effectiveSleepMinutes?.let { it * 60 },
        "sleepHours" to effectiveSleepMinutes?.let { it / 60.0 },
        "sleepDeepSecs" to sleep?.deepSleepMinutes?.let { it * 60 },
        "sleepRemSecs" to sleep?.remSleepMinutes?.let { it * 60 },
        "sleepLightSecs" to sleep?.lightSleepMinutes?.let { it * 60 },
        "sleepAwakeSecs" to sleep?.awakeSleepMinutes?.let { it * 60 },
        "avgSleepingHr" to sleep?.avgHeartRate,
        "rawJson" to mapOf(
            "source" to "SyncU",
            "device" to "Android"
        )
    ).filterValues { it != null }
}
