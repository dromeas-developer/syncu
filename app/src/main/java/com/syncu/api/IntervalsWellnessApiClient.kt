package com.syncu.api

import android.util.Log
import com.google.gson.Gson
import com.syncu.data.DailySummary
import com.syncu.data.IntervalsWellnessData
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
 * intervals.icu Wellness API Client
 * Handles wellness data exchange with intervals.icu
 */
class IntervalsWellnessApiClient(
    apiKey: String,
    athleteId: String
) {
    private val cleanApiKey = apiKey.trim()
    private val cleanAthleteId = athleteId.trim()

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor { message ->
            Log.d("IntervalsApi", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val credentials = Credentials.basic("API_KEY", cleanApiKey)
            val request = chain.request().newBuilder()
                .addHeader("Authorization", credentials)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "SyncU/1.0")
                .build()
            chain.proceed(request)
        }
        .build()

    private val gson = Gson()
    private val baseUrl = "https://intervals.icu/api/v1/athlete/$cleanAthleteId"

    /**
     * Get wellness data for a specific date
     */
    suspend fun getWellnessForDate(date: LocalDate): Result<IntervalsWellnessData?> = withContext(Dispatchers.IO) {
        try {
            val dateStr = date.format(DateTimeFormatter.ISO_DATE)
            val url = "$baseUrl/wellness/$dateStr"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val json = response.body?.string() ?: "{}"
                val data = gson.fromJson(json, IntervalsWellnessData::class.java)
                Result.success(data)
            } else if (response.code == 404) {
                Result.success(null)
            } else {
                Log.e("IntervalsApi", "Error: ${response.code} ${response.message}")
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("IntervalsApi", "Network error", e)
            Result.failure(e)
        }
    }

    /**
     * Upload or update wellness data to intervals.icu for a specific date
     * Uses PUT /api/v1/athlete/{id}/wellness/{date}
     */
    suspend fun uploadWellnessData(summary: DailySummary): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dateStr = summary.date.format(DateTimeFormatter.ISO_DATE)
            val wellnessData = summary.toIntervalsWellness()
            val json = gson.toJson(wellnessData)
            
            val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            // Using PUT for updates to a specific date record
            val request = Request.Builder()
                .url("$baseUrl/wellness/$dateStr")
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.i("IntervalsApi", "Upload successful for $dateStr")
                Result.success(responseBody)
            } else {
                val errorBody = response.body?.string() ?: ""
                Log.e("IntervalsApi", "Upload failed (${response.code}): $errorBody")
                Result.failure(IOException("Upload failed: ${response.code} $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("IntervalsApi", "Upload exception", e)
            Result.failure(e)
        }
    }

    /**
     * Get wellness data for a date range
     */
    suspend fun getWellnessData(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<IntervalsWellnessData>> = withContext(Dispatchers.IO) {
        try {
            val formatter = DateTimeFormatter.ISO_DATE
            val url = "$baseUrl/wellness?oldest=${startDate.format(formatter)}&newest=${endDate.format(formatter)}"

            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val json = response.body?.string() ?: "[]"
                val data = gson.fromJson(json, Array<IntervalsWellnessData>::class.java).toList()
                Result.success(data)
            } else {
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("IntervalsApi", "Range fetch error", e)
            Result.failure(e)
        }
    }
}

/**
 * Extension function to convert DailySummary to Intervals.icu format
 * Sanitizes data by replacing 0 with null for fields where 0 is invalid/unlikely
 */
fun DailySummary.toIntervalsWellness(): Map<String, Any?> {
    val formatter = DateTimeFormatter.ISO_DATE
    val effectiveSleepMinutes = this.sleep?.asleepDurationMinutes ?: this.sleep?.durationMinutes
    
    fun Int?.nullIfZero(): Int? = if (this == 0) null else this
    fun Double?.nullIfZero(): Double? = if (this == null || this == 0.0) null else this

    // We build a map instead of a data class to have full control over keys
    // and exclude fields like boneMass and leanMass that are causing 422 errors.
    return mapOf(
        "id" to this.date.format(formatter),
        "restingHR" to this.restingHR.nullIfZero(),
        "hrv" to this.hrvMs.nullIfZero(),
        "weight" to this.weightKg.nullIfZero(),
        "bodyFat" to this.bodyFatPercentage.nullIfZero(),
        "vo2max" to this.vo2Max.nullIfZero(),
        "sleepSecs" to effectiveSleepMinutes?.toInt()?.times(60).nullIfZero(),
        "avgSleepingHR" to this.sleep?.avgHeartRate?.nullIfZero()?.toDouble(),
        "spO2" to this.spo2Percentage.nullIfZero(),
        "systolic" to this.systolicBP.nullIfZero(),
        "diastolic" to this.diastolicBP.nullIfZero(),
        "bloodGlucose" to this.glucoseMmol.nullIfZero(),
        "respiration" to this.respiratoryRate.nullIfZero(),
        "kcalConsumed" to this.caloriesBurned?.toInt().nullIfZero(),
        "steps" to this.steps.nullIfZero(),
        "carbohydrates" to this.carbsGrams.nullIfZero(),
        "protein" to this.proteinGrams.nullIfZero(),
        "fatTotal" to this.fatGrams.nullIfZero()
    ).filterValues { it != null }
}
