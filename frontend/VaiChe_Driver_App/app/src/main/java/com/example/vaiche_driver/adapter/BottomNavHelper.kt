package com.example.vaiche_driver.adapter

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.vaiche_driver.R

/**
 * Enum để định danh các màn hình chính có thể điều hướng từ Bottom Nav.
 */
enum class BottomNavScreen {
    DASHBOARD,
    SCHEDULE,
    PROFILE,
    NOTIFICATIONS
}

/**
 * Object này là một helper (công cụ hỗ trợ) để quản lý logic
 * của thanh điều hướng dưới cùng, bao gồm cả sự kiện click và cập nhật màu sắc.
 * Nó được thiết kế để tách biệt logic của View ra khỏi Activity/Fragment.
 */
object BottomNavHelper {

    /**
     * Thiết lập các sự kiện click và màu sắc ban đầu cho thanh điều hướng.
     * @param rootView View gốc chứa thanh điều hướng (thường là view của Activity).
     * @param currentScreenForInitialColor Màn hình hiện tại để tô màu đúng icon lúc ban đầu.
     * @param onScreenSelected Một hàm (lambda) sẽ được gọi khi một icon được nhấn,
     *                         trả về enum của màn hình được chọn.
     */
    fun setup(
        rootView: View,
        currentScreenForInitialColor: BottomNavScreen,
        onScreenSelected: (BottomNavScreen) -> Unit
    ) {
        // Cập nhật màu sắc cho lần hiển thị đầu tiên
        updateIconColorsOnly(rootView, currentScreenForInitialColor)

        // Gán sự kiện click cho từng container, gọi callback khi được nhấn
        rootView.findViewById<LinearLayout>(R.id.btnDashboardContainer).setOnClickListener {
            onScreenSelected(BottomNavScreen.DASHBOARD)
        }
        rootView.findViewById<LinearLayout>(R.id.btnScheduleContainer).setOnClickListener {
            onScreenSelected(BottomNavScreen.SCHEDULE)
        }
        rootView.findViewById<LinearLayout>(R.id.btnProfileContainer).setOnClickListener {
            onScreenSelected(BottomNavScreen.PROFILE)
        }
        rootView.findViewById<LinearLayout>(R.id.btnSettingsContainer).setOnClickListener {
            onScreenSelected(BottomNavScreen.NOTIFICATIONS)
        }
    }

    /**
     * Một hàm công khai chỉ để cập nhật màu sắc của các icon.
     * MainActivity sẽ gọi hàm này mỗi khi chuyển đổi Fragment thành công.
     */
    fun updateIconColorsOnly(rootView: View, currentScreen: BottomNavScreen) {
        val context = rootView.context

        // Tìm các ImageButton
        val btnDashboard = rootView.findViewById<ImageButton>(R.id.btnDashboard)
        val btnSchedule = rootView.findViewById<ImageButton>(R.id.btnSchedule)
        val btnProfile = rootView.findViewById<ImageButton>(R.id.btnProfile)
        //val btnNotification = rootView.findViewById<ImageButton>(R.id.btnNotification)

        // Lấy màu từ resources
        val activeColor = ContextCompat.getColor(context, R.color.bottom_nav_active)
        val inactiveColor = ContextCompat.getColor(context, R.color.bottom_nav_inactive)

        // Cập nhật màu sắc dựa trên màn hình hiện tại
        btnDashboard.setColorFilter(if (currentScreen == BottomNavScreen.DASHBOARD) activeColor else inactiveColor)
        btnSchedule.setColorFilter(if (currentScreen == BottomNavScreen.SCHEDULE) activeColor else inactiveColor)
        btnProfile.setColorFilter(if (currentScreen == BottomNavScreen.PROFILE) activeColor else inactiveColor)
        //btnNotification.setColorFilter(if (currentScreen == BottomNavScreen.NOTIFICATIONS) activeColor else inactiveColor)
    }
}