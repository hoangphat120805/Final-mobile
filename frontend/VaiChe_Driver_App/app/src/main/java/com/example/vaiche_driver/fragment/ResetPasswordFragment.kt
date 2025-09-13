package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.os.CountDownTimer
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

class ResetPasswordFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    private var countDownTimer: CountDownTimer? = null
    private var resetToken: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_reset_password, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Views
        val emailInput = view.findViewById<EditText>(R.id.et_email)
        val otpInput = view.findViewById<EditText>(R.id.et_otp)
        val newPasswordInput = view.findViewById<EditText>(R.id.et_new_password)
        val confirmPassInput = view.findViewById<EditText>(R.id.et_confirm_password)
        val sendOtpButton = view.findViewById<TextView>(R.id.btn_send_otp)
        val resetButton = view.findViewById<Button>(R.id.btn_reset_password_final)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar_reset)
        val loadingOverlay = view.findViewById<View>(R.id.loading_overlay)   // <-- NEW

        // Lắng nghe ViewModel
        observeViewModel(
            sendOtpButton, resetButton, progressBar, loadingOverlay,
            emailInput, otpInput, newPasswordInput, confirmPassInput
        )

        // Clicks
        sendOtpButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isNotEmpty()) {
                authViewModel.sendOtp(email, "reset")
            } else {
                Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
            }
        }

        resetButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val otp = otpInput.text.toString().trim()
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPassInput.text.toString().trim()

            if (newPassword != confirmPassword) {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (resetToken == null) {
                authViewModel.verifyOtp(email, otp, "reset")
            } else {
                authViewModel.resetPassword(email, resetToken!!, newPassword)
            }
        }
    }

    private fun observeViewModel(
        sendOtpButton: TextView,
        resetButton: Button,
        progressBar: ProgressBar,
        loadingOverlay: View,
        emailInput: EditText,
        otpInput: EditText,
        newPasswordInput: EditText,
        confirmPassInput: EditText
    ) {
        authViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // logic cũ
            resetButton.isEnabled = !isLoading
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            // overlay đè toàn màn
            loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE

            // (khuyến nghị) khóa input khi loading
            emailInput.isEnabled = !isLoading && (resetToken == null) // đã verify thì khóa luôn
            otpInput.isEnabled = !isLoading && (resetToken == null)
            newPasswordInput.isEnabled = !isLoading
            confirmPassInput.isEnabled = !isLoading
            sendOtpButton.isEnabled = !isLoading && (resetToken == null)
        }

        authViewModel.errorMessage.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                resetOtpButton(sendOtpButton)
            }
        }

        authViewModel.otpSentEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isSuccess ->
                if (isSuccess) {
                    Toast.makeText(context, "OTP sent to your email!", Toast.LENGTH_SHORT).show()
                    startOtpCountdown(sendOtpButton)
                }
            }
        }

        authViewModel.otpVerifiedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { token ->
                Toast.makeText(
                    context,
                    "OTP Verified. Please enter your new password and press Reset again.",
                    Toast.LENGTH_LONG
                ).show()
                resetToken = token
                // khóa email/otp sau khi verify
                emailInput.isEnabled = false
                otpInput.isEnabled = false
            }
        }

        authViewModel.passwordResetSuccessEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { isSuccess ->
                if (isSuccess) {
                    Toast.makeText(
                        context,
                        "Password has been reset successfully. Please login.",
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    private fun startOtpCountdown(sendOtpButton: TextView) {
        sendOtpButton.isEnabled = false
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                sendOtpButton.text = "Resend in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                resetOtpButton(sendOtpButton)
            }
        }.start()
    }

    private fun resetOtpButton(sendOtpButton: TextView) {
        sendOtpButton.text = "Send OTP"
        sendOtpButton.isEnabled = true
        countDownTimer?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }
}
