package com.syncu.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.LocalDate

/**
 * Daily wellness record from Health Connect.
 * Schema matched to actual database state to fix Migration IllegalStateException.
 */
@Entity(tableName = "daily_wellness_records")
data class DailyWellnessRecord(
    @PrimaryKey val date: LocalDate,
    val steps: Int? = null,
    val caloriesBurned: Double? = null,
    val activeMinutes: Int? = null,
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
    
    // Metadata - NOT NULL in database
    var lastUpdated: Instant = Instant.now()
)

/**
 * Helper data class for ExtendedHealthConnectManager
 */
data class WellnessSummaryDay(
    val date: LocalDate,
    val hrvMs: Double? = null,
    val restingHR: Int? = null,
    val maxHR: Int? = null,
    val weightKg: Double? = null,
    val bodyFatPercentage: Double? = null,
    val spo2Percentage: Double? = null,
    val glucoseMmol: Double? = null,
    val systolicBP: Int? = null,
    val diastolicBP: Int? = null,
    val vo2Max: Double? = null,
    val respiratoryRate: Double? = null,
    val boneMassKg: Double? = null,
    val leanBodyMassKg: Double? = null
)

/**
 * Intervals.icu wellness data format (API)
 */
data class IntervalsWellnessData(
    val id: String, // Date
    val weight: Double? = null,
    val restingHR: Int? = null,
    val hrv: Double? = null,
    val sleepSecs: Int? = null,
    val avgSleepingHR: Double? = null,
    val spO2: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val bloodGlucose: Double? = null,
    val bodyFat: Double? = null,
    val vo2max: Double? = null,
    val kcalConsumed: Int? = null,
    val steps: Int? = null,
    val respiration: Double? = null,
    val carbohydrates: Double? = null,
    val protein: Double? = null,
    val fatTotal: Double? = null,
    @Transient var lastSyncedAt: Instant? = null
)

/**
 * Room Entity for Intervals Wellness Cache
 */
@Entity(tableName = "intervals_wellness_records")
data class IntervalsWellnessRecord(
    @PrimaryKey val date: LocalDate,
    val weight: Double? = null,
    val restingHR: Int? = null,
    val hrv: Double? = null,
    val kcalConsumed: Int? = null,
    val sleepSecs: Int? = null,
    val avgSleepingHR: Double? = null,
    val spO2: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val bloodGlucose: Double? = null,
    val bodyFat: Double? = null,
    val leanMass: Double? = null,
    val boneMass: Double? = null,
    val vo2max: Double? = null,
    val steps: Int? = null,
    val respiration: Double? = null,
    val carbohydrates: Double? = null,
    val protein: Double? = null,
    val fatTotal: Double? = null,
    
    // Metadata
    var lastUpdated: Instant = Instant.now(),
    var lastSyncedAt: Instant? = null
)

/**
 * CoachWatts wellness data format (API)
 */
data class CoachWattsWellnessData(
    val date: String,
    val weight: Double? = null,
    @SerializedName("restingHr") val resting_hr: Int? = null,
    val hrv: Double? = null,
    @SerializedName("sleepSecs") val sleep_seconds: Int? = null,
    @SerializedName("spO2") val spo2: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val glucose: Double? = null,
    @SerializedName("bodyFat") val body_fat: Double? = null,
    val lean_mass: Double? = null,
    val bone_mass: Double? = null,
    val vo2max: Double? = null,
    val steps: Int? = null,
    val respiration: Double? = null,
    val calories: Double? = null,
    @SerializedName("avgSleepingHr") val avg_sleeping_hr: Double? = null,
    val carbs: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    @Transient var lastSyncedAt: Instant? = null
) {
    // Computed property for UI compatibility
    val sleep_minutes: Int? get() = sleep_seconds?.let { it / 60 }
}

/**
 * Room Entity for CoachWatts Wellness Cache
 */
@Entity(tableName = "coachwatts_wellness_records")
data class CoachWattsWellnessRecord(
    @PrimaryKey val date: LocalDate,
    val weight: Double? = null,
    val restingHR: Int? = null,
    val hrv: Double? = null,
    val sleepMinutes: Int? = null,
    val avgSleepingHR: Double? = null,
    val spO2: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val glucose: Double? = null,
    val bodyFat: Double? = null,
    val leanMass: Double? = null,
    val boneMass: Double? = null,
    val vo2max: Double? = null,
    val steps: Int? = null,
    val respiration: Double? = null,
    val calories: Double? = null,
    val carbs: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    
    // Metadata
    var lastUpdated: Instant = Instant.now(),
    var lastSyncedAt: Instant? = null
)
