package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.example.vaiche_driver.MainActivity
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.AuthViewModel

/**
 * Fragment này là màn hình chờ (Splash Screen) đầu tiên của ứng dụng.
 * Nhiệm vụ chính của nó là kiểm tra trạng thái đăng nhập của người dùng và
 * điều hướng đến màn hình phù hợp (Login hoặc Dashboard).
 */
class SplashFragment : Fragment() {

    // Sử dụng activityViewModels để truy cập AuthViewModel chung
    private val authViewModel: AuthViewModel by activityViewModels()

    // Thời gian hiển thị màn hình splash (tính bằng mili giây)
    private val SPLASH_DELAY: Long = 2000 // 2 giây

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Nạp layout cho màn hình splash
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Sử dụng Handler để tạo độ trễ
        Handler(Looper.getMainLooper()).postDelayed({
            // isAdded là một biến an toàn để kiểm tra xem Fragment có còn
            // được gắn vào Activity hay không trước khi thực hiện transaction.
            if (isAdded) {
                checkAuthStatusAndNavigate()
            }
        }, SPLASH_DELAY)
    }

    /**
     * Kiểm tra trạng thái đăng nhập và điều hướng đến màn hình tiếp theo.
     */
    private fun checkAuthStatusAndNavigate() {
        if (authViewModel.isUserLoggedIn()) {
            // Gọi MainActivity để setup dashboard + bottom nav
            (requireActivity() as? MainActivity)?.navigateToDashboard()
        } else {
            // Nếu chưa, chuyển đến màn hình Đăng nhập
            navigateTo(LoginFragment())
        }
    }

    /**
     * Hàm tiện ích để thực hiện việc thay thế Fragment.
     */
    private fun navigateTo(fragment: Fragment) {
        parentFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
        }
    }
}