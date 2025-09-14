package com.example.vaiche_driver

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.vaiche_driver.adapter.BottomNavHelper
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.data.local.SessionCleaner
import com.example.vaiche_driver.fragment.*
import com.example.vaiche_driver.viewmodel.SharedViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var dashboardFragment: DashboardFragment? = null
    private var scheduleFragment: ScheduleFragment? = null
    private var profileFragment: ProfileFragment? = null
    private var notificationsFragment: NotificationsFragment? = null

    private var activeFragment: Fragment? = null
    private lateinit var bottomNavView: View
    private val sharedViewModel: SharedViewModel by viewModels()

    private var isLoggingOut = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavView = findViewById(R.id.bottom_nav_include)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, SplashFragment())
            }
            setBottomNavVisibility(false)
        }

        setupBottomNavigation()

        // ===== Auto ẩn/hiện nav theo fragment đang RESUME =====
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (isLoggingOut) {
                        setBottomNavVisibility(false)
                        return
                    }
                    val showOnMain = f is DashboardFragment ||
                            f is ScheduleFragment  ||
                            f is ProfileFragment   ||
                            f is NotificationsFragment
                    setBottomNavVisibility(showOnMain)
                }
            },
            true
        )
    }

    // ===== Main flow =====

    private fun ensureMainFragmentsAdded() {
        if (dashboardFragment == null) dashboardFragment = DashboardFragment()
        if (scheduleFragment == null) scheduleFragment = ScheduleFragment()
        if (profileFragment == null) profileFragment = ProfileFragment()
        if (notificationsFragment == null) notificationsFragment = NotificationsFragment()

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            if (!dashboardFragment!!.isAdded) add(R.id.fragment_container, dashboardFragment!!, "DASH").hide(dashboardFragment!!)
            if (!scheduleFragment!!.isAdded) add(R.id.fragment_container, scheduleFragment!!, "SCH").hide(scheduleFragment!!)
            if (!profileFragment!!.isAdded) add(R.id.fragment_container, profileFragment!!, "PRO").hide(profileFragment!!)
            if (!notificationsFragment!!.isAdded) add(R.id.fragment_container, notificationsFragment!!, "NO").hide(notificationsFragment!!)
        }
    }

    private fun setupBottomNavigation() {
        BottomNavHelper.setup(
            bottomNavView,
            BottomNavScreen.DASHBOARD
        ) { screen ->
            ensureMainFragmentsAdded()
            val fragmentToShow = when (screen) {
                BottomNavScreen.DASHBOARD     -> dashboardFragment!!
                BottomNavScreen.SCHEDULE      -> scheduleFragment!!
                BottomNavScreen.PROFILE       -> profileFragment!!
                BottomNavScreen.NOTIFICATIONS -> notificationsFragment!!
                else                          -> dashboardFragment!!
            }
            showMainFragment(fragmentToShow, screen)
        }
    }

    /** Gọi sau khi login thành công (hoặc Splash xác nhận đã login) */
    fun navigateToDashboard() {
        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { f ->
            if (f is SplashFragment) {
                supportFragmentManager.commit { remove(f) }
                supportFragmentManager.executePendingTransactions()
            }
        }

        ensureMainFragmentsAdded()

        supportFragmentManager.commit {
            hide(scheduleFragment!!)
            hide(profileFragment!!)
            hide(notificationsFragment!!)
            show(dashboardFragment!!)
        }
        activeFragment = dashboardFragment

        setBottomNavVisibility(true)
        BottomNavHelper.updateIconColorsOnly(bottomNavView, BottomNavScreen.DASHBOARD)

        sharedViewModel.syncDriverStateOnLaunch()
    }

    fun selectMainTab(screen: BottomNavScreen, clearBackStack: Boolean = true) {
        if (clearBackStack) {
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.executePendingTransactions()
        }

        ensureMainFragmentsAdded()

        val fragmentToShow = when (screen) {
            BottomNavScreen.DASHBOARD     -> dashboardFragment!!
            BottomNavScreen.SCHEDULE      -> scheduleFragment!!
            BottomNavScreen.PROFILE       -> profileFragment!!
            BottomNavScreen.NOTIFICATIONS -> notificationsFragment!!
            else                          -> dashboardFragment!!
        }

        showMainFragment(fragmentToShow, screen)
    }

    private fun showMainFragment(fragmentToShow: Fragment, screen: BottomNavScreen) {
        if (fragmentToShow == activeFragment) {
            setBottomNavVisibility(true)
            BottomNavHelper.updateIconColorsOnly(bottomNavView, screen)
            return
        }

        supportFragmentManager.commit {
            listOfNotNull(dashboardFragment, scheduleFragment, profileFragment, notificationsFragment)
                .forEach { if (it.isAdded) hide(it) }
            show(fragmentToShow)
        }

        activeFragment = fragmentToShow
        setBottomNavVisibility(true)
        BottomNavHelper.updateIconColorsOnly(bottomNavView, screen)
    }

    private fun setBottomNavVisibility(isVisible: Boolean) {
        bottomNavView.visibility = if (isVisible) View.VISIBLE else View.GONE
        bottomNavView.isEnabled = isVisible
        bottomNavView.alpha = if (isVisible) 1f else 0f
        bottomNavView.translationY = if (isVisible) 0f else bottomNavView.height.toFloat()
    }

    // ===== Logout mềm không kill app =====
    fun logoutToLogin() {
        if (isLoggingOut) return
        isLoggingOut = true

        setBottomNavVisibility(false)

        lifecycleScope.launch {
            SessionCleaner.hardLogout(applicationContext)
            viewModelStore.clear()

            supportFragmentManager.commit {
                listOfNotNull(dashboardFragment, scheduleFragment, profileFragment, notificationsFragment)
                    .forEach { if (it.isAdded) remove(it) }
            }

            dashboardFragment = null
            scheduleFragment = null
            profileFragment = null
            notificationsFragment = null
            activeFragment = null

            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()

            setBottomNavVisibility(false)
            isLoggingOut = false
        }
    }
}
