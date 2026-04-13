package com.apkforge.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.apkforge.R
import com.apkforge.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * APK Forge — Main Activity (Kotlin)
 * Single-activity architecture with Navigation Component
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        appBarConfig = AppBarConfiguration(
            setOf(R.id.dashboardFragment, R.id.buildFragment,
                  R.id.historyFragment, R.id.settingsFragment)
        )

        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on nested screens
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.bottomNav.visibility = when (dest.id) {
                R.id.dashboardFragment, R.id.buildFragment,
                R.id.historyFragment,   R.id.settingsFragment ->
                    android.view.View.VISIBLE
                else -> android.view.View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
}
