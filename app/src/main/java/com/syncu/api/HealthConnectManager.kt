package com.syncu.api

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.syncu.data.SleepSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.reflect.KClass

/**
 * Health Connect Manager
 * Synchronized with AndroidManifest.xml permissions
 */
class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    companion object {
        private const val TAG = "HealthConnectManager"
        
        // Background permission string
        const val PERMISSION_BACKGROUND_READ = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        // Base permissions (16 permissions - removed Distance and Total Calories)
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(BoneMassRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class)
        )
    }

    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: Exception) { emptySet() }
    }

    /**
     * Checks if a specific record type has read permission granted.
     */
    suspend fun hasReadPermission(recordType: KClass<out Record>): Boolean {
        return try {
            val granted = getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(recordType))
        } catch (e: Exception) { false }
    }

    private data class SleepInterval(
        val start: Instant,
        val end: Instant,
        val stage: Int,
        val sessionId: String,
        val parentDurationSecs: Long
    )

    /**
     * Read sleep data for a date.
     */
    suspend fun getSleepForDate(date: LocalDate): SleepSession? {
        if (!hasReadPermission(SleepSessionRecord::class)) return null
        
        return try {
            val zoneId = ZoneId.systemDefault()
            val startOfSleepDay = date.minusDays(1).atTime(18, 0).atZone(zoneId).toInstant()
            val endOfSleepDay = date.atTime(18, 0).atZone(zoneId).toInstant()

            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfSleepDay.minus(Duration.ofHours(12)), endOfSleepDay)
            )

            val response = healthConnectClient.readRecords(request)
            val sessionsInWindow = response.records.filter {
                it.endTime.isAfter(startOfSleepDay) && !it.endTime.isAfter(endOfSleepDay)
            }

            if (sessionsInWindow.isEmpty()) return null

            val hasDetailedDataGlobally = sessionsInWindow.any { s -> s.stages.any { it.stage in listOf(1, 3, 4, 5, 6, 7) } }
            val allIntervals = mutableListOf<SleepInterval>()
            val sessionInfoMap = mutableMapOf<String, String>()
            val sessionTotalDurationMap = mutableMapOf<String, Long>()
            val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")

            for (session in sessionsInWindow) {
                val sessionHasStages = session.stages.any { it.stage in listOf(1, 3, 4, 5, 6, 7) }
                if (hasDetailedDataGlobally && !sessionHasStages) continue

                val sessionDurSecs = Duration.between(session.startTime, session.endTime).seconds
                sessionInfoMap[session.metadata.id] = "Session: ${session.startTime.atZone(zoneId).format(dateTimeFormatter)} to ${session.endTime.atZone(zoneId).format(dateTimeFormatter)} (${sessionDurSecs/60}m)"
                sessionTotalDurationMap[session.metadata.id] = sessionDurSecs

                for (stage in session.stages) {
                    if (stage.stage in listOf(1, 3, 4, 5, 6, 7) || (!hasDetailedDataGlobally && stage.stage == 2)) {
                        allIntervals.add(SleepInterval(stage.startTime, stage.endTime, stage.stage, session.metadata.id, sessionDurSecs))
                    }
                }
            }

            if (allIntervals.isEmpty()) return null

            val boundaries = (allIntervals.map { it.start } + allIntervals.map { it.end }).distinct().sorted()
            val sessionContributionMap = mutableMapOf<String, Long>()
            val finalStageIntervals = mutableListOf<String>()

            var awakeSecs = 0L
            var lightSecs = 0L
            var deepSecs = 0L
            var remSecs = 0L
            var hasSpecificStages = false
            var firstUsedStart: Instant? = null
            var lastUsedEnd: Instant? = null
            
            val computedStages = mutableListOf<Pair<Long, Int>>() // Duration, Stage

            for (i in 0 until boundaries.size - 1) {
                val start = boundaries[i]
                val end = boundaries[i+1]
                val sliceDur = Duration.between(start, end).seconds
                val covering = allIntervals.filter { it.start <= start && it.end >= end }

                if (covering.isNotEmpty()) {
                    val winner = covering.minByOrNull { interval ->
                        val stageRank = when (interval.stage) {
                            6 -> 1 // REM
                            5 -> 2 // Deep
                            4 -> 3 // Light
                            1, 3, 7 -> 4 // Awake
                            else -> 5 // Generic
                        }
                        (interval.parentDurationSecs.toDouble() * 10) + stageRank
                    }

                    winner?.let {
                        if (firstUsedStart == null) firstUsedStart = start
                        lastUsedEnd = end
                        sessionContributionMap[it.sessionId] = (sessionContributionMap[it.sessionId] ?: 0L) + sliceDur
                        finalStageIntervals.add("${start.toEpochMilli()},${end.toEpochMilli()},${it.stage}")
                        computedStages.add(sliceDur to it.stage)

                        when (it.stage) {
                            1, 3, 7 -> awakeSecs += sliceDur
                            4 -> { lightSecs += sliceDur; hasSpecificStages = true }
                            5 -> { deepSecs += sliceDur; hasSpecificStages = true }
                            6 -> { remSecs += sliceDur; hasSpecificStages = true }
                            2 -> if (!hasSpecificStages) lightSecs += sliceDur
                        }
                    }
                }
            }
            
            // Calculate "Asleep" duration: Time in Bed minus initial and trailing awake stages
            var asleepSecs = 0L
            if (computedStages.isNotEmpty()) {
                val sleepStageIds = listOf(2, 4, 5, 6)
                val firstSleepIdx = computedStages.indexOfFirst { it.second in sleepStageIds }
                val lastSleepIdx = computedStages.indexOfLast { it.second in sleepStageIds }
                
                if (firstSleepIdx != -1 && lastSleepIdx != -1) {
                    for (i in firstSleepIdx..lastSleepIdx) {
                        asleepSecs += computedStages[i].first
                    }
                }
            }

            val avgSleepHeartRate = if (firstUsedStart != null && lastUsedEnd != null && hasReadPermission(HeartRateRecord::class)) {
                try {
                    val hrResponse = healthConnectClient.aggregate(
                        AggregateRequest(
                            metrics = setOf(HeartRateRecord.BPM_AVG),
                            timeRangeFilter = TimeRangeFilter.between(firstUsedStart!!, lastUsedEnd!!)
                        )
                    )
                    hrResponse[HeartRateRecord.BPM_AVG]?.toInt()
                } catch (e: Exception) { null }
            } else null

            val eventLog = StringBuilder()
            sessionInfoMap.forEach { (id, info) ->
                val contributed = sessionContributionMap[id] ?: 0L
                val total = sessionTotalDurationMap[id] ?: 1L
                val status = if (contributed >= total - 1) "[CONTRIBUTED]" else "[PARTIAL]"
                eventLog.append("$info $status\n")
            }

            val totalInBedSecs = awakeSecs + lightSecs + deepSecs + remSecs

            return SleepSession(
                id = "agg_${date}",
                startTime = firstUsedStart ?: Instant.now(),
                endTime = lastUsedEnd ?: Instant.now(),
                durationMinutes = (totalInBedSecs / 60.0).roundToLong(),
                deepSleepMinutes = if (hasSpecificStages) (deepSecs / 60.0).roundToLong() else null,
                remSleepMinutes = if (hasSpecificStages) (remSecs / 60.0).roundToLong() else null,
                lightSleepMinutes = if (hasSpecificStages) (lightSecs / 60.0).roundToLong() else null,
                awakeSleepMinutes = if (awakeSecs > 0) (awakeSecs / 60.0).roundToLong() else null,
                avgHeartRate = avgSleepHeartRate,
                sourceInfo = "Health Connect",
                notes = eventLog.toString().trim(),
                stageIntervals = finalStageIntervals.joinToString(";"),
                asleepDurationMinutes = (asleepSecs / 60.0).roundToLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "getSleepForDate error", e)
            null
        }
    }

    suspend fun getStepsForDate(date: LocalDate): Int {
        if (!hasReadPermission(StepsRecord::class)) return 0
        return try {
            val startInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
        } catch (e: Exception) { 0 }
    }

    suspend fun getActiveCaloriesForDate(date: LocalDate): Double {
        if (!hasReadPermission(ActiveCaloriesBurnedRecord::class)) return 0.0
        return try {
            val startInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    suspend fun getBasalCaloriesForDate(date: LocalDate): Double {
        if (!hasReadPermission(BasalMetabolicRateRecord::class)) return 0.0
        return try {
            val startInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )
            response[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) { 0.0 }
    }

    suspend fun getActiveMinutesForDate(date: LocalDate): Int = 0

    suspend fun isAvailable(): Boolean = 
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            val required = PERMISSIONS.map { it.toString() }
            granted.containsAll(required)
        } catch (e: Exception) { false }
    }
}
