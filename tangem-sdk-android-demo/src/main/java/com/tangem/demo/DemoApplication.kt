package com.tangem.demo

import android.app.Application
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.tangem.demo.ui.settings.SettingsFragment

/**
[REDACTED_AUTHOR]
 */
class DemoApplication : Application() {

    lateinit var shPrefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()

        shPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        switchToNighMode(isNightModeActive())
    }

    fun isNightModeActive(): Boolean {
        return shPrefs.getBoolean(SettingsFragment.NIGHT_MODE, false)
    }

    fun switchToNighMode(switch: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (switch) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}