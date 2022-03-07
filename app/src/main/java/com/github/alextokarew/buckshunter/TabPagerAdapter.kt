package com.github.alextokarew.buckshunter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class TabPagerAdapter(fm: FragmentManager, lifecycle: Lifecycle, private var tabCount: Int) : FragmentStateAdapter(fm, lifecycle) {
    override fun getItemCount(): Int {
        return tabCount
    }

    override fun createFragment(position: Int): Fragment {
        when (position) {
            0 -> return SettingsFragment()
            1 -> return LogsFragment()
            else -> return SettingsFragment()
        }
    }
}