package com.syncu.api

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.syncu.data.WellnessSummaryDay
import com.syncu.utils.PreferencesManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Extended Health Connect Manager
 * Reads wellness metrics with pagination and robust error handling
 */
class ExtendedHealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    private val preferencesManager by lazy {
        PreferencesManager(context)
    }

    companion object {
        private const val TAG = "ExtendedHCManager"
        val EXTENDED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        )
    }

    private suspend fun hasPermission(recordType: kotlin.reflect.KClass<out Record>): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(recordType))
        } catch (e: Exception) { false }
    }

    suspend fun getWellnessSummaryForDay(date: LocalDate, asleepStart: Instant? = null, asleepEnd: Instant? = null): WellnessSummaryDay? {
        val startInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endInstant = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)

        val hrv = if (hasPermission(HeartRateVariabilityRmssdRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)
                ).records
                records.firstOrNull()?.heartRateVariabilityMillis
            }.getOrNull()
        } else null

        val weight = if (hasPermission(WeightRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(WeightRecord::class, timeRange)
                ).records
                records.firstOrNull()?.weight?.inKilograms
            }.getOrNull()
        } else null

        val bodyFatPercentage = if (hasPermission(BodyFatRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(BodyFatRecord::class, timeRange)
                ).records
                records.firstOrNull()?.percentage?.value
            }.getOrNull()
        } else null

        // Calculate resting HR based on preference and availability
        var restingHRValue: Long? = null
        val useCustom = try { preferencesManager.useCustomRestingHR } catch (e: Exception) { true }
        
        if (asleepStart != null && asleepEnd != null && useCustom && hasPermission(HeartRateRecord::class)) {
            restingHRValue = calculateLowest30MinAsleepHR(asleepStart, asleepEnd)
        }

        // Fallback to standard record if custom is disabled or failed to yield a result
        if (restingHRValue == null && hasPermission(RestingHeartRateRecord::class)) {
            restingHRValue = runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(RestingHeartRateRecord::class, timeRange)
                ).records
                records.firstOrNull()?.beatsPerMinute
            }.getOrNull()
        }

        val spo2 = if (hasPermission(OxygenSaturationRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)
                ).records
                if (records.isNotEmpty()) {
                    records.map { it.percentage.value }.average().roundToInt().toDouble()
                } else null
            }.getOrNull()
        } else null

        val glucose = if (hasPermission(BloodGlucoseRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(BloodGlucoseRecord::class, timeRange)
                ).records
                if (records.isNotEmpty()) records.map { it.level.inMillimolesPerLiter }.average() else null
            }.getOrNull()
        } else null

        val bp = if (hasPermission(BloodPressureRecord::class)) {
            runCatching {
                val record = healthConnectClient.readRecords(
                    ReadRecordsRequest(BloodPressureRecord::class, timeRange)
                ).records.maxByOrNull { it.time }
                Pair(record?.systolic?.inMillimetersOfMercury?.toInt(), record?.diastolic?.inMillimetersOfMercury?.toInt())
            }.getOrNull()
        } else null

        val vo2Max = if (hasPermission(Vo2MaxRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(Vo2MaxRecord::class, timeRange)
                ).records
                records.firstOrNull()?.vo2MillilitersPerMinuteKilogram
            }.getOrNull()
        } else null

        val respRate = if (hasPermission(RespiratoryRateRecord::class)) {
            runCatching {
                val records = healthConnectClient.readRecords(
                    ReadRecordsRequest(RespiratoryRateRecord::class, timeRange)
                ).records
                records.firstOrNull()?.rate
            }.getOrNull()
        } else null

        // Max Heart Rate from Daily Samples
        val maxHR = if (hasPermission(HeartRateRecord::class)) {
            runCatching {
                val allSamples = mutableListOf<HeartRateRecord.Sample>()
                var pageToken: String? = null
                do {
                    val request = ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = timeRange,
                        pageToken = pageToken
                    )
                    val response = healthConnectClient.readRecords(request)
                    allSamples.addAll(response.records.flatMap { it.samples })
                    pageToken = response.pageToken
                } while (pageToken != null)
                
                if (allSamples.isNotEmpty()) {
                    allSamples.maxOf { it.beatsPerMinute }.toInt()
                } else null
            }.onFailure { Log.e(TAG, "Max HR calculation failed", it) }.getOrNull()
        } else null

        return WellnessSummaryDay(
            date = date,
            hrvMs = hrv,
            restingHR = restingHRValue?.toInt(),
            maxHR = maxHR,
            weightKg = weight,
            bodyFatPercentage = bodyFatPercentage,
            spo2Percentage = spo2,
            glucoseMmol = glucose,
            systolicBP = bp?.first,
            diastolicBP = bp?.second,
            vo2Max = vo2Max,
            respiratoryRate = respRate
        )
    }

    private suspend fun calculateLowest30MinAsleepHR(asleepStart: Instant, asleepEnd: Instant): Long? {
        val result = runCatching {
            val allSamples = mutableListOf<HeartRateRecord.Sample>()
            var pageToken: String? = null
            do {
                val request = ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(asleepStart, asleepEnd),
                    pageToken = pageToken
                )
                val response = healthConnectClient.readRecords(request)
                allSamples.addAll(response.records.flatMap { it.samples })
                pageToken = response.pageToken
            } while (pageToken != null)
            
            val samples = allSamples.sortedBy { it.time }

            if (samples.isEmpty()) {
                return@runCatching null
            }

            var lowestAvg: Double? = null

            // Sliding 30-minute window, stepping by 1 minute
            var currentStart = asleepStart
            while (!currentStart.isAfter(asleepEnd.minus(Duration.ofMinutes(30)))) {
                val currentEnd = currentStart.plus(Duration.ofMinutes(30))
                val subSamples = samples.filter { !it.time.isBefore(currentStart) && it.time.isBefore(currentEnd) }

                if (subSamples.size >= 2) {
                    var totalMillis = 0L
                    var weightedSum = 0.0
                    for (i in 0 until subSamples.size - 1) {
                        val s1 = subSamples[i]
                        val s2 = subSamples[i+1]
                        val dur = Duration.between(s1.time, s2.time).toMillis()
                        weightedSum += (s1.beatsPerMinute + s2.beatsPerMinute) / 2.0 * dur
                        totalMillis += dur
                    }

                    if (totalMillis > 0) {
                        val avg = weightedSum / totalMillis
                        if (lowestAvg == null || avg < lowestAvg!!) {
                            lowestAvg = avg
                        }
                    }
                } else if (subSamples.size == 1) {
                    val avg = subSamples[0].beatsPerMinute.toDouble()
                    if (lowestAvg == null || avg < lowestAvg!!) {
                        lowestAvg = avg
                    }
                }
                currentStart = currentStart.plus(Duration.ofMinutes(1))
            }

            lowestAvg?.roundToLong()
        }.onFailure { Log.e(TAG, "Custom resting HR calculation failed", it) }
        
        return result.getOrNull()
    }
}
