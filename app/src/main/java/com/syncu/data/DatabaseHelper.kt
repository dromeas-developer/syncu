package com.syncu.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import com.syncu.api.HealthConnectManager
import com.syncu.api.ExtendedHealthConnectManager
import com.syncu.api.IntervalsWellnessApiClient
import com.syncu.api.CoachWattsApiClient
import com.syncu.utils.PreferencesManager
import com.syncu.utils.SecureCredentialsManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * Database Helper with manual edit protection, data provenance, and multi-service caching
 */
class DatabaseHelper(
    private val context: Context,
    private val database: AppDatabase,
    private val healthManager: HealthConnectManager,
    private val extendedHealthManager: ExtendedHealthConnectManager
) {

    private val preferencesManager = PreferencesManager(context)

    /**
     * Force a full refresh of all data for a specific date from APIs
     */
    suspend fun loadDataForDate(date: LocalDate) {
        try {
            purgeOldCache()
            
            // 1. Force refresh Sleep first (needed for custom resting HR calculation)
            val sleep = healthManager.getSleepForDate(date)
            sleep?.let { database.sleepDao().insertSleep(it) }
            
            // 2. Force refresh Wellness (HC) - Pass "true" sleep boundaries
            val sleepWindow = getAsleepWindow(sleep)
            refreshWellnessCache(date, sleepWindow.first, sleepWindow.second)
            
            // 3. Force refresh Services
            refreshIntervalsCache(date)
            refreshCoachWattsCache(date)
            
        } catch (e: Exception) {
            Log.e("SyncU_Data", "Error force loading data for $date", e)
        }
    }

    /**
     * Extracts the "true" asleep window from a sleep session.
     */
    private fun getAsleepWindow(sleep: SleepSession?): Pair<Instant?, Instant?> {
        if (sleep == null) return Pair(null, null)
        
        val intervals = sleep.stageIntervals?.split(";") ?: return Pair(sleep.startTime, sleep.endTime)
        val sleepStageIds = listOf("2", "4", "5", "6")
        
        val sleepIntervals = intervals.mapNotNull { interval ->
            val parts = interval.split(",")
            if (parts.size == 3 && parts[2] in sleepStageIds) {
                try {
                    Triple(Instant.ofEpochMilli(parts[0].toLong()), Instant.ofEpochMilli(parts[1].toLong()), parts[2])
                } catch (e: Exception) { null }
            } else null
        }

        if (sleepIntervals.isEmpty()) return Pair(sleep.startTime, sleep.endTime)

        val start = sleepIntervals.first().first.truncatedTo(ChronoUnit.MINUTES)
        val end = sleepIntervals.last().second.truncatedTo(ChronoUnit.MINUTES)
        
        return Pair(start, end)
    }

    private suspend fun purgeOldCache() {
        try {
            val retentionDays = preferencesManager.cacheRetentionDays
            val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong())
            database.wellnessDao().deleteWellnessBefore(cutoffDate)
            database.intervalsDao().deleteWellnessBefore(cutoffDate)
            database.coachWattsDao().deleteWellnessBefore(cutoffDate)
        } catch (e: Exception) {
            Log.e("SyncU_Data", "Error purging cache", e)
        }
    }

    private suspend fun refreshWellnessCache(date: LocalDate, sleepStart: Instant? = null, sleepEnd: Instant? = null) {
        val wellness = extendedHealthManager.getWellnessSummaryForDay(date, sleepStart, sleepEnd)
        val steps = healthManager.getStepsForDate(date)
        val totalCalories = healthManager.getActiveCaloriesForDate(date)
        val activeMinutes = healthManager.getActiveMinutesForDate(date)

        val record = DailyWellnessRecord(
            date = date,
            steps = steps,
            caloriesBurned = totalCalories,
            activeMinutes = activeMinutes,
            restingHR = wellness?.restingHR,
            hrvMs = wellness?.hrvMs,
            weightKg = wellness?.weightKg,
            bodyFatPercentage = wellness?.bodyFatPercentage,
            spo2Percentage = wellness?.spo2Percentage,
            glucoseMmol = wellness?.glucoseMmol,
            systolicBP = wellness?.systolicBP,
            diastolicBP = wellness?.diastolicBP,
            vo2Max = wellness?.vo2Max,
            respiratoryRate = wellness?.respiratoryRate,
            lastUpdated = Instant.now()
        )
        database.wellnessDao().insertWellness(record)
    }

    suspend fun refreshIntervalsCache(date: LocalDate): IntervalsWellnessData? {
        val credentialsManager = SecureCredentialsManager(context)
        val apiKey = credentialsManager.getApiKey()
        val athleteId = credentialsManager.getAthleteId()

        if (apiKey != null && athleteId != null) {
            val intervalsClient = IntervalsWellnessApiClient(apiKey, athleteId)
            val result = intervalsClient.getWellnessForDate(date)
            if (result.isSuccess) {
                val data = result.getOrNull()
                if (data != null) {
                    val existing = database.intervalsDao().getWellnessForDate(date)
                    val record = IntervalsWellnessRecord(
                        date = date,
                        weight = data.weight,
                        restingHR = data.restingHR,
                        hrv = data.hrv,
                        kcalConsumed = data.kcalConsumed,
                        sleepSecs = data.sleepSecs,
                        avgSleepingHR = data.avgSleepingHR,
                        spO2 = data.spO2,
                        systolic = data.systolic,
                        diastolic = data.diastolic,
                        bloodGlucose = data.bloodGlucose,
                        bodyFat = data.bodyFat,
                        vo2max = data.vo2max,
                        steps = data.steps,
                        respiration = data.respiration,
                        carbohydrates = data.carbohydrates,
                        protein = data.protein,
                        fatTotal = data.fatTotal,
                        lastUpdated = Instant.now(),
                        lastSyncedAt = existing?.lastSyncedAt
                    )
                    database.intervalsDao().insertWellness(record)
                    data.lastSyncedAt = existing?.lastSyncedAt
                }
                return data
            }
        }
        return null
    }

    suspend fun refreshCoachWattsCache(date: LocalDate): CoachWattsWellnessRecord? {
        val credentialsManager = SecureCredentialsManager(context)
        val token = credentialsManager.getCoachWattsToken()

        if (token != null) {
            val client = CoachWattsApiClient(token)
            val result = client.getWellnessForDate(date)
            if (result.isSuccess) {
                val json = result.getOrNull()
                if (json != null) {
                    try {
                        val gson = com.google.gson.Gson()
                        val data = gson.fromJson(json, CoachWattsWellnessData::class.java)
                        val existing = database.coachWattsDao().getWellnessForDate(date)
                        val record = CoachWattsWellnessRecord(
                            date = date,
                            weight = data.weight,
                            restingHR = data.resting_hr,
                            hrv = data.hrv,
                            sleepMinutes = data.sleep_minutes,
                            avgSleepingHR = data.avg_sleeping_hr,
                            spO2 = data.spo2,
                            systolic = data.systolic,
                            diastolic = data.diastolic,
                            glucose = data.glucose,
                            bodyFat = data.body_fat,
                            vo2max = data.vo2max,
                            steps = data.steps,
                            respiration = data.respiration,
                            calories = data.calories,
                            totalCalories = data.total_calories,
                            carbs = data.carbs,
                            protein = data.protein,
                            fat = data.fat,
                            lastUpdated = Instant.now(),
                            lastSyncedAt = existing?.lastSyncedAt
                        )
                        database.coachWattsDao().insertWellness(record)
                        return record
                    } catch (e: Exception) {
                        Log.e("SyncU_Data", "Error parsing CoachWatts data", e)
                    }
                }
            }
        }
        return null
    }

    suspend fun getDailySummary(date: LocalDate): DailySummary {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val isToday = date == LocalDate.now()
        
        val permissions = healthManager.getGrantedPermissions()
        fun has(recordType: KClass<out androidx.health.connect.client.records.Record>) = 
            permissions.contains(HealthPermission.getReadPermission(recordType))

        val sleepList = database.sleepDao().getSleepForDateRange(startOfDay.toEpochMilli(), endOfDay.toEpochMilli())
        var sleep = sleepList.firstOrNull()
        if (sleep == null || isToday) {
            val fetchedSleep = healthManager.getSleepForDate(date)
            fetchedSleep?.let {
                database.sleepDao().insertSleep(it)
                sleep = it
            }
        }
        
        if (!has(androidx.health.connect.client.records.SleepSessionRecord::class)) sleep = null

        var wellnessRecord = database.wellnessDao().getWellnessForDate(date)
        val shouldRefreshWellness = wellnessRecord == null || 
                (isToday && (wellnessRecord.lastUpdated == null || ChronoUnit.MINUTES.between(wellnessRecord.lastUpdated, Instant.now()) > 5)) ||
                (wellnessRecord.lastUpdated != null && ChronoUnit.HOURS.between(wellnessRecord.lastUpdated, Instant.now()) > 24)

        if (shouldRefreshWellness) {
            val sleepWindow = getAsleepWindow(sleep)
            refreshWellnessCache(date, sleepWindow.first, sleepWindow.second)
            wellnessRecord = database.wellnessDao().getWellnessForDate(date)
        }

        wellnessRecord = wellnessRecord?.let {
            it.copy(
                steps = if (has(androidx.health.connect.client.records.StepsRecord::class)) it.steps else null,
                caloriesBurned = if (has(androidx.health.connect.client.records.ActiveCaloriesBurnedRecord::class)) it.caloriesBurned else null,
                restingHR = if (has(androidx.health.connect.client.records.RestingHeartRateRecord::class) || has(androidx.health.connect.client.records.HeartRateRecord::class)) it.restingHR else null,
                hrvMs = if (has(androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord::class)) it.hrvMs else null,
                weightKg = if (has(androidx.health.connect.client.records.WeightRecord::class)) it.weightKg else null,
                bodyFatPercentage = if (has(androidx.health.connect.client.records.BodyFatRecord::class)) it.bodyFatPercentage else null,
                spo2Percentage = if (has(androidx.health.connect.client.records.OxygenSaturationRecord::class)) it.spo2Percentage else null,
                glucoseMmol = if (has(androidx.health.connect.client.records.BloodGlucoseRecord::class)) it.glucoseMmol else null,
                systolicBP = if (has(androidx.health.connect.client.records.BloodPressureRecord::class)) it.systolicBP else null,
                diastolicBP = if (has(androidx.health.connect.client.records.BloodPressureRecord::class)) it.diastolicBP else null,
                vo2Max = if (has(androidx.health.connect.client.records.Vo2MaxRecord::class)) it.vo2Max else null,
                respiratoryRate = if (has(androidx.health.connect.client.records.RespiratoryRateRecord::class)) it.respiratoryRate else null
            )
        }

        // Intervals cache
        val intervalsRecord = database.intervalsDao().getWellnessForDate(date)
        val shouldRefreshIntervals = intervalsRecord == null || 
                (isToday && (intervalsRecord.lastUpdated == null || ChronoUnit.MINUTES.between(intervalsRecord.lastUpdated, Instant.now()) > 5)) ||
                (intervalsRecord.lastUpdated != null && ChronoUnit.HOURS.between(intervalsRecord.lastUpdated, Instant.now()) > 24)
        
        val intervalsData = if (shouldRefreshIntervals) refreshIntervalsCache(date) else {
            intervalsRecord?.let {
                IntervalsWellnessData(
                    id = date.toString(),
                    weight = it.weight,
                    restingHR = it.restingHR,
                    hrv = it.hrv,
                    kcalConsumed = it.kcalConsumed,
                    sleepSecs = it.sleepSecs,
                    avgSleepingHR = it.avgSleepingHR,
                    spO2 = it.spO2,
                    systolic = it.systolic,
                    diastolic = it.diastolic,
                    bloodGlucose = it.bloodGlucose,
                    bodyFat = it.bodyFat,
                    vo2max = it.vo2max,
                    steps = it.steps,
                    respiration = it.respiration,
                    carbohydrates = it.carbohydrates,
                    protein = it.protein,
                    fatTotal = it.fatTotal,
                    lastSyncedAt = it.lastSyncedAt
                )
            }
        }

        // CoachWatts cache
        val cwRecord = database.coachWattsDao().getWellnessForDate(date)
        val shouldRefreshCW = cwRecord == null || 
                (isToday && (cwRecord.lastUpdated == null || ChronoUnit.MINUTES.between(cwRecord.lastUpdated, Instant.now()) > 5)) ||
                (cwRecord.lastUpdated != null && ChronoUnit.HOURS.between(cwRecord.lastUpdated, Instant.now()) > 24)
        
        val cwData = if (shouldRefreshCW) {
            val refreshed = refreshCoachWattsCache(date)
            refreshed?.let { CoachWattsWellnessData(
                date = date.toString(),
                weight = it.weight,
                resting_hr = it.restingHR,
                hrv = it.hrv,
                sleep_seconds = it.sleepMinutes?.times(60),
                spo2 = it.spO2,
                systolic = it.systolic,
                diastolic = it.diastolic,
                glucose = it.glucose,
                body_fat = it.bodyFat,
                vo2max = it.vo2max,
                steps = it.steps,
                respiration = it.respiration,
                calories = it.calories,
                total_calories = it.totalCalories,
                avg_sleeping_hr = it.avgSleepingHR,
                carbs = it.carbs,
                protein = it.protein,
                fat = it.fat,
                lastSyncedAt = it.lastSyncedAt
            ) }
        } else {
            cwRecord?.let { CoachWattsWellnessData(
                date = date.toString(),
                weight = it.weight,
                resting_hr = it.restingHR,
                hrv = it.hrv,
                sleep_seconds = it.sleepMinutes?.times(60),
                spo2 = it.spO2,
                systolic = it.systolic,
                diastolic = it.diastolic,
                glucose = it.glucose,
                body_fat = it.bodyFat,
                vo2max = it.vo2max,
                steps = it.steps,
                respiration = it.respiration,
                calories = it.calories,
                total_calories = it.totalCalories,
                avg_sleeping_hr = it.avgSleepingHR,
                carbs = it.carbs,
                protein = it.protein,
                fat = it.fat,
                lastSyncedAt = it.lastSyncedAt
            ) }
        }

        val provenance = DataProvenance(
            stepsSource = buildStepsProvenance(wellnessRecord?.steps),
            caloriesSource = buildCaloriesProvenance(wellnessRecord?.caloriesBurned),
            heartRateSource = buildHeartRateProvenance(wellnessRecord?.restingHR, sleep?.avgHeartRate),
            sleepSource = sleep?.sourceInfo,
            weightSource = buildWeightProvenance(wellnessRecord?.weightKg),
            bodyCompositionSource = buildBodyCompProvenance(wellnessRecord),
            hrvSource = buildHrvProvenance(wellnessRecord?.hrvMs),
            vitalsSource = buildVitalsProvenance(wellnessRecord)
        )

        return DailySummary(
            date = date,
            steps = wellnessRecord?.steps,
            caloriesBurned = wellnessRecord?.caloriesBurned,
            activeMinutes = wellnessRecord?.activeMinutes,
            sleep = sleep,
            restingHR = wellnessRecord?.restingHR,
            hrvMs = wellnessRecord?.hrvMs,
            weightKg = wellnessRecord?.weightKg,
            bodyFatPercentage = wellnessRecord?.bodyFatPercentage,
            spo2Percentage = wellnessRecord?.spo2Percentage,
            glucoseMmol = wellnessRecord?.glucoseMmol,
            systolicBP = wellnessRecord?.systolicBP,
            diastolicBP = wellnessRecord?.diastolicBP,
            vo2Max = wellnessRecord?.vo2Max,
            respiratoryRate = wellnessRecord?.respiratoryRate,
            dataProvenance = provenance,
            intervalsWellness = intervalsData,
            coachWattsWellness = cwData,
            grantedPermissions = permissions
        )
    }

    private fun buildStepsProvenance(steps: Int?): String? = steps?.let { "Health Connect: $it steps" }
    private fun buildCaloriesProvenance(calories: Double?): String? = if (calories == null || calories <= 0) null else "Health Connect: ${calories.toInt()} kcal active"
    private fun buildWeightProvenance(weight: Double?): String? = weight?.let { "Health Connect: ${"%.1f".format(it)} kg" }
    private fun buildHrvProvenance(hrv: Double?): String? = hrv?.let { "Health Connect: ${it.toInt()} ms (avg)" }

    private fun buildHeartRateProvenance(restingHR: Int?, sleepHR: Int?): String? {
        if (restingHR == null && sleepHR == null) return null
        val parts = mutableListOf<String>()
        sleepHR?.let { parts.add("Sleep $it") }
        restingHR?.let { parts.add("Resting $it") }
        return "Health Connect: ${parts.joinToString(", ")} bpm"
    }

    private fun buildBodyCompProvenance(record: DailyWellnessRecord?): String? {
        if (record == null) return null
        val parts = mutableListOf<String>()
        record.bodyFatPercentage?.let { parts.add("Fat: ${"%.1f".format(it)}%") }
        return if (parts.isNotEmpty()) "Health Connect: ${parts.joinToString(", ")}" else null
    }

    private fun buildVitalsProvenance(record: DailyWellnessRecord?): String? {
        if (record == null) return null
        val parts = mutableListOf<String>()
        record.spo2Percentage?.let { parts.add("SpO2: ${it.toInt()}%") }
        record.glucoseMmol?.let { parts.add("Glucose: ${"%.1f".format(it)}") }
        record.vo2Max?.let { parts.add("VO2 Max: ${"%.1f".format(it)}") }
        record.respiratoryRate?.let { parts.add("Resp: ${it.toInt()}rpm") }
        return if (parts.isNotEmpty()) "Health Connect: ${parts.joinToString(", ")}" else null
    }
}
