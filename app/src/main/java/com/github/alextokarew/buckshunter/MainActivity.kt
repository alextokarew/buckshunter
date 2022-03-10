package com.github.alextokarew.buckshunter

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkManager
import com.google.android.gms.location.*
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)

        val fm = supportFragmentManager
        val sa = TabPagerAdapter(fm, lifecycle, tabLayout.tabCount)
        val vp2 = findViewById<ViewPager2>(R.id.viewPager2)
        vp2.adapter = sa

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                vp2.setCurrentItem(tab!!.position, true)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) { }

            override fun onTabReselected(tab: TabLayout.Tab?) { }
        })

        vp2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations){
                    //TODO maybe update some field in UI
                    Log.i("Location callback", location.toString())
                }
            }
        }
    }

    private val prefsChangeListener = object : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            Log.i("Prefs", "$key was changed")
            when (key) {
                resources.getString(R.string.pref_scan_enabled) -> if (sp.getBoolean(key, false)) {
                    launchPeriodicService(sp)
                } else {
                    stopPeriodicService()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Log.i("Prefs", "registering on change listener")
        prefs.registerOnSharedPreferenceChangeListener(prefsChangeListener)
    }

    override fun onPause() {
        super.onPause()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsChangeListener)
    }

    @SuppressLint("MissingPermission")
    private fun launchPeriodicService(prefs: SharedPreferences) {
        Log.i("Poll exec", "Starting periodic service")
        val delay: Long = prefs.getString(resources.getString(R.string.pref_period_min), "2")!!.toLong() * 60000
        ApiPollWorker.scheduleNextExecution(this, null)
        val locationRequest = LocationRequest.create()?.apply {
            interval = delay
            fastestInterval = delay / 2
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopPeriodicService() {
        WorkManager.getInstance(this).cancelAllWorkByTag(ApiPollWorker.TAG)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}