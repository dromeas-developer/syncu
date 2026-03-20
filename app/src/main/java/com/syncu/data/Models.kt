package com.syncu.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Activity data class with manual edit tracking and data provenance
 */
@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey val id: String,
    val type: ActivityType,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
    val caloriesBurned: Double? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgPower: Double? = null,
    val elevationGain: Double? = null,
    val synced: Boolean = false,
    val syncedAt: Instant? = null,
    val manuallyEdited: Boolean = false,  // Track if user edited
    val lastEditedAt: Instant? = null,    // When edited
    val sourceInfo: String? = null        // Data provenance
)

enum class ActivityType {
    RUNNING, CYCLING, WALKING, SWIMMING, HIKING, YOGA, STRENGTH_TRAINING, OTHER
}

data class HeartRateData(val timestamp: Instant, val bpm: Int)

@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey val id: String = "",
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Long,
    val deepSleepMinutes: Long? = null,
    val remSleepMinutes: Long? = null,
    val lightSleepMinutes: Long? = null,
    val awakeSleepMinutes: Long? = null,
    val avgHeartRate: Int? = null,
    val sourceInfo: String? = null,
    val notes: String? = null,
    val stageIntervals: String? = null, // Format: "startTimeMillis,endTimeMillis,stageType;..."
    val asleepDurationMinutes: Long? = null // Time in Bed minus initial/trailing awake time
)

data class DailySummary(
    val date: java.time.LocalDate,
    val activities: List<Activity> = emptyList(),
    val steps: Int? = null,
    val caloriesBurned: Double? = null,
    val proteinGrams: Double? = null, 
    val carbsGrams: Double? = null,    
    val fatGrams: Double? = null,      
    val activeMinutes: Int? = null,
    val sleep: SleepSession? = null,
    val restingHR: Int? = null,
    val maxHR: Int? = null,
    val hrvMs: Double? = null,
    val weightKg: Double? = null,
    val bodyFatPercentage: Double? = null,
    val leanBodyMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val spo2Percentage: Double? = null,
    val glucoseMmol: Double? = null,
    val systolicBP: Int? = null,
    val diastolicBP: Int? = null,
    val vo2Max: Double? = null, 
    val respiratoryRate: Double? = null,
    val dataProvenance: DataProvenance? = null,
    val intervalsWellness: IntervalsWellnessData? = null, 
    val coachWattsWellness: CoachWattsWellnessData? = null, // Added for CoachWatts comparison
    val grantedPermissions: Set<String> = emptySet()
)

/**
 * Data Provenance - what sources contributed to each metric
 */
data class DataProvenance(
    val stepsSource: String? = null,
    val caloriesSource: String? = null,
    val heartRateSource: String? = null,
    val sleepSource: String? = null,
    val weightSource: String? = null,
    val bodyCompositionSource: String? = null,
    val hrvSource: String? = null,
    val vitalsSource: String? = null
)
