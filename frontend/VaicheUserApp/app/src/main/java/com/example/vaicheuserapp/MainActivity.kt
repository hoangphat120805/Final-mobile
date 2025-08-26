package com.example.vaicheuserapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.vaicheuserapp.databinding.ActivityMainBinding // Ensure this is correct
import com.example.vaicheuserapp.ui.dashboard.HomeFragment
import com.example.vaicheuserapp.ScrapDetailActivity // Still launched as Activity
import com.example.vaicheuserapp.ui.profile.ProfileFragment // Now a fragment
import com.example.vaicheuserapp.ui.sell.SetPlanFragment // New fragment for sell flow
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController // NavController for fragment transitions
    private val navIconMap = mutableMapOf<Int, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Get the NavController from the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // 2. Setup listeners for the custom bottom navigation bar
        setupCustomBottomNavigation()
    }

    private fun setupCustomBottomNavigation() {
        // Populate the map with Nav Destination IDs and their ImageView references
        navIconMap[R.id.nav_home_fragment] = binding.root.findViewById(R.id.iv_home)
        navIconMap[R.id.nav_notifications_fragment] = binding.root.findViewById(R.id.iv_notifications)
        navIconMap[R.id.nav_history_fragment] = binding.root.findViewById(R.id.iv_history)
        navIconMap[R.id.nav_profile_fragment] = binding.root.findViewById(R.id.iv_profile)

        // Set up click listeners for each icon
        navIconMap.forEach { (navId, imageView) ->
            imageView.setOnClickListener {
                navController.navigate(navId)
            }
        }

        // Set up click listener for the central Sell button (it's not part of the selection logic)
        binding.root.findViewById<TextView>(R.id.fab_sell).setOnClickListener {
            Log.d("MainActivity", "Sell button clicked. Navigating to Set Plan Fragment.")
            navController.navigate(R.id.set_plan_fragment)
        }

        // --- NEW: Listen for destination changes to update icon selection ---
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavSelection(destination.id)
        }

        // Set initial selection when the activity is created (e.g., if Home is start destination)
        updateBottomNavSelection(navController.currentDestination?.id ?: R.id.nav_home_fragment)
    }

    private fun updateBottomNavSelection(selectedDestinationId: Int) {
        navIconMap.forEach { (navId, imageView) ->
            val tintColor = if (navId == selectedDestinationId) {
                // Selected state: white
                ContextCompat.getColor(this, R.color.white)
            } else {
                // Unselected state: muted white
                ContextCompat.getColor(this, R.color.gray)
            }
            imageView.setColorFilter(tintColor) // Apply the tint
        }
    }
}