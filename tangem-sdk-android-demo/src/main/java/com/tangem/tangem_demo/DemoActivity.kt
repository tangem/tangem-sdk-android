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
import kotlinx.android.synthetic.main.activity_demo.*

class DemoActivity : AppCompatActivity() {

    private val pageChangeListeners = mutableListOf<(Int) -> Unit>()
    private val fragmentPages = listOf(
        CommandListFragment::class.java,
        SettingsFragment::class.java
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        viewPager.adapter = ViewPagerAdapter(fragmentPages,this)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageChangeListeners.forEach { it.invoke(position) }
            }
        })
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = fragmentPages[position].simpleName.replace("Fragment", "")
        }.attach()

    }

    fun listenPageChanges(callback: (Int) -> Unit) {
        pageChangeListeners.add(callback)
    }

    fun enableSwipe(enable: Boolean) {
        viewPager.isUserInputEnabled = enable
    }

    class ViewPagerAdapter(
        private val fgList: List<Class<out Fragment>>,
        fgActivity: FragmentActivity
    ) : FragmentStateAdapter(fgActivity) {

        override fun getItemCount(): Int = fgList.size

        override fun createFragment(position: Int): Fragment = fgList[position].newInstance()
    }
}