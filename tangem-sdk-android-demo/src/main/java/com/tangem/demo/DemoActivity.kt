package com.tangem.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.tangem.Log
import com.tangem.SessionViewDelegate
import com.tangem.TangemSdk
import com.tangem.demo.ui.separtedCommands.CommandListFragment
import com.tangem.demo.ui.settings.SettingsFragment
import com.tangem.demo.ui.viewDelegate.ViewDelegateFragment
import com.tangem.tangem_demo.databinding.ActivityDemoBinding

class DemoActivity : AppCompatActivity() {

    private val container: DependencyContainer
        get() = (application as DemoApplication).dependencyContainer

    val logger get() = container.logger

    lateinit var sdk: TangemSdk
        private set
    lateinit var viewDelegate: SessionViewDelegate
        private set

    lateinit var binding: ActivityDemoBinding

    private val pageChangeListeners = mutableListOf<(Int) -> Unit>()
    private val fragmentPages = listOf(
        CommandListFragment::class.java,
        ViewDelegateFragment::class.java,
        SettingsFragment::class.java,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        sdk = container.getSdk(this)
        viewDelegate = container.viewDelegate

        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        Log.addLogger(logger)
        binding.viewPager.adapter = ViewPagerAdapter(fragmentPages, this)
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    pageChangeListeners.forEach { it.invoke(position) }
                }
            },
        )
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = fragmentPages[position].simpleName.replace("Fragment", "")
        }.attach()
    }

    fun listenPageChanges(callback: (Int) -> Unit) {
        pageChangeListeners.add(callback)
    }

    fun enableSwipe(enable: Boolean) {
        binding.viewPager.isUserInputEnabled = enable
    }

    class ViewPagerAdapter(
        private val fgList: List<Class<out Fragment>>,
        fgActivity: FragmentActivity,
    ) : FragmentStateAdapter(fgActivity) {

        override fun getItemCount(): Int = fgList.size

        override fun createFragment(position: Int): Fragment = fgList[position].newInstance()
    }
}