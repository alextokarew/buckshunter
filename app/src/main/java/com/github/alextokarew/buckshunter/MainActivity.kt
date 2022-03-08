package com.github.alextokarew.buckshunter

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.tabs.TabLayout
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }

    private val prefsChangeListener = object : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
            Log.i("Prefs", "$key was changed")
            when (key) {
                resources.getString(R.string.pref_scan_enabled) -> if (sp.getBoolean(key, false)) {
                    launchPeriodicService()
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

    private fun launchPeriodicService() {
        Log.i("Poll exec", "Starting periodic service")
        ApiPollWorker.scheduleNextExecution(this)
    }

    private fun stopPeriodicService() {
        WorkManager.getInstance(this).cancelAllWorkByTag(ApiPollWorker.TAG)
    }
}