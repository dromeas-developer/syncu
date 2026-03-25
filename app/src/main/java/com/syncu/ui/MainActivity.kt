package com.syncu.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.syncu.R
import com.syncu.sync.SyncWorker
import com.syncu.data.AppDatabase
import com.syncu.utils.PreferencesManager
import com.syncu.utils.SecureCredentialsManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main Activity - Swipeable Day View
 * Handles date transitions, credentials check, and background sync scheduling
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dayPagerAdapter: DayPagerAdapter
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvLastSync: TextView
    
    private var adapterBaseDate = LocalDate.now()
    private var currentRetentionDays = 14
    
    private lateinit var btnFooterToday: Button
    private lateinit var btnFooterSync: Button
    private lateinit var btnFooterSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        preferencesManager = PreferencesManager(this)
        currentRetentionDays = preferencesManager.cacheRetentionDays
        adapterBaseDate = LocalDate.now()
        
        if (!isSetupCompleteSync()) {
            redirectToSetup()
            return
        }
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvLastSync = findViewById(R.id.tvLastSync)
        
        setupFooter()
        setupViewPager()
        setupSystemBars()
        
        // Ensure background sync is scheduled according to latest settings
        if (preferencesManager.autoSyncEnabled) {
            SyncWorker.scheduleNext(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val today = LocalDate.now()
        val settingsRetention = preferencesManager.cacheRetentionDays
        
        // 1. Check for midnight transition or settings change
        if (today != adapterBaseDate || settingsRetention != currentRetentionDays) {
            Log.i("SyncU_Data", "Date or settings changed. Rebuilding ViewPager. OldDate: $adapterBaseDate, NewDate: $today")
            adapterBaseDate = today
            currentRetentionDays = settingsRetention
            setupViewPager()
        } else {
            // 2. Just refresh current page labels and sync status
            val position = viewPager.currentItem
            val date = dayPagerAdapter.getDateForPosition(position)
            updateHeaderDate(date, tvHeaderTitle)
            updateLastSyncTimestamp(date)
            
            val currentFragment = supportFragmentManager.findFragmentByTag("f$position")
            if (currentFragment is DayFragment) {
                currentFragment.refreshSyncButtonStatus()
                // Auto-refresh today's data if we've returned to the app
                if (date == today) {
                    currentFragment.refreshData()
                }
            }
        }
        
        // 3. Keep scheduling heartbeat alive
        if (preferencesManager.autoSyncEnabled) {
            SyncWorker.scheduleNext(this)
        }
    }

    private fun isSetupCompleteSync(): Boolean {
        val credentialsManager = SecureCredentialsManager(this)
        return credentialsManager.hasCredentials()
    }

    private fun redirectToSetup() {
        if (isFinishing) return
        val intent = Intent(this, SetupActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupFooter() {
        btnFooterToday = findViewById(R.id.btnFooterToday)
        btnFooterSync = findViewById(R.id.btnFooterSync)
        btnFooterSettings = findViewById(R.id.btnFooterSettings)

        btnFooterToday.setOnClickListener {
            viewPager.setCurrentItem(dayPagerAdapter.itemCount - 1, true)
        }

        btnFooterSync.setOnClickListener {
            syncCurrentDay()
        }

        btnFooterSettings.setOnClickListener {
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        dayPagerAdapter = DayPagerAdapter(this, adapterBaseDate, currentRetentionDays)
        viewPager.adapter = dayPagerAdapter
        // Always start at "Today" (the last item in the range)
        viewPager.setCurrentItem(dayPagerAdapter.itemCount - 1, false)
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val date = dayPagerAdapter.getDateForPosition(position)
                updateHeaderDate(date, tvHeaderTitle)
                updateFooterButtons(date)
                updateLastSyncTimestamp(date)
            }
        })
    }

    private fun setupSystemBars() {
        val rootLayout = findViewById<View>(R.id.viewPager).parent as ViewGroup
        val headerLayout = rootLayout.getChildAt(0)
        val footerLayout = rootLayout.getChildAt(3)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerLayout.updatePadding(top = systemBars.top)
            footerLayout.updatePadding(bottom = systemBars.bottom)
            view.updatePadding(left = systemBars.left, right = systemBars.right)
            insets
        }
    }

    private fun updateHeaderDate(date: LocalDate, tvHeaderTitle: TextView) {
        if (date == LocalDate.now()) {
            tvHeaderTitle.text = "Today"
        } else {
            val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
            tvHeaderTitle.text = date.format(formatter)
        }
    }

    private fun updateFooterButtons(date: LocalDate) {
        val isToday = date == LocalDate.now()
        btnFooterToday.isEnabled = !isToday
        btnFooterToday.alpha = if (!isToday) 1.0f else 0.5f
        
        val currentFragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
        if (currentFragment is DayFragment) {
            currentFragment.refreshSyncButtonStatus()
        } else {
            setSyncButtonEnabled(false)
        }
    }

    fun setSyncButtonEnabled(enabled: Boolean) {
        if (::btnFooterSync.isInitialized) {
            btnFooterSync.isEnabled = enabled
            btnFooterSync.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    private fun syncCurrentDay() {
        val currentFragment = supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
        if (currentFragment is DayFragment) {
            currentFragment.syncDataToIntervals()
        } else {
            Log.w("MainActivity", "Could not find active DayFragment to sync")
            Toast.makeText(this, "Please wait for page to load", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun setLastSyncText(text: String) {
        if (::tvLastSync.isInitialized) {
            tvLastSync.text = text
        }
    }

    fun updateLastSyncTimestamp(date: LocalDate) {
        lifecycleScope.launch {
            try {
                val database = AppDatabase.getDatabase(applicationContext, charArrayOf())
                val intRecord = database.intervalsDao().getWellnessForDate(date)
                val cwRecord = database.coachWattsDao().getWellnessForDate(date)
                
                val intSync = intRecord?.lastSyncedAt
                val cwSync = cwRecord?.lastSyncedAt
                
                // Find the most recent sync time among all services
                val latestSync = listOfNotNull(intSync, cwSync).maxOrNull()
                
                if (latestSync != null) {
                    val dateTime = latestSync.atZone(ZoneId.systemDefault()).toLocalDateTime()
                    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
                    setLastSyncText("Last sync: ${dateTime.format(formatter)}")
                } else {
                    setLastSyncText("Last sync: Never")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating last sync timestamp", e)
            }
        }
    }
}

class DayPagerAdapter(
    activity: FragmentActivity,
    private val today: LocalDate,
    private val retentionDays: Int
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = retentionDays + 1

    override fun createFragment(position: Int): Fragment {
        val date = getDateForPosition(position)
        return DayFragment.newInstance(date)
    }

    fun getDateForPosition(position: Int): LocalDate {
        val offset = position - retentionDays
        return today.plusDays(offset.toLong())
    }
}
