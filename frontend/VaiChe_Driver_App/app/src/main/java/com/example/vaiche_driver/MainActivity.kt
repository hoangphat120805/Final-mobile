package com.example.vaiche_driver

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.vaiche_driver.adapter.BottomNavHelper
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.fragment.DashboardFragment
import com.example.vaiche_driver.fragment.ScheduleFragment
import com.example.vaiche_driver.fragment.SettingsFragment
import com.example.vaiche_driver.ui.ProfileFragment
import com.example.vaiche_driver.ui.SplashFragment
import com.example.vaiche_driver.viewmodel.SharedViewModel
import kotlinx.coroutines.launch

/**
 * MainActivity là "vỏ" chính của ứng dụng.
 * - Quản lý việc chuyển đổi giữa luồng Xác thực (Login/Register) và luồng Chính (Dashboard...).
 * - Quản lý thanh Bottom Navigation và cơ chế show/hide các Fragment chính.
 * - Là chủ sở hữu của SharedViewModel.
 */
class MainActivity : AppCompatActivity() {

    // Khởi tạo các Fragment chính MỘT LẦN DUY NHẤT bằng `lazy`
    private val dashboardFragment by lazy { DashboardFragment() }
    private val scheduleFragment by lazy { ScheduleFragment() }
    private val profileFragment by lazy { ProfileFragment() }

    private val settingsFragment by lazy { SettingsFragment() }

    private var activeFragment: Fragment? = null
    private lateinit var bottomNavView: View
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavView = findViewById(R.id.bottom_nav_include)

        if (savedInstanceState == null) {
            // Luôn bắt đầu bằng SplashFragment để kiểm tra trạng thái đăng nhập
            supportFragmentManager.commit {
                replace(R.id.fragment_container, SplashFragment())
            }
            // Ẩn thanh nav đi lúc đầu
            setBottomNavVisibility(false)
        }

        setupBottomNavigation()

        supportFragmentManager.addOnBackStackChangedListener {
            // Lấy Fragment đang hiển thị trên cùng
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            // Chỉ hiển thị nav nếu đó là một trong các màn hình chính
            val isMainScreen = currentFragment is DashboardFragment || currentFragment is ScheduleFragment
                    || currentFragment is ProfileFragment || currentFragment is SettingsFragment
            setBottomNavVisibility(isMainScreen)

            // Cập nhật màu sắc cho icon
            if (isMainScreen && currentFragment != null) {
                BottomNavHelper.updateIconColorsOnly(bottomNavView, getScreenForFragment(currentFragment))
            }
        }
    }

    private fun setupBottomNavigation() {
        BottomNavHelper.setup(bottomNavView, getScreenForFragment(activeFragment ?: dashboardFragment)) { screen ->
            val fragmentToShow = when (screen) {
                BottomNavScreen.DASHBOARD -> dashboardFragment
                BottomNavScreen.SCHEDULE -> scheduleFragment
                BottomNavScreen.PROFILE -> profileFragment
                BottomNavScreen.SETTINGS -> settingsFragment
                else -> null
            }
            fragmentToShow?.let { switchFragment(it) }
        }
    }

    /**
     * Hàm này được gọi từ LoginFragment sau khi đăng nhập thành công.
     * Nó sẽ thiết lập các Fragment chính của ứng dụng.
     */
    fun navigateToDashboard() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)

            if (supportFragmentManager.findFragmentByTag("DASH") == null) {
                add(R.id.fragment_container, dashboardFragment, "DASH")
            }
            if (supportFragmentManager.findFragmentByTag("SCH") == null) {
                add(R.id.fragment_container, scheduleFragment, "SCH").hide(scheduleFragment)
            }
            if (supportFragmentManager.findFragmentByTag("PRO") == null) {
                add(R.id.fragment_container, profileFragment, "PRO").hide(profileFragment)
            }
        }
        activeFragment = supportFragmentManager.findFragmentByTag("DASH")
        setBottomNavVisibility(true)
    }

    private fun switchFragment(fragment: Fragment) {
        if (fragment == activeFragment) return

        supportFragmentManager.commit {
            if (activeFragment != null) {
                hide(activeFragment!!)
            }
            show(fragment)
        }
        activeFragment = fragment

        BottomNavHelper.updateIconColorsOnly(bottomNavView, getScreenForFragment(fragment))
    }

    private fun getScreenForFragment(fragment: Fragment): BottomNavScreen {
        return when(fragment) {
            is DashboardFragment -> BottomNavScreen.DASHBOARD
            is ScheduleFragment -> BottomNavScreen.SCHEDULE
            is ProfileFragment -> BottomNavScreen.PROFILE
            is SettingsFragment -> BottomNavScreen.SETTINGS
            else -> BottomNavScreen.DASHBOARD
        }
    }

    private fun setBottomNavVisibility(isVisible: Boolean) {
        bottomNavView.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
}