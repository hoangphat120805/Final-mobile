package com.example.vaicheuserapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.data.model.EmailRequest
import com.example.vaicheuserapp.data.model.ResetPasswordPayload
import com.example.vaicheuserapp.data.model.VerifyOtpRequest
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private var otpSent = false
    private var otpVerified = false
    private var otpTimer: CountDownTimer? = null
    private val OTP_RESEND_DELAY_SECONDS = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateOtpButtonState() // Initial state for OTP button
    }

    private fun setupListeners() {
        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendOtpForResetPassword(email)
        }

        binding.etOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 6 && otpSent && !otpVerified) { // Assuming 6-digit OTP
                    verifyOtp(binding.etEmail.text.toString().trim(), s.toString())
                }
            }
        })

        binding.btnResetPassword.setOnClickListener {
            resetUserPassword()
        }
    }

    private fun updateOtpButtonState() {
        if (otpSent && otpTimer != null) {
            binding.btnSendOtp.isEnabled = false
            binding.btnSendOtp.alpha = 0.5f // Mute visually
        } else {
            binding.btnSendOtp.isEnabled = true
            binding.btnSendOtp.alpha = 1.0f
            binding.btnSendOtp.text = getString(R.string.send_otp) // Restore text
        }
    }

    private fun startOtpTimer() {
        otpSent = true
        binding.etEmail.isEnabled = false // Disable email input after OTP sent
        updateOtpButtonState()

        otpTimer = object : CountDownTimer((OTP_RESEND_DELAY_SECONDS * 1000).toLong(), 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.btnSendOtp.text = "Resend OTP in $seconds s"
            }

            override fun onFinish() {
                otpSent = false
                binding.etEmail.isEnabled = true // Re-enable email
                updateOtpButtonState()
                Toast.makeText(this@ResetPasswordActivity, "You can now resend OTP", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun sendOtpForResetPassword(email: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.sendResetPasswordOtp(EmailRequest(email))
                if (response.isSuccessful) {
                    Toast.makeText(this@ResetPasswordActivity, "OTP sent to your email!", Toast.LENGTH_SHORT).show()
                    startOtpTimer()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ResetPassword", "Failed to send OTP: ${response.code()} - $errorBody")
                    Toast.makeText(this@ResetPasswordActivity, "Failed to send OTP: ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ResetPassword", "Error sending OTP: ${e.message}", e)
                Toast.makeText(this@ResetPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun verifyOtp(email: String, otp: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.verifyResetOtp(VerifyOtpRequest(email, otp))
                if (response.isSuccessful) {
                    otpVerified = true
                    otpTimer?.cancel() // Stop timer if OTP is verified
                    binding.etOtp.isEnabled = false // Disable OTP input
                    Toast.makeText(this@ResetPasswordActivity, "OTP verified!", Toast.LENGTH_SHORT).show()
                    // Now user can enter new password
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ResetPassword", "OTP verification failed: ${response.code()} - $errorBody")
                    Toast.makeText(this@ResetPasswordActivity, "Invalid OTP. Please try again.", Toast.LENGTH_LONG).show()
                    otpVerified = false
                }
            } catch (e: Exception) {
                Log.e("ResetPassword", "Error verifying OTP: ${e.message}", e)
                Toast.makeText(this@ResetPasswordActivity, "Network error during OTP verification: ${e.message}", Toast.LENGTH_LONG).show()
                otpVerified = false
            }
        }
    }

    private fun resetUserPassword() {
        val email = binding.etEmail.text.toString().trim()
        val otp = binding.etOtp.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmNewPassword = binding.etConfirmNewPassword.text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
            return
        }
        if (otp.isEmpty() || !otpVerified) {
            Toast.makeText(this, "Please send and verify OTP first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword.isEmpty() || newPassword.length < 8) { // Example: min 8 chars
            Toast.makeText(this, "New password must be at least 8 characters.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword != confirmNewPassword) {
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.resetPassword(ResetPasswordPayload(email, otp, newPassword))
                if (response.isSuccessful) {
                    Toast.makeText(this@ResetPasswordActivity, "Password reset successful! Please log in.", Toast.LENGTH_LONG).show()
                    // Navigate back to login screen, clearing task stack
                    val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ResetPassword", "Password reset failed: ${response.code()} - $errorBody")
                    Toast.makeText(this@ResetPasswordActivity, "Password reset failed: ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ResetPassword", "Error resetting password: ${e.message}", e)
                Toast.makeText(this@ResetPasswordActivity, "Network error during password reset: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        otpTimer?.cancel() // Cancel timer to prevent leaks
    }
}