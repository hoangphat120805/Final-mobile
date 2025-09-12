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

    private val authViewModel: AuthViewModel by activityViewModels()

    private var countDownTimer: CountDownTimer? = null
    private var registerToken: String? = null

    private lateinit var fullNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPassInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var sendOtpButton: TextView
    private lateinit var registerButton: Button
    private lateinit var signInLink: TextView

    private lateinit var progressBar: ProgressBar          // đã có
    private lateinit var loadingOverlay: View               // <-- NEW

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_registration, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ánh xạ view
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
        loadingOverlay = view.findViewById(R.id.loading_overlay)   // <-- NEW

        otpInput.isEnabled = false

        observeViewModel()
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

            if (password != confirmPass) {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (otp.length != 6) {
                Toast.makeText(context, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            authViewModel.verifyOtp(email, otp, "register")
        }

        signInLink.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeViewModel() {
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Giữ nguyên logic cũ:
            registerButton.isEnabled = !isLoading
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            // NEW: hiển/ẩn overlay để đè lên toàn màn hình
            loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE

            // (Không bắt buộc) khóa mở các input cho đỡ bấm nhầm trong lúc loading
            fullNameInput.isEnabled = !isLoading
            emailInput.isEnabled = !isLoading
            phoneInput.isEnabled = !isLoading
            passwordInput.isEnabled = !isLoading
            confirmPassInput.isEnabled = !isLoading
            otpInput.isEnabled = !isLoading || otpInput.text?.isNotEmpty() == true
            sendOtpButton.isEnabled = !isLoading
            signInLink.isEnabled = !isLoading
        }

        authViewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                resetOtpButton()
            }
        }

        authViewModel.otpSentEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isSuccess ->
                if (isSuccess) {
                    Toast.makeText(context, "OTP sent to your email!", Toast.LENGTH_SHORT).show()
                    otpInput.isEnabled = true
                    startOtpCountdown()
                }
            }
        }

        authViewModel.otpVerifiedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { token ->
                this.registerToken = token
                val fullName = fullNameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val password = passwordInput.text.toString().trim()
                authViewModel.register(email, phone, password, fullName, this.registerToken!!)
            }
        }

        authViewModel.registrationSuccessEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { user ->
                Toast.makeText(context, "Registration successful for ${user.fullName}! Please log in.", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun startOtpCountdown() {
        sendOtpButton.isEnabled = false
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
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
        countDownTimer?.cancel()
    }
}
