package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.example.vaiche_driver.MainActivity
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.AuthViewModel

class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    // LoginFragment.kt
    override fun onResume() {
        super.onResume()
        //(requireActivity() as MainActivity).hideBottomNavForAuthScreens()
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- TÌM CÁC VIEW ---
        val phoneInput = view.findViewById<EditText>(R.id.et_phone_number)
        val passwordInput = view.findViewById<EditText>(R.id.et_password)
        val signInButton = view.findViewById<Button>(R.id.btn_sign_in)
        val signUpLink = view.findViewById<TextView>(R.id.tv_sign_up_link)
        val forgotPasswordLink = view.findViewById<TextView>(R.id.tv_forgot_password)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar_login) // <-- THÊM ID NÀY VÀO XML

        // --- LẮNG NGHE KẾT QUẢ TỪ VIEWMODEL ---
        observeViewModel(signInButton, progressBar)

        // --- XỬ LÝ SỰ KIỆN CLICK ---
        signInButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            // TODO: Thêm kiểm tra đầu vào (validation) chi tiết hơn
            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Please enter phone number and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Ra lệnh cho ViewModel thực hiện đăng nhập
            authViewModel.login(phone, password)
        }

        signUpLink.setOnClickListener {
            navigateTo(RegistrationFragment())
        }

        forgotPasswordLink.setOnClickListener {
            navigateTo(ResetPasswordFragment())
        }
    }

    private fun observeViewModel(signInButton: Button, progressBar: ProgressBar) {
        // Lắng nghe trạng thái loading
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            signInButton.isEnabled = !isLoading
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Lắng nghe thông báo lỗi
        authViewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, "Login Failed: $message", Toast.LENGTH_LONG).show()
            }
        }

        // Lắng nghe sự kiện đăng nhập thành công
        authViewModel.loginSuccessEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isSuccess ->
                if (isSuccess) {
                    Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                    // Điều hướng đến màn hình Dashboard
                    (activity as? MainActivity)?.navigateToDashboard()
                }
            }
        }
    }

    /**
     * Hàm tiện ích để điều hướng giữa các Fragment.
     * @param clearBackStack Xóa toàn bộ back stack, dùng khi đăng nhập thành công.
     */
    private fun navigateTo(fragment: Fragment, clearBackStack: Boolean = false) {
        parentFragmentManager.commit {
            replace(R.id.fragment_container, fragment)
            if (!clearBackStack) {
                addToBackStack(null) // Cho phép quay lại màn hình Login từ Register/Reset
            }
        }
        if (clearBackStack) {
            // Xóa sạch back stack để người dùng không thể quay lại màn hình Login
            parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }
}