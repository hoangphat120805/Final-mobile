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

    // ⚠️ chặn double-tap logout
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

        supportFragmentManager.addOnBackStackChangedListener {
            val top = supportFragmentManager.findFragmentById(R.id.fragment_container)
            val isMain = top is DashboardFragment || top is ScheduleFragment ||
                    top is ProfileFragment || top is NotificationsFragment
            setBottomNavVisibility(isMain && !isLoggingOut) // nếu đang logout thì vẫn ẩn
        }
    }

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
            setBottomNavVisibility(true && !isLoggingOut)
            BottomNavHelper.updateIconColorsOnly(bottomNavView, screen)
            return
        }

        supportFragmentManager.commit {
            listOfNotNull(dashboardFragment, scheduleFragment, profileFragment, notificationsFragment)
                .forEach { if (it.isAdded) hide(it) }
            show(fragmentToShow)
        }

        activeFragment = fragmentToShow
        setBottomNavVisibility(true && !isLoggingOut)
        BottomNavHelper.updateIconColorsOnly(bottomNavView, screen)
    }

    private fun setBottomNavVisibility(isVisible: Boolean) {
        bottomNavView.visibility = if (isVisible) View.VISIBLE else View.GONE
        bottomNavView.isEnabled = isVisible
    }

    /** Logout mềm: ẩn bottom bar ngay lập tức, dọn session/cache, xoá fragment cũ, về Login */
    fun logoutToLogin() {
        if (isLoggingOut) return
        isLoggingOut = true

        // Ẩn & khoá bottom bar NGAY khi bấm logout
        setBottomNavVisibility(false)

        lifecycleScope.launch {
            // 1) dọn session/cache/cookies + reset Retrofit
            SessionCleaner.hardLogout(applicationContext)

            // 2) xoá ViewModels của Activity
            viewModelStore.clear()

            // 3) remove toàn bộ main fragments để khỏi reuse instance cũ
            supportFragmentManager.commit {
                listOfNotNull(dashboardFragment, scheduleFragment, profileFragment, notificationsFragment)
                    .forEach { if (it.isAdded) remove(it) }
            }

            // 4) bỏ reference để lần sau tạo lại fragment mới
            dashboardFragment = null
            scheduleFragment = null
            profileFragment = null
            notificationsFragment = null
            activeFragment = null

            // 5) clear back stack & về Login
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()

            // vẫn để ẩn bottom bar ở màn Login
            // nếu có case hủy logout giữa chừng, nhớ bật lại:
            isLoggingOut = false
            setBottomNavVisibility(false)
        }
    }

    fun hideBottomNavForAuthScreens() {
        setBottomNavVisibility(false)
    }
}
