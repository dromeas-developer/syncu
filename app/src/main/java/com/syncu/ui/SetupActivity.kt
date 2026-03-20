package com.syncu.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.syncu.R
import com.syncu.api.HealthConnectManager
import com.syncu.api.ExtendedHealthConnectManager
import com.syncu.sync.SyncWorker
import com.syncu.utils.PreferencesManager
import com.syncu.utils.SecureCredentialsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Setup Activity - First-time setup wizard & Settings
 * Handles Health Connect permissions, credentials, cache settings and background sync scheduling
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var credentialsManager: SecureCredentialsManager
    private lateinit var preferencesManager: PreferencesManager
    
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etAthleteId: TextInputEditText
    private lateinit var etCoachWattsToken: TextInputEditText
    
    private lateinit var btnHealthConnect: Button
    private lateinit var btnFooterBack: Button
    private lateinit var btnFooterSave: Button
    private lateinit var switchAutoSync: SwitchCompat
    private lateinit var switchCustomRestingHR: SwitchCompat
    private lateinit var sliderRetention: Slider
    private lateinit var tvRetentionLabel: TextView
    
    private lateinit var btnSyncTime1: Button
    private lateinit var btnSyncTime2: Button
    private lateinit var switchSyncTime2: SwitchCompat
    private lateinit var layoutSyncTimes: View
    private lateinit var btnBuyCoffee: ImageButton

    private var initialApiKey: String? = ""
    private var initialAthleteId: String? = ""
    private var initialCoachWattsToken: String? = ""
    
    private var initialAutoSync: Boolean = true
    private var initialCustomRestingHR: Boolean = false
    private var initialRetentionDays: Int = 14
    private var initialSyncTime1: String = "08:00"
    private var initialSyncTime2: String = "01:00"
    private var initialSyncTime2Enabled: Boolean = true

    private var healthConnectPermissionsGranted = false
    
    private lateinit var requestPermissions: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        setupHeader()
        setupSystemBars()

        credentialsManager = SecureCredentialsManager(this)
        preferencesManager = PreferencesManager(this)

        // Initialize views
        etApiKey = findViewById(R.id.etApiKey)
        etAthleteId = findViewById(R.id.etAthleteId)
        etCoachWattsToken = findViewById(R.id.etCoachWattsToken)
        
        btnHealthConnect = findViewById(R.id.btnHealthConnectPermissions)
        btnFooterBack = findViewById(R.id.btnFooterBack)
        btnFooterSave = findViewById(R.id.btnFooterSave)
        switchAutoSync = findViewById(R.id.switchAutoSync)
        switchCustomRestingHR = findViewById(R.id.switchCustomRestingHR)
        sliderRetention = findViewById(R.id.sliderRetention)
        tvRetentionLabel = findViewById(R.id.tvRetentionLabel)
        
        btnSyncTime1 = findViewById(R.id.btnSyncTime1)
        btnSyncTime2 = findViewById(R.id.btnSyncTime2)
        switchSyncTime2 = findViewById(R.id.switchSyncTime2)
        layoutSyncTimes = findViewById(R.id.layoutSyncTimes)
        btnBuyCoffee = findViewById(R.id.btnBuyCoffee)

        // Load existing credentials
        loadExistingCredentials()

        // Setup change tracking
        setupChangeTracking()

        // Time Picker setup
        btnSyncTime1.setOnClickListener { showTimePicker(1) }
        btnSyncTime2.setOnClickListener { showTimePicker(2) }
        
        btnBuyCoffee.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/dromeas"))
            startActivity(intent)
        }

        val requestPermissionActivityContract = PermissionController.createRequestPermissionResultContract()
        requestPermissions = registerForActivityResult(requestPermissionActivityContract) { granted ->
            lifecycleScope.launch {
                refreshPermissionState()
                checkForChanges()
            }
        }

        lifecycleScope.launch {
            val sdkStatus = HealthConnectClient.getSdkStatus(this@SetupActivity)
            if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE) {
                btnHealthConnect.isEnabled = false
                btnHealthConnect.text = "Health Connect Unavailable"
            } else {
                refreshPermissionState()
            }
        }

        btnHealthConnect.setOnClickListener { handleHealthConnectButtonClick() }
        btnFooterBack.setOnClickListener { finish() }
        btnFooterSave.setOnClickListener { finishSetup() }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            refreshPermissionState()
        }
    }

    private suspend fun refreshPermissionState() {
        try {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            val baseRequired = getBasePermissions()
            
            val baseGrantedCount = baseRequired.count { granted.contains(it) }
            healthConnectPermissionsGranted = baseGrantedCount >= baseRequired.size
            
            updateHealthConnectButton(baseGrantedCount, baseRequired.size)
        } catch (e: Exception) {
            Log.e("SyncU_Setup", "Error refreshing permissions", e)
        }
    }

    private fun setupHeader() {
        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        tvHeaderTitle.text = getString(R.string.settings_title)
    }

    private fun setupSystemBars() {
        val rootLayout = findViewById<ViewGroup>(R.id.layoutHeader).parent as ViewGroup
        val headerLayout = rootLayout.getChildAt(0)
        val footerLayout = findViewById<View>(R.id.layoutFooter)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerLayout.updatePadding(top = systemBars.top)
            footerLayout.updatePadding(bottom = systemBars.bottom)
            view.updatePadding(left = systemBars.left, right = systemBars.right)
            insets
        }
    }

    private fun loadExistingCredentials() {
        initialApiKey = credentialsManager.getApiKey() ?: ""
        initialAthleteId = credentialsManager.getAthleteId() ?: ""
        initialCoachWattsToken = credentialsManager.getCoachWattsToken() ?: ""
        
        initialAutoSync = preferencesManager.autoSyncEnabled
        initialCustomRestingHR = preferencesManager.useCustomRestingHR
        initialRetentionDays = preferencesManager.cacheRetentionDays
        initialSyncTime1 = preferencesManager.syncTime1
        initialSyncTime2 = preferencesManager.syncTime2
        initialSyncTime2Enabled = preferencesManager.syncTime2Enabled
        
        etApiKey.setText(initialApiKey)
        etAthleteId.setText(initialAthleteId)
        etCoachWattsToken.setText(initialCoachWattsToken)
        
        switchAutoSync.isChecked = initialAutoSync
        switchCustomRestingHR.isChecked = initialCustomRestingHR
        sliderRetention.value = initialRetentionDays.toFloat()
        updateRetentionLabel(initialRetentionDays)
        
        btnSyncTime1.text = initialSyncTime1
        btnSyncTime2.text = initialSyncTime2
        switchSyncTime2.isChecked = initialSyncTime2Enabled
        btnSyncTime2.isEnabled = initialSyncTime2Enabled
        btnSyncTime2.alpha = if (initialSyncTime2Enabled) 1.0f else 0.5f
        
        layoutSyncTimes.visibility = if (initialAutoSync) View.VISIBLE else View.GONE
    }

    private fun showTimePicker(slot: Int) {
        val currentTime = if (slot == 1) btnSyncTime1.text.toString() else btnSyncTime2.text.toString()
        val parts = currentTime.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        TimePickerDialog(this, { _, h, m ->
            val formatted = String.format(Locale.US, "%02d:%02d", h, m)
            if (slot == 1) btnSyncTime1.text = formatted else btnSyncTime2.text = formatted
            checkForChanges()
        }, hour, minute, true).show()
    }

    private fun updateRetentionLabel(days: Int) {
        tvRetentionLabel.text = String.format(Locale.US, "Keep cached data for: %d days", days)
    }

    private fun setupChangeTracking() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkForChanges() }
            override fun afterTextChanged(s: Editable?) {}
        }

        etApiKey.addTextChangedListener(watcher)
        etAthleteId.addTextChangedListener(watcher)
        etCoachWattsToken.addTextChangedListener(watcher)
        
        switchAutoSync.setOnClickListener { 
            val isChecked = switchAutoSync.isChecked
            if (isChecked) {
                validateAndEnableAutoSync()
            } else {
                layoutSyncTimes.visibility = View.GONE
                checkForChanges()
            }
        }
        
        switchCustomRestingHR.setOnCheckedChangeListener { _, _ -> checkForChanges() }
        
        switchSyncTime2.setOnCheckedChangeListener { _, isChecked -> 
            btnSyncTime2.isEnabled = isChecked
            btnSyncTime2.alpha = if (isChecked) 1.0f else 0.5f
            checkForChanges() 
        }
        
        sliderRetention.addOnChangeListener { _, value, _ ->
            updateRetentionLabel(value.toInt())
            checkForChanges()
        }
    }

    private fun validateAndEnableAutoSync() {
        lifecycleScope.launch {
            val hasBatteryOpt = isIgnoringBatteryOptimizations()
            val hasBackgroundHC = hasHealthConnectBackgroundPermission()
            
            if (hasBatteryOpt && hasBackgroundHC) {
                layoutSyncTimes.visibility = View.VISIBLE
                checkForChanges()
            } else {
                switchAutoSync.isChecked = false
                showAutoSyncRequirementsDialog(hasBatteryOpt, hasBackgroundHC)
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private suspend fun hasHealthConnectBackgroundPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.contains(HealthConnectManager.PERMISSION_BACKGROUND_READ)
        } catch (e: Exception) { false }
    }

    private fun showAutoSyncRequirementsDialog(hasBatteryOpt: Boolean, hasBackgroundHC: Boolean) {
        val message = StringBuilder("Automatic sync requires two settings:\n\n")
        if (!hasBatteryOpt) message.append("• Battery: Set to 'Unrestricted' so the app can sync in background.\n\n")
        if (!hasBackgroundHC) message.append("• Health Connect: Enable 'Background Access' in app permissions.")

        AlertDialog.Builder(this)
            .setTitle("Auto-Sync Requirements")
            .setMessage(message.toString())
            .setPositiveButton("Configure") { _, _ ->
                if (!hasBatteryOpt) openBatterySettings() else handleHealthConnectButtonClick()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Search for Battery Optimization in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkForChanges() {
        val currentApiKey = etApiKey.text.toString().trim()
        val currentAthleteId = etAthleteId.text.toString().trim()
        val currentCoachWattsToken = etCoachWattsToken.text.toString().trim()
        
        val currentAutoSync = switchAutoSync.isChecked
        val currentCustomRestingHR = switchCustomRestingHR.isChecked
        val currentRetentionDays = sliderRetention.value.toInt()
        val currentSyncTime1 = btnSyncTime1.text.toString()
        val currentSyncTime2 = btnSyncTime2.text.toString()
        val currentSyncTime2Enabled = switchSyncTime2.isChecked

        val hasChanged = currentApiKey != initialApiKey ||
                currentAthleteId != initialAthleteId ||
                currentCoachWattsToken != initialCoachWattsToken ||
                currentAutoSync != initialAutoSync ||
                currentCustomRestingHR != initialCustomRestingHR ||
                currentRetentionDays != initialRetentionDays ||
                currentSyncTime1 != initialSyncTime1 ||
                currentSyncTime2 != initialSyncTime2 ||
                currentSyncTime2Enabled != initialSyncTime2Enabled

        // Valid if at least one service has credentials or it's not the first setup
        val anyCredentials = (currentApiKey.isNotEmpty() && currentAthleteId.isNotEmpty()) || currentCoachWattsToken.isNotEmpty()
        
        btnFooterSave.isEnabled = hasChanged && anyCredentials
    }

    private fun getBasePermissions(): Set<String> {
        return (HealthConnectManager.PERMISSIONS + ExtendedHealthConnectManager.EXTENDED_PERMISSIONS).map { it.toString() }.toSet()
    }

    private fun handleHealthConnectButtonClick() {
        lifecycleScope.launch {
            try {
                val healthConnectClient = HealthConnectClient.getOrCreate(this@SetupActivity)
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                val baseRequired = getBasePermissions()
                
                if (granted.containsAll(baseRequired)) {
                    openHealthConnectSettings()
                } else {
                    requestPermissions.launch(baseRequired)
                }
            } catch (e: Exception) {
                openHealthConnectSettings()
            }
        }
    }

    private fun openHealthConnectSettings() {
        // Try specific app permission management first
        val actions = listOf(
            "android.intent.action.VIEW_PERMISSION_USAGE", // Android 14+ direct way
            "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS",
            "androidx.health.action.MANAGE_HEALTH_PERMISSIONS",
            HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
        )
        
        for (action in actions) {
            try {
                val intent = Intent(action)
                if (action == "android.intent.action.VIEW_PERMISSION_USAGE") {
                    intent.addCategory("android.intent.category.HEALTH_PERMISSIONS")
                } else if (action.contains("MANAGE_HEALTH_PERMISSIONS")) {
                    intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
                return
            } catch (e: Exception) { /* continue */ }
        }
        
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Open Health Connect from System Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateHealthConnectButton(baseGrantedCount: Int, totalCount: Int) {
        btnHealthConnect.isEnabled = true
        btnHealthConnect.text = if (baseGrantedCount >= totalCount) "Manage Permissions" else "Grant Permissions"
    }

    private fun finishSetup() {
        val apiKey = etApiKey.text.toString().trim()
        val athleteId = etAthleteId.text.toString().trim()
        val cwToken = etCoachWattsToken.text.toString().trim()

        // Save Intervals credentials
        if (apiKey.isNotEmpty() && athleteId.isNotEmpty()) {
            credentialsManager.saveCredentials(apiKey, athleteId)
        }
        
        // Save CoachWatts credentials
        if (cwToken.isNotEmpty()) {
            credentialsManager.saveCoachWattsCredentials(cwToken)
        }

        preferencesManager.autoSyncEnabled = switchAutoSync.isChecked
        preferencesManager.useCustomRestingHR = switchCustomRestingHR.isChecked
        preferencesManager.cacheRetentionDays = sliderRetention.value.toInt()
        preferencesManager.syncTime1 = btnSyncTime1.text.toString()
        preferencesManager.syncTime2 = btnSyncTime2.text.toString()
        preferencesManager.syncTime2Enabled = switchSyncTime2.isChecked
        
        if (switchAutoSync.isChecked) {
            // Use REPLACE to ensure new settings are applied immediately to the background schedule
            SyncWorker.scheduleNext(this, ExistingWorkPolicy.REPLACE)
        } else {
            SyncWorker.cancelSync(this)
        }

        Toast.makeText(this, "✓ Settings updated!", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            delay(100)
            if (isTaskRoot) startActivity(Intent(this@SetupActivity, MainActivity::class.java))
            finish()
        }
    }
}
