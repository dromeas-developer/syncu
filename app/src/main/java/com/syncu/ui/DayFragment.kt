package com.syncu.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.syncu.R
import com.syncu.api.CoachWattsApiClient
import com.syncu.api.ExtendedHealthConnectManager
import com.syncu.api.HealthConnectManager
import com.syncu.api.IntervalsWellnessApiClient
import com.syncu.data.*
import com.syncu.utils.SecureCredentialsManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import kotlin.math.abs

class DayFragment : Fragment() {

    private lateinit var date: LocalDate
    private var currentSummary: DailySummary? = null
    
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar

    // Cards
    private lateinit var cardActivity: CardView
    private lateinit var cardVitals: CardView
    private lateinit var cardHeart: CardView
    private lateinit var cardBody: CardView
    private lateinit var cardSleep: CardView

    // Metric Rows Mapping
    private inner class MetricViews(
        val row: View,
        val tvValue: TextView,
        val ivIntervals: ImageView,
        val ivCoachWatts: ImageView
    )

    private lateinit var mvSteps: MetricViews
    private lateinit var mvCalories: MetricViews
    private lateinit var mvSystolic: MetricViews
    private lateinit var mvDiastolic: MetricViews
    private lateinit var mvSpO2: MetricViews
    private lateinit var mvBloodGlucose: MetricViews
    private lateinit var mvVO2Max: MetricViews
    private lateinit var mvRespiratoryRate: MetricViews
    private lateinit var mvRestingHR: MetricViews
    private lateinit var mvSleepHR: MetricViews
    private lateinit var mvHRV: MetricViews
    private lateinit var mvWeight: MetricViews
    private lateinit var mvBodyFat: MetricViews
    private lateinit var mvSleepDuration: MetricViews
    private lateinit var mvSleepAsleep: MetricViews

    private lateinit var sleepChartView: SleepChartView
    private lateinit var rowAwakeSleep: View
    private lateinit var tvAwakeSleep: TextView
    private lateinit var tvAwakeSleepPercent: TextView
    private lateinit var rowRemSleep: View
    private lateinit var tvRemSleep: TextView
    private lateinit var tvRemSleepPercent: TextView
    private lateinit var rowLightSleep: View
    private lateinit var tvLightSleep: TextView
    private lateinit var tvLightSleepPercent: TextView
    private lateinit var rowDeepSleep: View
    private lateinit var tvDeepSleep: TextView
    private lateinit var tvDeepSleepPercent: TextView

    companion object {
        private const val ARG_DATE = "date"
        fun newInstance(date: LocalDate) = DayFragment().apply {
            arguments = Bundle().apply { putString(ARG_DATE, date.toString()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        date = LocalDate.parse(requireArguments().getString(ARG_DATE))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_day, container, false)
        initViews(view)
        setupSwipeRefresh()
        setupExpandCollapse(view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDayData(forceRefresh = false)
    }

    private fun initViews(view: View) {
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        progressBar = view.findViewById(R.id.progressBar)

        cardActivity = view.findViewById(R.id.cardActivity)
        cardVitals = view.findViewById(R.id.cardVitals)
        cardHeart = view.findViewById(R.id.cardHeart)
        cardBody = view.findViewById(R.id.cardBody)
        cardSleep = view.findViewById(R.id.cardSleep)

        mvSteps = MetricViews(view.findViewById(R.id.rowSteps), view.findViewById(R.id.tvSteps), view.findViewById(R.id.ivStepsIntervals), view.findViewById(R.id.ivStepsCoachWatts))
        mvCalories = MetricViews(view.findViewById(R.id.rowCalories), view.findViewById(R.id.tvCalories), view.findViewById(R.id.ivCaloriesIntervals), view.findViewById(R.id.ivCaloriesCoachWatts))
        
        mvSystolic = MetricViews(view.findViewById(R.id.rowSystolic), view.findViewById(R.id.tvSystolic), view.findViewById(R.id.ivSystolicIntervals), view.findViewById(R.id.ivSystolicCoachWatts))
        mvDiastolic = MetricViews(view.findViewById(R.id.rowDiastolic), view.findViewById(R.id.tvDiastolic), view.findViewById(R.id.ivDiastolicIntervals), view.findViewById(R.id.ivDiastolicCoachWatts))
        mvSpO2 = MetricViews(view.findViewById(R.id.rowSpO2), view.findViewById(R.id.tvSpO2), view.findViewById(R.id.ivSpO2Intervals), view.findViewById(R.id.ivSpO2CoachWatts))
        mvBloodGlucose = MetricViews(view.findViewById(R.id.rowBloodGlucose), view.findViewById(R.id.tvBloodGlucose), view.findViewById(R.id.ivBloodGlucoseIntervals), view.findViewById(R.id.ivBloodGlucoseCoachWatts))
        mvVO2Max = MetricViews(view.findViewById(R.id.rowVO2Max), view.findViewById(R.id.tvVO2Max), view.findViewById(R.id.ivVO2MaxIntervals), view.findViewById(R.id.ivVO2MaxCoachWatts))
        mvRespiratoryRate = MetricViews(view.findViewById(R.id.rowRespiratoryRate), view.findViewById(R.id.tvRespiratoryRate), view.findViewById(R.id.ivRespiratoryRateIntervals), view.findViewById(R.id.ivRespiratoryRateCoachWatts))

        mvRestingHR = MetricViews(view.findViewById(R.id.rowRestingHR), view.findViewById(R.id.tvRestingHR), view.findViewById(R.id.ivRestingHRIntervals), view.findViewById(R.id.ivRestingHRCoachWatts))
        mvSleepHR = MetricViews(view.findViewById(R.id.rowSleepHR), view.findViewById(R.id.tvSleepHR), view.findViewById(R.id.ivSleepHRIntervals), view.findViewById(R.id.ivSleepHRCoachWatts))
        mvHRV = MetricViews(view.findViewById(R.id.rowHRV), view.findViewById(R.id.tvHRV), view.findViewById(R.id.ivHRVIntervals), view.findViewById(R.id.ivHRVCoachWatts))

        mvWeight = MetricViews(view.findViewById(R.id.rowWeight), view.findViewById(R.id.tvWeight), view.findViewById(R.id.ivWeightIntervals), view.findViewById(R.id.ivWeightCoachWatts))
        mvBodyFat = MetricViews(view.findViewById(R.id.rowBodyFat), view.findViewById(R.id.tvBodyFat), view.findViewById(R.id.ivBodyFatIntervals), view.findViewById(R.id.ivBodyFatCoachWatts))

        mvSleepDuration = MetricViews(view.findViewById(R.id.rowSleepDuration), view.findViewById(R.id.tvSleepDuration), view.findViewById(R.id.ivSleepDurationIntervals), view.findViewById(R.id.ivSleepDurationCoachWatts))
        mvSleepAsleep = MetricViews(view.findViewById(R.id.rowSleepAsleep), view.findViewById(R.id.tvSleepAsleep), view.findViewById(R.id.ivSleepAsleepIntervals), view.findViewById(R.id.ivSleepAsleepCoachWatts))

        sleepChartView = view.findViewById(R.id.sleepChartView)
        rowAwakeSleep = view.findViewById(R.id.rowSleepAwakeDetail)
        tvAwakeSleep = view.findViewById(R.id.tvSleepAwakeDetail)
        tvAwakeSleepPercent = view.findViewById(R.id.tvSleepAwakePercent)
        rowRemSleep = view.findViewById(R.id.rowSleepREMDetail)
        tvRemSleep = view.findViewById(R.id.tvSleepREMDetail)
        tvRemSleepPercent = view.findViewById(R.id.tvSleepREMPercent)
        rowLightSleep = view.findViewById(R.id.rowSleepLightDetail)
        tvLightSleep = view.findViewById(R.id.tvSleepLightDetail)
        tvLightSleepPercent = view.findViewById(R.id.tvSleepLightPercent)
        rowDeepSleep = view.findViewById(R.id.rowSleepDeepDetail)
        tvDeepSleep = view.findViewById(R.id.tvSleepDeepDetail)
        tvDeepSleepPercent = view.findViewById(R.id.tvSleepDeepPercent)
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener { loadDayData(forceRefresh = true) }
    }

    private fun setupExpandCollapse(view: View) {
        view.findViewById<View>(R.id.headerActivity).setOnClickListener { toggleCard(view.findViewById(R.id.contentActivity), view.findViewById(R.id.iconActivity)) }
        view.findViewById<View>(R.id.headerVitals).setOnClickListener { toggleCard(view.findViewById(R.id.contentVitals), view.findViewById(R.id.iconVitals)) }
        view.findViewById<View>(R.id.headerHeart).setOnClickListener { toggleCard(view.findViewById(R.id.contentHeart), view.findViewById(R.id.iconHeart)) }
        view.findViewById<View>(R.id.headerBody).setOnClickListener { toggleCard(view.findViewById(R.id.contentBody), view.findViewById(R.id.iconBody)) }
        view.findViewById<View>(R.id.headerSleep).setOnClickListener { toggleCard(view.findViewById(R.id.contentSleep), view.findViewById(R.id.iconSleep)) }
    }

    fun syncDataToIntervals() {
        val summary = currentSummary ?: return
        
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val creds = SecureCredentialsManager(requireContext())
                val now = Instant.now()
                val db = AppDatabase.getDatabase(requireContext(), charArrayOf())

                // Sync to Intervals.icu if configured
                if (creds.getApiKey() != null) {
                    val client = IntervalsWellnessApiClient(creds.getApiKey()!!, creds.getAthleteId()!!)
                    if (client.uploadWellnessData(summary).isSuccess) {
                        db.intervalsDao().updateLastSyncedAt(date, now)
                    }
                }

                // Sync to CoachWatts if configured
                if (creds.getCoachWattsToken() != null) {
                    val client = CoachWattsApiClient(creds.getCoachWattsToken()!!)
                    if (client.uploadWellnessData(summary).isSuccess) {
                        db.coachWattsDao().updateLastSyncedAt(date, now)
                    }
                }

                Toast.makeText(requireContext(), "Sync complete", Toast.LENGTH_SHORT).show()
                (activity as? MainActivity)?.updateLastSyncTimestamp(date)
                loadDayData(forceRefresh = true)
            } catch (e: Exception) {
                Log.e("DayFragment", "Sync error", e)
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun toggleCard(content: View, icon: ImageView) {
        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            icon.setImageResource(android.R.drawable.arrow_down_float)
        } else {
            content.visibility = View.VISIBLE
            icon.setImageResource(android.R.drawable.arrow_up_float)
        }
    }

    fun refreshData() { loadDayData(forceRefresh = false) }

    fun refreshSyncButtonStatus() {
        if (!isAdded || isDetached) return
        val summary = currentSummary ?: return
        
        fun isMeaningful(n: Number?): Boolean {
            val d = n?.toDouble() ?: 0.0
            return d > 0.05
        }

        val hcSleepMinutes = (summary.sleep?.asleepDurationMinutes ?: summary.sleep?.durationMinutes ?: 0L).toInt()
        val hasHCData = isMeaningful(summary.steps) || isMeaningful(summary.restingHR) || 
                        isMeaningful(summary.hrvMs) || hcSleepMinutes > 0 || isMeaningful(summary.weightKg)

        (activity as? MainActivity)?.setSyncButtonEnabled(hasHCData)
    }

    private fun loadDayData(forceRefresh: Boolean) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = false
                val database = AppDatabase.getDatabase(requireContext(), charArrayOf())
                val dbHelper = DatabaseHelper(requireContext(), database, HealthConnectManager(requireContext()), ExtendedHealthConnectManager(requireContext()))
                
                if (forceRefresh) dbHelper.loadDataForDate(date)
                
                val summary = dbHelper.getDailySummary(date)
                currentSummary = summary
                displaySummary(summary)
                
                refreshSyncButtonStatus()
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("DayFragment", "Error loading data", e)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun formatMinutes(minutes: Long?): String {
        if (minutes == null) return "--"
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    private fun displaySummary(summary: DailySummary) {
        val granted = summary.grantedPermissions
        val intervals = summary.intervalsWellness
        val cw = summary.coachWattsWellness

        fun hasPerm(recordType: kotlin.reflect.KClass<out Record>): Boolean = granted.contains(HealthPermission.getReadPermission(recordType))

        fun updateMetric(mv: MetricViews, hcValue: Number?, intValue: Number?, cwValue: Number?, perm: kotlin.reflect.KClass<out Record>?, format: String = "%.1f", isInt: Boolean = false) {
            val hasPermission = perm == null || hasPerm(perm)
            
            // Value display
            mv.tvValue.text = if (hasPermission) {
                if (hcValue == null || hcValue.toDouble() == 0.0) "--"
                else if (isInt) hcValue.toDouble().roundToInt().toString()
                else when (hcValue) {
                    is Double -> format.format(hcValue)
                    else -> hcValue.toString()
                }
            } else "n.a."

            // Service Sync / Warning logic
            fun applyIcon(iv: ImageView, serviceVal: Number?) {
                if (serviceVal == null || serviceVal.toDouble() == 0.0) {
                    iv.visibility = View.INVISIBLE
                    return
                }
                
                iv.visibility = View.VISIBLE
                val hcD = hcValue?.toDouble() ?: 0.0
                val svcD = serviceVal.toDouble()
                
                val hasDifference = if (isInt) {
                    abs(hcD.roundToInt() - svcD.roundToInt()) >= 1
                } else {
                    abs(hcD - svcD) > 0.11 // allow for small float errors
                }

                if (hasPermission && hcValue != null && hcD > 0 && hasDifference) {
                    iv.setImageResource(android.R.drawable.stat_sys_warning)
                    iv.setColorFilter(Color.parseColor("#FF9800"))
                } else {
                    iv.setImageResource(R.drawable.ic_synced)
                    iv.clearColorFilter()
                }
            }

            applyIcon(mv.ivIntervals, intValue)
            applyIcon(mv.ivCoachWatts, cwValue)
            
            val isSvcVal = (intValue != null && intValue.toDouble() != 0.0) || (cwValue != null && cwValue.toDouble() != 0.0)
            mv.row.visibility = if (hasPermission || isSvcVal) View.VISIBLE else View.GONE
        }

        // Activity
        updateMetric(mvSteps, summary.steps, intervals?.steps, cw?.steps, StepsRecord::class, isInt = true)
        updateMetric(mvCalories, summary.caloriesBurned, intervals?.kcalConsumed, cw?.calories, ActiveCaloriesBurnedRecord::class, isInt = true)
        cardActivity.visibility = if (mvSteps.row.visibility == View.VISIBLE || mvCalories.row.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Vitals
        updateMetric(mvSystolic, summary.systolicBP, intervals?.systolic, cw?.systolic, BloodPressureRecord::class, isInt = true)
        updateMetric(mvDiastolic, summary.diastolicBP, intervals?.diastolic, cw?.diastolic, BloodPressureRecord::class, isInt = true)
        updateMetric(mvSpO2, summary.spo2Percentage, intervals?.spO2, cw?.spo2, OxygenSaturationRecord::class, isInt = true)
        updateMetric(mvBloodGlucose, summary.glucoseMmol, intervals?.bloodGlucose, cw?.glucose, BloodGlucoseRecord::class)
        updateMetric(mvVO2Max, summary.vo2Max, intervals?.vo2max, cw?.vo2max, Vo2MaxRecord::class)
        updateMetric(mvRespiratoryRate, summary.respiratoryRate, intervals?.respiration, cw?.respiration, RespiratoryRateRecord::class)
        cardVitals.visibility = if (mvSystolic.row.visibility == View.VISIBLE || mvDiastolic.row.visibility == View.VISIBLE || mvSpO2.row.visibility == View.VISIBLE || mvBloodGlucose.row.visibility == View.VISIBLE || mvVO2Max.row.visibility == View.VISIBLE || mvRespiratoryRate.row.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Heart
        updateMetric(mvRestingHR, summary.restingHR, intervals?.restingHR, cw?.resting_hr, RestingHeartRateRecord::class, isInt = true)
        updateMetric(mvSleepHR, summary.sleep?.avgHeartRate, intervals?.avgSleepingHR, cw?.avg_sleeping_hr, HeartRateRecord::class, isInt = true)
        updateMetric(mvHRV, summary.hrvMs, intervals?.hrv, cw?.hrv, HeartRateVariabilityRmssdRecord::class, isInt = true)
        cardHeart.visibility = if (mvRestingHR.row.visibility == View.VISIBLE || mvSleepHR.row.visibility == View.VISIBLE || mvHRV.row.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Body
        updateMetric(mvWeight, summary.weightKg, intervals?.weight, cw?.weight, WeightRecord::class)
        updateMetric(mvBodyFat, summary.bodyFatPercentage, intervals?.bodyFat, cw?.body_fat, BodyFatRecord::class)
        cardBody.visibility = if (mvWeight.row.visibility == View.VISIBLE || mvBodyFat.row.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Sleep
        val sleep = summary.sleep
        updateMetric(mvSleepDuration, sleep?.durationMinutes, null, null, SleepSessionRecord::class, isInt = true)
        updateMetric(mvSleepAsleep, sleep?.asleepDurationMinutes, intervals?.sleepSecs?.let { it / 60 }, cw?.sleep_seconds?.let { it / 60 }, SleepSessionRecord::class, isInt = true)
        
        if (sleep != null && hasPerm(SleepSessionRecord::class)) {
            val total = sleep.durationMinutes.toDouble().coerceAtLeast(1.0)
            fun setStage(row: View, tv: TextView, tvPct: TextView, mins: Long?) {
                if (mins != null && mins > 0) {
                    row.visibility = View.VISIBLE
                    tv.text = formatMinutes(mins)
                    tvPct.text = "${((mins / total) * 100).roundToInt()}%"
                } else row.visibility = View.GONE
            }
            setStage(rowAwakeSleep, tvAwakeSleep, tvAwakeSleepPercent, sleep.awakeSleepMinutes)
            setStage(rowRemSleep, tvRemSleep, tvRemSleepPercent, sleep.remSleepMinutes)
            setStage(rowLightSleep, tvLightSleep, tvLightSleepPercent, sleep.lightSleepMinutes)
            setStage(rowDeepSleep, tvDeepSleep, tvDeepSleepPercent, sleep.deepSleepMinutes)

            if (!sleep.stageIntervals.isNullOrEmpty()) {
                sleepChartView.visibility = View.VISIBLE
                sleepChartView.setData(sleep.stageIntervals, sleep.startTime.toEpochMilli(), sleep.endTime.toEpochMilli())
            } else sleepChartView.visibility = View.GONE
        } else {
            rowAwakeSleep.visibility = View.GONE
            rowRemSleep.visibility = View.GONE
            rowLightSleep.visibility = View.GONE
            rowDeepSleep.visibility = View.GONE
            sleepChartView.visibility = View.GONE
        }
        cardSleep.visibility = if (mvSleepDuration.row.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }
}
