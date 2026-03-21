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
import com.google.android.material.tabs.TabLayout
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

class DayFragment : Fragment() {

    private lateinit var date: LocalDate
    private var currentSummary: DailySummary? = null
    
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tabLayout: TabLayout
    private lateinit var ivServiceIcon: ImageView
    private lateinit var ivServiceIconVitals: ImageView
    private lateinit var ivServiceIconHeart: ImageView
    private lateinit var ivServiceIconBody: ImageView
    private lateinit var ivServiceIconSleep: ImageView

    // Cards
    private lateinit var cardActivity: CardView
    private lateinit var cardVitals: CardView
    private lateinit var cardHeart: CardView
    private lateinit var cardBody: CardView
    private lateinit var cardSleep: CardView

    // Activity Rows
    private lateinit var rowSteps: View
    private lateinit var tvSteps: TextView
    private lateinit var tvStepsService: TextView
    private lateinit var rowCalories: View
    private lateinit var tvCalories: TextView
    private lateinit var tvCaloriesService: TextView

    // Vitals Rows
    private lateinit var rowSystolic: View
    private lateinit var tvSystolic: TextView
    private lateinit var tvSystolicService: TextView
    private lateinit var rowDiastolic: View
    private lateinit var tvDiastolic: TextView
    private lateinit var tvDiastolicService: TextView
    private lateinit var rowSpO2: View
    private lateinit var tvSpO2: TextView
    private lateinit var tvSpO2Service: TextView
    private lateinit var rowBloodGlucose: View
    private lateinit var tvBloodGlucose: TextView
    private lateinit var tvBloodGlucoseService: TextView
    private lateinit var rowVO2Max: View
    private lateinit var tvVO2Max: TextView
    private lateinit var tvVO2MaxService: TextView
    private lateinit var rowRespiratoryRate: View
    private lateinit var tvRespiratoryRate: TextView
    private lateinit var tvRespiratoryRateService: TextView

    // Heart Rows
    private lateinit var rowRestingHR: View
    private lateinit var tvRestingHR: TextView
    private lateinit var tvRestingHRService: TextView
    private lateinit var rowSleepHR: View
    private lateinit var tvSleepHR: TextView
    private lateinit var tvSleepHRService: TextView
    private lateinit var rowHRV: View
    private lateinit var tvHRV: TextView
    private lateinit var tvHRVService: TextView

    // Body Rows
    private lateinit var rowWeight: View
    private lateinit var tvWeight: TextView
    private lateinit var tvWeightService: TextView
    private lateinit var rowBodyFat: View
    private lateinit var tvBodyFat: TextView
    private lateinit var tvBodyFatService: TextView

    // Sleep Rows
    private lateinit var rowSleepDuration: View
    private lateinit var tvSleepDuration: TextView
    private lateinit var tvSleepDurationService: TextView
    
    private lateinit var rowSleepAsleep: View
    private lateinit var tvSleepAsleep: TextView
    private lateinit var tvSleepAsleepService: TextView
    
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

    private var services = listOf<String>()
    private var selectedServiceIndex = 0

    companion object {
        private const val ARG_DATE = "date"
        fun newInstance(date: LocalDate) = DayFragment().apply {
            arguments = Bundle().apply { putString(ARG_DATE, date.toString()) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        date = LocalDate.parse(requireArguments().getString(ARG_DATE))
        
        val creds = SecureCredentialsManager(requireContext())
        val list = mutableListOf<String>()
        if (creds.getApiKey() != null) list.add("Intervals.icu")
        if (creds.getCoachWattsToken() != null) list.add("CoachWatts")
        services = list
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_day, container, false)
        initViews(view)
        setupTabs(view)
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
        tabLayout = view.findViewById(R.id.tabLayoutServices)
        ivServiceIcon = view.findViewById(R.id.ivServiceIcon)
        ivServiceIconVitals = view.findViewById(R.id.ivServiceIconVitals)
        ivServiceIconHeart = view.findViewById(R.id.ivServiceIconHeart)
        ivServiceIconBody = view.findViewById(R.id.ivServiceIconBody)
        ivServiceIconSleep = view.findViewById(R.id.ivServiceIconSleep)

        cardActivity = view.findViewById(R.id.cardActivity)
        cardVitals = view.findViewById(R.id.cardVitals)
        cardHeart = view.findViewById(R.id.cardHeart)
        cardBody = view.findViewById(R.id.cardBody)
        cardSleep = view.findViewById(R.id.cardSleep)

        // Rows
        rowSteps = view.findViewById(R.id.rowSteps)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvStepsService = view.findViewById(R.id.tvStepsIntervals)
        
        rowCalories = view.findViewById(R.id.rowCalories)
        tvCalories = view.findViewById(R.id.tvCalories)
        tvCaloriesService = view.findViewById(R.id.tvCaloriesIntervals)

        rowSystolic = view.findViewById(R.id.rowSystolic)
        tvSystolic = view.findViewById(R.id.tvSystolic)
        tvSystolicService = view.findViewById(R.id.tvSystolicIntervals)

        rowDiastolic = view.findViewById(R.id.rowDiastolic)
        tvDiastolic = view.findViewById(R.id.tvDiastolic)
        tvDiastolicService = view.findViewById(R.id.tvDiastolicIntervals)

        rowSpO2 = view.findViewById(R.id.rowSpO2)
        tvSpO2 = view.findViewById(R.id.tvSpO2)
        tvSpO2Service = view.findViewById(R.id.tvSpO2Intervals)

        rowBloodGlucose = view.findViewById(R.id.rowBloodGlucose)
        tvBloodGlucose = view.findViewById(R.id.tvBloodGlucose)
        tvBloodGlucoseService = view.findViewById(R.id.tvBloodGlucoseIntervals)

        rowVO2Max = view.findViewById(R.id.rowVO2Max)
        tvVO2Max = view.findViewById(R.id.tvVO2Max)
        tvVO2MaxService = view.findViewById(R.id.tvVO2MaxIntervals)

        rowRespiratoryRate = view.findViewById(R.id.rowRespiratoryRate)
        tvRespiratoryRate = view.findViewById(R.id.tvRespiratoryRate)
        tvRespiratoryRateService = view.findViewById(R.id.tvRespiratoryRateIntervals)

        rowRestingHR = view.findViewById(R.id.rowRestingHR)
        tvRestingHR = view.findViewById(R.id.tvRestingHR)
        tvRestingHRService = view.findViewById(R.id.tvRestingHRIntervals)

        rowSleepHR = view.findViewById(R.id.rowSleepHR)
        tvSleepHR = view.findViewById(R.id.tvSleepHR)
        tvSleepHRService = view.findViewById(R.id.tvSleepHRIntervals)

        rowHRV = view.findViewById(R.id.rowHRV)
        tvHRV = view.findViewById(R.id.tvHRV)
        tvHRVService = view.findViewById(R.id.tvHRVIntervals)

        rowWeight = view.findViewById(R.id.rowWeight)
        tvWeight = view.findViewById(R.id.tvWeight)
        tvWeightService = view.findViewById(R.id.tvWeightIntervals)

        rowBodyFat = view.findViewById(R.id.rowBodyFat)
        tvBodyFat = view.findViewById(R.id.tvBodyFat)
        tvBodyFatService = view.findViewById(R.id.tvBodyFatIntervals)

        rowSleepDuration = view.findViewById(R.id.rowSleepDuration)
        tvSleepDuration = view.findViewById(R.id.tvSleepDuration)
        tvSleepDurationService = view.findViewById(R.id.tvSleepDurationIntervals)
        
        rowSleepAsleep = view.findViewById(R.id.rowSleepAsleep)
        tvSleepAsleep = view.findViewById(R.id.tvSleepAsleep)
        tvSleepAsleepService = view.findViewById(R.id.tvSleepAsleepIntervals)
        
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

    private fun setupTabs(view: View) {
        tabLayout.removeAllTabs()
        services.forEach { service ->
            tabLayout.addTab(tabLayout.newTab().setText(service))
        }

        if (services.size <= 1) {
            tabLayout.visibility = View.GONE
        } else {
            tabLayout.visibility = View.VISIBLE
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedServiceIndex = tab?.position ?: 0
                updateServiceIcons()
                currentSummary?.let { displaySummary(it) }
                updateActivitySyncTimestamp()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        updateServiceIcons()
    }

    private fun updateServiceIcons() {
        if (services.isEmpty()) return
        val iconRes = if (services[selectedServiceIndex] == "Intervals.icu") {
            R.mipmap.ic_intervals_icon
        } else {
            R.drawable.ic_syncu_logo 
        }
        ivServiceIcon.setImageResource(iconRes)
        ivServiceIconVitals.setImageResource(iconRes)
        ivServiceIconHeart.setImageResource(iconRes)
        ivServiceIconBody.setImageResource(iconRes)
        ivServiceIconSleep.setImageResource(iconRes)
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
        val serviceName = if (services.isNotEmpty()) services[selectedServiceIndex] else "Intervals.icu"
        
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                val creds = SecureCredentialsManager(requireContext())
                
                val result = if (serviceName == "Intervals.icu") {
                    val client = IntervalsWellnessApiClient(creds.getApiKey()!!, creds.getAthleteId()!!)
                    client.uploadWellnessData(summary)
                } else {
                    val client = CoachWattsApiClient(creds.getCoachWattsToken()!!)
                    client.uploadWellnessData(summary)
                }

                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "Synced to $serviceName!", Toast.LENGTH_SHORT).show()
                    val now = Instant.now()
                    if (serviceName == "Intervals.icu") {
                        AppDatabase.getDatabase(requireContext(), charArrayOf()).intervalsDao().updateLastSyncedAt(date, now)
                        currentSummary?.intervalsWellness?.lastSyncedAt = now
                    } else {
                        AppDatabase.getDatabase(requireContext(), charArrayOf()).coachWattsDao().updateLastSyncedAt(date, now)
                        currentSummary?.coachWattsWellness?.lastSyncedAt = now
                    }
                    updateActivitySyncTimestamp()
                    loadDayData(forceRefresh = true)
                } else {
                    Toast.makeText(requireContext(), "Sync failed", Toast.LENGTH_LONG).show()
                }
                progressBar.visibility = View.GONE
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

    fun refreshData() {
        loadDayData(forceRefresh = false)
    }

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
                
                updateActivitySyncTimestamp()
                refreshSyncButtonStatus()
                progressBar.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("DayFragment", "Error loading data", e)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateActivitySyncTimestamp() {
        if (!isAdded || isDetached) return
        
        val isIntervals = services.isNotEmpty() && selectedServiceIndex < services.size && services[selectedServiceIndex] == "Intervals.icu"
        val isCW = services.isNotEmpty() && selectedServiceIndex < services.size && services[selectedServiceIndex] == "CoachWatts"
        
        val lastSynced = when {
            isIntervals -> currentSummary?.intervalsWellness?.lastSyncedAt
            isCW -> currentSummary?.coachWattsWellness?.lastSyncedAt
            else -> null
        }
        
        if (lastSynced != null) {
            val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
            (activity as? MainActivity)?.setLastSyncText("Last sync: ${lastSynced.atZone(ZoneId.systemDefault()).format(formatter)}")
        } else {
            (activity as? MainActivity)?.setLastSyncText("Last sync: Never")
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
        
        val isIntervals = services.isNotEmpty() && selectedServiceIndex < services.size && services[selectedServiceIndex] == "Intervals.icu"
        val isCW = services.isNotEmpty() && selectedServiceIndex < services.size && services[selectedServiceIndex] == "CoachWatts"

        fun hasPerm(recordType: kotlin.reflect.KClass<out Record>): Boolean = granted.contains(HealthPermission.getReadPermission(recordType))
        fun isVal(v: Any?): Boolean = v != null && v != 0 && v != 0.0

        fun sStr(intVal: Any?, cwVal: Any?): String = when {
            isIntervals -> intVal?.toString() ?: "--"
            isCW -> cwVal?.toString() ?: "--"
            else -> "--"
        }
        
        fun sFloat(intVal: Double?, cwVal: Double?): String = when {
            isIntervals -> intVal?.let { "%.1f".format(it) } ?: "--"
            isCW -> cwVal?.let { "%.1f".format(it) } ?: "--"
            else -> "--"
        }

        // Activity Card
        rowSteps.visibility = if (hasPerm(StepsRecord::class) || (isIntervals && isVal(intervals?.steps)) || (isCW && isVal(cw?.steps))) View.VISIBLE else View.GONE
        tvSteps.text = if (hasPerm(StepsRecord::class)) (summary.steps?.toString() ?: "--") else "n.a."
        tvStepsService.text = sStr(intervals?.steps, cw?.steps)

        rowCalories.visibility = if (hasPerm(ActiveCaloriesBurnedRecord::class) || (isIntervals && isVal(intervals?.kcalConsumed)) || (isCW && isVal(cw?.calories))) View.VISIBLE else View.GONE
        tvCalories.text = if (hasPerm(ActiveCaloriesBurnedRecord::class)) (summary.caloriesBurned?.roundToInt()?.toString() ?: "--") else "n.a."
        tvCaloriesService.text = sStr(intervals?.kcalConsumed, cw?.calories?.roundToInt())

        cardActivity.visibility = if (rowSteps.visibility == View.VISIBLE || rowCalories.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Vitals Card
        rowSystolic.visibility = if (hasPerm(BloodPressureRecord::class) || (isIntervals && isVal(intervals?.systolic)) || (isCW && isVal(cw?.systolic))) View.VISIBLE else View.GONE
        tvSystolic.text = if (hasPerm(BloodPressureRecord::class)) (summary.systolicBP?.toString() ?: "--") else "n.a."
        tvSystolicService.text = sStr(intervals?.systolic, cw?.systolic)

        rowDiastolic.visibility = if (hasPerm(BloodPressureRecord::class) || (isIntervals && isVal(intervals?.diastolic)) || (isCW && isVal(cw?.diastolic))) View.VISIBLE else View.GONE
        tvDiastolic.text = if (hasPerm(BloodPressureRecord::class)) (summary.diastolicBP?.toString() ?: "--") else "n.a."
        tvDiastolicService.text = sStr(intervals?.diastolic, cw?.diastolic)

        rowSpO2.visibility = if (hasPerm(OxygenSaturationRecord::class) || (isIntervals && isVal(intervals?.spO2)) || (isCW && isVal(cw?.spo2))) View.VISIBLE else View.GONE
        tvSpO2.text = if (hasPerm(OxygenSaturationRecord::class)) (summary.spo2Percentage?.roundToInt()?.toString() ?: "--") else "n.a."
        tvSpO2Service.text = when {
            isIntervals -> intervals?.spO2?.roundToInt()?.toString() ?: "--"
            isCW -> cw?.spo2?.roundToInt()?.toString() ?: "--"
            else -> "--"
        }

        rowBloodGlucose.visibility = if (hasPerm(BloodGlucoseRecord::class) || (isIntervals && isVal(intervals?.bloodGlucose)) || (isCW && isVal(cw?.glucose))) View.VISIBLE else View.GONE
        tvBloodGlucose.text = if (hasPerm(BloodGlucoseRecord::class)) (summary.glucoseMmol?.let { "%.1f".format(it) } ?: "--") else "n.a."
        tvBloodGlucoseService.text = sFloat(intervals?.bloodGlucose, cw?.glucose)

        rowVO2Max.visibility = if (hasPerm(Vo2MaxRecord::class) || (isIntervals && isVal(intervals?.vo2max)) || (isCW && isVal(cw?.vo2max))) View.VISIBLE else View.GONE
        tvVO2Max.text = if (hasPerm(Vo2MaxRecord::class)) (summary.vo2Max?.let { "%.1f".format(it) } ?: "--") else "n.a."
        tvVO2MaxService.text = sFloat(intervals?.vo2max, cw?.vo2max)

        rowRespiratoryRate.visibility = if (hasPerm(RespiratoryRateRecord::class) || (isIntervals && isVal(intervals?.respiration)) || (isCW && isVal(cw?.respiration))) View.VISIBLE else View.GONE
        tvRespiratoryRate.text = if (hasPerm(RespiratoryRateRecord::class)) (summary.respiratoryRate?.let { "%.1f".format(it) } ?: "--") else "n.a."
        tvRespiratoryRateService.text = sFloat(intervals?.respiration, cw?.respiration)

        cardVitals.visibility = if (rowSystolic.visibility == View.VISIBLE || rowDiastolic.visibility == View.VISIBLE ||
                          rowSpO2.visibility == View.VISIBLE || rowBloodGlucose.visibility == View.VISIBLE ||
                          rowVO2Max.visibility == View.VISIBLE || rowRespiratoryRate.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Heart Card
        rowRestingHR.visibility = if (hasPerm(RestingHeartRateRecord::class) || (isIntervals && isVal(intervals?.restingHR)) || (isCW && isVal(cw?.resting_hr))) View.VISIBLE else View.GONE
        tvRestingHR.text = if (hasPerm(RestingHeartRateRecord::class)) (summary.restingHR?.toString() ?: "--") else "n.a."
        tvRestingHRService.text = sStr(intervals?.restingHR, cw?.resting_hr)

        rowSleepHR.visibility = if (hasPerm(HeartRateRecord::class) || (isIntervals && isVal(intervals?.avgSleepingHR)) || (isCW && isVal(cw?.avg_sleeping_hr))) View.VISIBLE else View.GONE
        tvSleepHR.text = if (hasPerm(HeartRateRecord::class)) (summary.sleep?.avgHeartRate?.toString() ?: "--") else "n.a."
        tvSleepHRService.text = when {
            isIntervals -> intervals?.avgSleepingHR?.roundToInt()?.toString() ?: "--"
            isCW -> cw?.avg_sleeping_hr?.roundToInt()?.toString() ?: "--"
            else -> "--"
        }

        rowHRV.visibility = if (hasPerm(HeartRateVariabilityRmssdRecord::class) || (isIntervals && isVal(intervals?.hrv)) || (isCW && isVal(cw?.hrv))) View.VISIBLE else View.GONE
        tvHRV.text = if (hasPerm(HeartRateVariabilityRmssdRecord::class)) (summary.hrvMs?.roundToInt()?.toString() ?: "--") else "n.a."
        tvHRVService.text = sStr(intervals?.hrv?.roundToInt(), cw?.hrv?.roundToInt())

        cardHeart.visibility = if (rowRestingHR.visibility == View.VISIBLE || rowSleepHR.visibility == View.VISIBLE || rowHRV.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Body Card
        rowWeight.visibility = if (hasPerm(WeightRecord::class) || (isIntervals && isVal(intervals?.weight)) || (isCW && isVal(cw?.weight))) View.VISIBLE else View.GONE
        tvWeight.text = if (hasPerm(WeightRecord::class)) (summary.weightKg?.let { "%.1f".format(it) } ?: "--") else "n.a."
        tvWeightService.text = sFloat(intervals?.weight, cw?.weight)

        rowBodyFat.visibility = if (hasPerm(BodyFatRecord::class) || (isIntervals && isVal(intervals?.bodyFat)) || (isCW && isVal(cw?.body_fat))) View.VISIBLE else View.GONE
        tvBodyFat.text = if (hasPerm(BodyFatRecord::class)) (summary.bodyFatPercentage?.let { "%.1f".format(it) } ?: "--") else "n.a."
        tvBodyFatService.text = sFloat(intervals?.bodyFat, cw?.body_fat)

        cardBody.visibility = if (rowWeight.visibility == View.VISIBLE || rowBodyFat.visibility == View.VISIBLE) View.VISIBLE else View.GONE

        // Sleep Card
        val sleep = summary.sleep
        val hasSleep = sleep != null && hasPerm(SleepSessionRecord::class)
        
        rowSleepDuration.visibility = if (hasSleep || (isIntervals && isVal(intervals?.sleepSecs)) || (isCW && isVal(cw?.sleep_seconds))) View.VISIBLE else View.GONE
        tvSleepDuration.text = if (hasSleep) formatMinutes(sleep?.durationMinutes) else "n.a."
        tvSleepDurationService.text = "--" // Intervals/CoachWatts provide Asleep time
        
        rowSleepAsleep.visibility = if (hasSleep || (isIntervals && isVal(intervals?.sleepSecs)) || (isCW && isVal(cw?.sleep_seconds))) View.VISIBLE else View.GONE
        tvSleepAsleep.text = if (hasSleep) formatMinutes(sleep?.asleepDurationMinutes) else "n.a."
        tvSleepAsleepService.text = when {
            isIntervals -> formatMinutes(intervals?.sleepSecs?.toLong()?.div(60))
            isCW -> formatMinutes(cw?.sleep_seconds?.toLong()?.div(60))
            else -> "--"
        }

        if (hasSleep && sleep != null) {
            val total = sleep.durationMinutes.toDouble().coerceAtLeast(1.0)
            
            fun setStage(row: View, tv: TextView, tvPct: TextView, mins: Long?) {
                if (mins != null && mins > 0) {
                    row.visibility = View.VISIBLE
                    tv.text = formatMinutes(mins)
                    tvPct.text = "${((mins / total) * 100).roundToInt()}%"
                } else {
                    row.visibility = View.GONE
                }
            }

            setStage(rowAwakeSleep, tvAwakeSleep, tvAwakeSleepPercent, sleep.awakeSleepMinutes)
            setStage(rowRemSleep, tvRemSleep, tvRemSleepPercent, sleep.remSleepMinutes)
            setStage(rowLightSleep, tvLightSleep, tvLightSleepPercent, sleep.lightSleepMinutes)
            setStage(rowDeepSleep, tvDeepSleep, tvDeepSleepPercent, sleep.deepSleepMinutes)

            if (!sleep.stageIntervals.isNullOrEmpty()) {
                sleepChartView.visibility = View.VISIBLE
                sleepChartView.setData(sleep.stageIntervals, sleep.startTime.toEpochMilli(), sleep.endTime.toEpochMilli())
            } else {
                sleepChartView.visibility = View.GONE
            }
        } else {
            rowAwakeSleep.visibility = View.GONE
            rowRemSleep.visibility = View.GONE
            rowLightSleep.visibility = View.GONE
            rowDeepSleep.visibility = View.GONE
            sleepChartView.visibility = View.GONE
        }

        cardSleep.visibility = if (rowSleepDuration.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }
}
