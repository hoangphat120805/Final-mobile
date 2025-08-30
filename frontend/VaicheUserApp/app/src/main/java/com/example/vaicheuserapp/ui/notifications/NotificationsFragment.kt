package com.example.vaicheuserapp.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vaicheuserapp.databinding.FragmentNotificationsBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.example.vaicheuserapp.R

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
    }

    private fun setupTabs() {
        val viewPagerAdapter = ViewPagerAdapter(childFragmentManager, lifecycle)
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = viewPagerAdapter.getTabTitle(position)
            // Custom tab views for selection effect would go here if needed
            // For now, default TabLayout styling will apply
        }.attach()

        // Optional: Style the tabs
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                // Change background or text color for selected tab indicator
                tab.view.setBackgroundResource(R.drawable.bg_tab_selected) // Define bg_tab_selected.xml
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.view.setBackgroundResource(R.drawable.bg_tab_unselected) // Define bg_tab_unselected.xml
            }
            override fun onTabReselected(tab: TabLayout.Tab) { /* Do nothing */ }
        })
        // Initially select the first tab and apply its background
        binding.tabLayout.getTabAt(0)?.select()
        binding.tabLayout.getTabAt(0)?.view?.setBackgroundResource(R.drawable.bg_tab_selected)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}