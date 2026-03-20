package com.syncu.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.syncu.api.HealthConnectManager
import com.syncu.api.IntervalsWellnessApiClient
import com.syncu.api.CoachWattsApiClient
import com.syncu.data.AppDatabase
import com.syncu.data.DatabaseHelper
import com.syncu.utils.PreferencesManager
import com.syncu.utils.SecureCredentialsManager
import com.syncu.api.ExtendedHealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.time.Instant
import kotlinx.coroutines.CancellationException

/**
 * Background sync worker
 * Syncs wellness data to configured services at scheduled times
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefManager = PreferencesManager(applicationContext)
        if (!prefManager.autoSyncEnabled) return@withContext Result.success()

        try {
            Log.i("SyncWorker", "Background sync task started")
            
            scheduleNext(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)

            val credentialsManager = SecureCredentialsManager(applicationContext)
            
            val healthManager = HealthConnectManager(applicationContext)
            val extendedHealthManager = ExtendedHealthConnectManager(applicationContext)
            
            if (!healthManager.isAvailable() || !healthManager.hasAllPermissions()) {
                Log.w("SyncWorker", "Health Connect permissions missing or SDK unavailable")
                return@withContext Result.failure()
            }

            val database = AppDatabase.getDatabase(applicationContext, charArrayOf())
            val dbHelper = DatabaseHelper(
                applicationContext,
                database,
                healthManager,
                extendedHealthManager
            )

            // Sync Yesterday and Today
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val daysToSync = listOf(yesterday, today)

            // 1. Sync to Intervals.icu if configured
            if (credentialsManager.hasIntervalsCredentials()) {
                val intervalsClient = IntervalsWellnessApiClient(
                    credentialsManager.getApiKey()!!, 
                    credentialsManager.getAthleteId()!!
                )
                
                for (date in daysToSync) {
                    dbHelper.loadDataForDate(date)
                    val summary = dbHelper.getDailySummary(date)
                    val result = intervalsClient.uploadWellnessData(summary)
                    if (result.isSuccess) {
                        database.intervalsDao().updateLastSyncedAt(date, Instant.now())
                        dbHelper.refreshIntervalsCache(date)
                    }
                }
            }

            // 2. Sync to CoachWatts if configured
            if (credentialsManager.hasCoachWattsCredentials()) {
                val coachWattsClient = CoachWattsApiClient(credentialsManager.getCoachWattsToken()!!)
                
                for (date in daysToSync) {
                    dbHelper.loadDataForDate(date)
                    val summary = dbHelper.getDailySummary(date)
                    coachWattsClient.uploadWellnessData(summary)
                }
            }

            Log.i("SyncWorker", "Background sync task completed")
            Result.success()
        } catch (e: CancellationException) {
            Log.i("SyncWorker", "Sync worker cancelled")
            throw e
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync execution error", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "syncu_scheduled_sync"

        fun scheduleNext(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val prefManager = PreferencesManager(context)
            if (!prefManager.autoSyncEnabled) {
                cancelSync(context)
                return
            }

            val now = LocalDateTime.now()
            
            val time1 = parseTime(prefManager.syncTime1) ?: LocalTime.of(8, 0)
            var nextOccurrence1 = now.with(time1).withSecond(0).withNano(0)
            if (!nextOccurrence1.isAfter(now)) {
                nextOccurrence1 = nextOccurrence1.plusDays(1)
            }

            var nextRunAt = nextOccurrence1

            if (prefManager.syncTime2Enabled) {
                val time2 = parseTime(prefManager.syncTime2) ?: LocalTime.of(1, 0)
                var nextOccurrence2 = now.with(time2).withSecond(0).withNano(0)
                if (!nextOccurrence2.isAfter(now)) {
                    nextOccurrence2 = nextOccurrence2.plusDays(1)
                }
                
                if (nextOccurrence2.isBefore(nextRunAt)) {
                    nextRunAt = nextOccurrence2
                }
            }

            val delayMillis = Duration.between(now, nextRunAt).toMillis()
            if (delayMillis < 5000) {
                nextRunAt = nextRunAt.plusDays(1)
            }

            val finalDelaySeconds = Duration.between(now, nextRunAt).seconds

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(finalDelaySeconds, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                syncRequest
            )
        }

        private fun parseTime(timeStr: String): LocalTime? {
            return try {
                val parts = timeStr.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } catch (e: Exception) { null }
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
