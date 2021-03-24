package com.tangem.tangem_demo.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.tangem.tangem_demo.DemoApplication


/**
[REDACTED_AUTHOR]
 */
class SettingsFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(createThemeSwitcher())
        preferenceScreen = screen
    }

    private fun createThemeSwitcher():Preference{
        val themeSwitch = SwitchPreferenceCompat(context)
        themeSwitch.key = nightMode
        themeSwitch.title = "Enable night mode"
        themeSwitch.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val value = newValue as? Boolean ?: return@OnPreferenceChangeListener false

            val appContext = requireActivity().applicationContext as DemoApplication
            appContext.switchToNighMode(value)
            return@OnPreferenceChangeListener true
        }
        return themeSwitch
    }

    companion object {
        val nightMode = "nightMode"
    }
}