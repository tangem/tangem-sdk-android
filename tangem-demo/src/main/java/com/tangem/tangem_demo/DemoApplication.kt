package com.tangem.tangem_demo

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.tangem.tangem_demo.ui.settings.SettingsFragment

/**
[REDACTED_AUTHOR]
 */
class DemoApplication : Application() {

    private lateinit var prefManager: SharedPreferences

    override fun onCreate() {
        super.onCreate()

        prefManager = PreferenceManager.getDefaultSharedPreferences(this)
        switchToNighMode(isNightModeActive())
    }

    fun isNightModeActive(): Boolean {
        return prefManager.getBoolean(SettingsFragment.nightMode, false)
    }

    fun switchToNighMode(switch: Boolean) {
        if (switch) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}