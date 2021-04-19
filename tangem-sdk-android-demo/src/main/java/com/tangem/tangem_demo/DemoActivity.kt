package com.tangem.tangem_demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.tangem.tangem_demo.ui.separtedCommands.CommandListFragment
import com.tangem.tangem_demo.ui.settings.SettingsFragment
import com.tangem.tangem_demo.ui.tasksLogger.SdkTaskSpinnerFragment
import kotlinx.android.synthetic.main.activity_demo.*

class DemoActivity : AppCompatActivity() {

    private val pageChangeListeners = mutableListOf<(Int) -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        viewPager.adapter = ViewPagerAdapter(this)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageChangeListeners.forEach { it.invoke(position) }
            }
        })
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Command list"
                1 -> "Command tester"
                2 -> "Settings"
                else -> ""
            }
        }.attach()

    }

    fun listenPageChanges(callback: (Int) -> Unit) {
        pageChangeListeners.add(callback)
    }

    fun enableSwipe(enable: Boolean) {
        viewPager.isUserInputEnabled = enable
    }

    class ViewPagerAdapter(fgActivity: FragmentActivity) : FragmentStateAdapter(fgActivity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> CommandListFragment()
                1 -> SdkTaskSpinnerFragment()
                2 -> SettingsFragment()
                else -> EmptyFragment()
            }
        }

        class EmptyFragment : Fragment()
    }
}