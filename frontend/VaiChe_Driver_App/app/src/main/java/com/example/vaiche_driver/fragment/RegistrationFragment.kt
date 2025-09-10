package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.AuthViewModel

class RegistrationFragment : Fragment() {

    // Truy cập AuthViewModel chung của Activity
    private val authViewModel: AuthViewModel by activityViewModels()

    // Timer cho việc gửi lại OTP
    private var countDownTimer: CountDownTimer? = null

    // Biến để lưu lại token tạm thời sau khi xác thực OTP
    private var registerToken: String? = null

    // Khai báo các View để dễ dàng truy cập
    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPassInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var sendOtpButton: TextView
    private lateinit var registerButton: Button
    private lateinit var signInLink: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_registration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ánh xạ các View từ layout
        fullNameInput = view.findViewById(R.id.et_full_name)
        emailInput = view.findViewById(R.id.et_email)
        phoneInput = view.findViewById(R.id.et_phone_number)
        passwordInput = view.findViewById(R.id.et_password)
        confirmPassInput = view.findViewById(R.id.et_confirm_password)
        otpInput = view.findViewById(R.id.et_otp)
        sendOtpButton = view.findViewById(R.id.btn_send_otp)
        registerButton = view.findViewById(R.id.btn_register)
        signInLink = view.findViewById(R.id.tv_sign_in_link)
        progressBar = view.findViewById(R.id.progress_bar_register)

        // Ban đầu, không cho phép nhập OTP
        otpInput.isEnabled = false

        // Lắng nghe các kết quả từ ViewModel
        observeViewModel()

        // Thiết lập các sự kiện click
        setupClickListeners()
    }

    private fun setupClickListeners() {
        sendOtpButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                authViewModel.sendOtp(email, "register")
            } else {
                Toast.makeText(context, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            }
        }

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val otp = otpInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPass = confirmPassInput.text.toString().trim()

            // TODO: Thêm validation chi tiết hơn cho các trường
            if (password != confirmPass) {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (otp.length != 6) { // Giả sử OTP có 6 chữ số
                Toast.makeText(context, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Bước đầu tiên của việc đăng ký là xác thực OTP
            authViewModel.verifyOtp(email, otp, "register")
        }

        signInLink.setOnClickListener {
            parentFragmentManager.popBackStack() // Quay lại màn hình Login
        }
    }

    private fun observeViewModel() {
        // Lắng nghe trạng thái loading chung
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            registerButton.isEnabled = !isLoading
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Lắng nghe thông báo lỗi
        authViewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                resetOtpButton()
            }
        }

        // Lắng nghe sự kiện gửi OTP thành công
        authViewModel.otpSentEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isSuccess ->
                if (isSuccess) {
                    Toast.makeText(context, "OTP sent to your email!", Toast.LENGTH_SHORT).show()
                    otpInput.isEnabled = true // Cho phép người dùng nhập OTP
                    startOtpCountdown()
                }
            }
        }

        // Lắng nghe sự kiện xác thực OTP thành công
        authViewModel.otpVerifiedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { token ->
                // Sau khi OTP đúng, lấy token và thực hiện đăng ký
                this.registerToken = token

                val fullName = fullNameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()

                // Gọi hàm đăng ký cuối cùng
                authViewModel.register(email, phone, password, fullName, this.registerToken!!)
            }
        }

        // Lắng nghe sự kiện đăng ký thành công
        authViewModel.registrationSuccessEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { user ->
                Toast.makeText(context, "Registration successful for ${user.fullName}! Please log in.", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack() // Quay về màn hình Login
            }
        }
    }

    private fun startOtpCountdown() {
        sendOtpButton.isEnabled = false
        countDownTimer?.cancel() // Hủy timer cũ nếu có
        countDownTimer = object: CountDownTimer(60000, 1000) { // 60 giây
            override fun onTick(millisUntilFinished: Long) {
                sendOtpButton.text = "Resend in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                resetOtpButton()
            }
        }.start()
    }

    private fun resetOtpButton() {
        sendOtpButton.text = "Send OTP"
        sendOtpButton.isEnabled = true
        countDownTimer?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel() // Dọn dẹp timer để tránh memory leak
    }
}