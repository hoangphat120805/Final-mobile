package com.example.vaiche_driver

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.vaiche_driver.adapter.BottomNavHelper
import com.example.vaiche_driver.adapter.BottomNavScreen
import com.example.vaiche_driver.adapter.BottomNavVisibilityManager
import com.example.vaiche_driver.fragment.DashboardFragment
import com.example.vaiche_driver.fragment.ScheduleFragment
import com.example.vaiche_driver.viewmodel.SharedViewModel

/**
 * MainActivity là "vỏ" chính của ứng dụng.
 * - Quản lý các Fragment chính bằng cơ chế show/hide để giữ lại trạng thái.
 * - Xử lý logic điều hướng của thanh Bottom Navigation.
 * - Cung cấp cơ chế để các Fragment con có thể ẩn/hiện Bottom Navigation.
 * - Là chủ sở hữu của SharedViewModel.
 */
class MainActivity : AppCompatActivity(), BottomNavVisibilityManager {

    // Khởi tạo các Fragment chính MỘT LẦN DUY NHẤT bằng `lazy`
    private val dashboardFragment by lazy { DashboardFragment() }
    private val scheduleFragment by lazy { ScheduleFragment() }
    // private val profileFragment by lazy { ProfileFragment() }
    // private val settingsFragment by lazy { SettingsFragment() }

    // Lưu lại Fragment đang hoạt động
    private var activeFragment: Fragment = dashboardFragment

    // Lưu tham chiếu đến thanh điều hướng để dễ dàng ẩn/hiện
    private lateinit var bottomNavView: View

    // Khởi tạo SharedViewModel ở cấp Activity.
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavView = findViewById(R.id.bottom_nav_include)

        // Chỉ thêm các Fragment vào lần đầu tiên Activity được tạo
        if (savedInstanceState == null) {
            setupInitialFragments()
        } else {
            // Khôi phục activeFragment sau khi thay đổi cấu hình (ví dụ: xoay màn hình)
            val activeTag = savedInstanceState.getString("ACTIVE_FRAGMENT_TAG", "1")
            activeFragment = supportFragmentManager.findFragmentByTag(activeTag) ?: dashboardFragment
        }

        setupBottomNavigation()
    }

    /**
     * Lưu lại tag của Fragment đang hoạt động trước khi Activity bị hủy.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("ACTIVE_FRAGMENT_TAG", activeFragment.tag)
    }

    /**
     * Thêm tất cả các Fragment chính vào FragmentManager, hide những cái không cần thiết,
     * và chỉ show Fragment mặc định.
     */
    private fun setupInitialFragments() {
        supportFragmentManager.beginTransaction().apply {
            // Add tất cả các fragment và gán tag cho chúng
            add(R.id.fragment_container, scheduleFragment, "2").hide(scheduleFragment)
            // add(R.id.fragment_container, profileFragment, "3").hide(profileFragment)
            // add(R.id.fragment_container, settingsFragment, "4").hide(settingsFragment)

            // Add và show fragment mặc định (Dashboard)
            add(R.id.fragment_container, dashboardFragment, "1")
        }.commit()
        activeFragment = dashboardFragment
    }

    /**
     * Thiết lập các sự kiện click cho thanh điều hướng.
     */
    private fun setupBottomNavigation() {
        BottomNavHelper.setup(bottomNavView, getScreenForFragment(activeFragment)) { screen ->
            // Khi một icon được nhấn, tìm Fragment tương ứng và gọi switchFragment
            val fragmentToShow = when (screen) {
                BottomNavScreen.DASHBOARD -> dashboardFragment
                BottomNavScreen.SCHEDULE -> scheduleFragment
                // BottomNavScreen.PROFILE -> profileFragment
                // BottomNavScreen.SETTINGS -> settingsFragment
                else -> null
            }
            fragmentToShow?.let { switchFragment(it) }
        }
    }

    /**
     * Hàm chính để chuyển đổi giữa các Fragment bằng show/hide.
     */
    private fun switchFragment(fragment: Fragment) {
        if (fragment == activeFragment) return // Không làm gì nếu đã ở màn hình đó

        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(fragment)
            .commit()
        activeFragment = fragment

        // Cập nhật lại màu sắc cho thanh nav sau khi chuyển đổi
        BottomNavHelper.updateIconColorsOnly(bottomNavView, getScreenForFragment(fragment))
    }

    /**
     * Hàm tiện ích để tìm ra enum Screen tương ứng với một Fragment.
     */
    private fun getScreenForFragment(fragment: Fragment): BottomNavScreen {
        return when(fragment) {
            is DashboardFragment -> BottomNavScreen.DASHBOARD
            is ScheduleFragment -> BottomNavScreen.SCHEDULE
            // is ProfileFragment -> BottomNavScreen.PROFILE
            // is SettingsFragment -> BottomNavScreen.SETTINGS
            else -> BottomNavScreen.DASHBOARD
        }
    }

    /**
     * --- THỰC THI INTERFACE ---
     * Hàm này được gọi từ các Fragment con (như OrderDetailFragment)
     * để ra lệnh cho Activity ẩn hoặc hiện thanh điều hướng.
     */
    override fun setBottomNavVisibility(isVisible: Boolean) {
        bottomNavView.visibility = if (isVisible) View.VISIBLE else View.GONE
    }
}