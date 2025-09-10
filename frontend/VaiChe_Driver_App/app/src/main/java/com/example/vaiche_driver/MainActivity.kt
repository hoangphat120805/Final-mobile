package com.example.vaiche_driver

import android.os.Bundle
import android.util.Log
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
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.fragment.DashboardFragment
import com.example.vaiche_driver.fragment.LoginFragment
import com.example.vaiche_driver.fragment.NotificationsFragment
import com.example.vaiche_driver.fragment.ProfileFragment
import com.example.vaiche_driver.fragment.ScheduleFragment
import com.example.vaiche_driver.fragment.SplashFragment
import com.example.vaiche_driver.viewmodel.SharedViewModel
import kotlinx.coroutines.launch
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import kotlin.system.exitProcess

/**
 * MainActivity là "vỏ" chính của ứng dụng.
 * - Quản lý việc chuyển đổi giữa luồng Xác thực (Login/Register) và luồng Chính (Dashboard...).
 * - Quản lý thanh Bottom Navigation và cơ chế show/hide các Fragment chính.
 * - Là chủ sở hữu của SharedViewModel.
 */
class MainActivity : AppCompatActivity() {

    private val dashboardFragment by lazy { DashboardFragment() }
    private val scheduleFragment by lazy { ScheduleFragment() }
    private val profileFragment by lazy { ProfileFragment() }
    private val notificationsFragment by lazy { NotificationsFragment() }

    private var activeFragment: Fragment? = null
    private lateinit var bottomNavView: View
    private val sharedViewModel: SharedViewModel by viewModels()

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

        // Chỉ ẩn/hiện nav theo fragment top của container.
        // KHÔNG cập nhật highlight icon ở đây (tránh ghi đè sai).
        supportFragmentManager.addOnBackStackChangedListener {
            val top = supportFragmentManager.findFragmentById(R.id.fragment_container)
            val isMain = top is DashboardFragment || top is ScheduleFragment ||
                    top is ProfileFragment || top is NotificationsFragment
            setBottomNavVisibility(isMain)
            // Không gọi updateIconColorsOnly ở đây nữa.
        }
    }

    private fun ensureMainFragmentsAdded() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            if (!dashboardFragment.isAdded) {
                add(R.id.fragment_container, dashboardFragment, "DASH").hide(dashboardFragment)
            }
            if (!scheduleFragment.isAdded) {
                add(R.id.fragment_container, scheduleFragment, "SCH").hide(scheduleFragment)
            }
            if (!profileFragment.isAdded) {
                add(R.id.fragment_container, profileFragment, "PRO").hide(profileFragment)
            }
            if (!notificationsFragment.isAdded) {
                add(R.id.fragment_container, notificationsFragment, "NO").hide(notificationsFragment)
            }
        }
    }

    private fun setupBottomNavigation() {
        // tab mặc định là DASHBOARD
        BottomNavHelper.setup(
            bottomNavView,
            BottomNavScreen.DASHBOARD
        ) { screen ->
            val fragmentToShow = when (screen) {
                BottomNavScreen.DASHBOARD     -> dashboardFragment
                BottomNavScreen.SCHEDULE      -> scheduleFragment
                BottomNavScreen.PROFILE       -> profileFragment
                BottomNavScreen.NOTIFICATIONS -> notificationsFragment
                else                          -> dashboardFragment
            }
            showMainFragment(fragmentToShow, screen)
        }
    }

    /** Được gọi sau login hoặc khi splash xác nhận đã login */
    fun navigateToDashboard() {
        // Gỡ Splash nếu còn
        supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { f ->
            if (f is SplashFragment) {
                supportFragmentManager.commit { remove(f) }
                // Quan trọng: hoàn tất giao dịch trước khi add/show các fragment khác
                supportFragmentManager.executePendingTransactions()
            }
        }

        ensureMainFragmentsAdded()

        // Show DASHBOARD, hide còn lại
        supportFragmentManager.commit {
            hide(scheduleFragment)
            hide(profileFragment)
            hide(notificationsFragment)
            show(dashboardFragment)
        }
        activeFragment = dashboardFragment
        setBottomNavVisibility(true)
        BottomNavHelper.updateIconColorsOnly(bottomNavView, BottomNavScreen.DASHBOARD)

        sharedViewModel.syncDriverStateOnLaunch()
    }


    /** API public: chọn tab từ fragment con */
    fun selectMainTab(screen: BottomNavScreen, clearBackStack: Boolean = true) {
        if (clearBackStack) {
            supportFragmentManager.popBackStack(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            supportFragmentManager.executePendingTransactions()
        }

        ensureMainFragmentsAdded()

        val fragmentToShow = when (screen) {
            BottomNavScreen.DASHBOARD     -> dashboardFragment
            BottomNavScreen.SCHEDULE      -> scheduleFragment
            BottomNavScreen.PROFILE       -> profileFragment
            BottomNavScreen.NOTIFICATIONS -> notificationsFragment
            else                          -> dashboardFragment
        }

        showMainFragment(fragmentToShow, screen)
    }


    /** Thao tác hide/show + cập nhật state + highlight */
    private fun showMainFragment(fragmentToShow: Fragment, screen: BottomNavScreen) {
        if (fragmentToShow == activeFragment) {
            // vẫn cập nhật highlight để chắc
            setBottomNavVisibility(true)
            BottomNavHelper.updateIconColorsOnly(bottomNavView, screen)
            return
        }

        supportFragmentManager.commit {
            // hide 4 cái để chắc
            if (dashboardFragment.isAdded) hide(dashboardFragment)
            if (scheduleFragment.isAdded) hide(scheduleFragment)
            if (profileFragment.isAdded) hide(profileFragment)
            if (notificationsFragment.isAdded) hide(notificationsFragment)

            show(fragmentToShow)
        }

        activeFragment = fragmentToShow
        setBottomNavVisibility(true)
        BottomNavHelper.updateIconColorsOnly(bottomNavView, screen)
    }

    private fun setBottomNavVisibility(isVisible: Boolean) {
        bottomNavView.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    // MainActivity.kt
    fun logoutToLogin() {
        lifecycleScope.launch {
            // (tuỳ bạn) gọi revoke token server trước:
            // runCatching { api.revokeToken() }

            SessionCleaner.hardLogout(applicationContext)

            // UI sạch: xoá ViewModel + back stack
            viewModelStore.clear()
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
        }
    }

}
