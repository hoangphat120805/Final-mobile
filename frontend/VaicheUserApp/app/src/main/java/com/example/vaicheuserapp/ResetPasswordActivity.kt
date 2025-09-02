package com.example.vaicheuserapp

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.data.model.OTPRequest
import com.example.vaicheuserapp.data.model.OTPPurpose
import com.example.vaicheuserapp.data.model.OTPVerifyRequest
import com.example.vaicheuserapp.data.model.ResetPasswordRequest
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay // Added for simulation or debounce

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private var actualResetToken: String? = null // Store the token received after OTP verification
    private var otpSent = false // True if send OTP API call succeeded
    private var otpTimer: CountDownTimer? = null
    private val OTP_RESEND_DELAY_SECONDS = 60 // 60 seconds delay for OTP resend

    // --- NEW STATE VARIABLE ---
    private var otpVerificationAttempted = false // True if we've successfully called verifyOtp and got a token

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        // Add TextWatcher for email field to update UI state
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateUiState()
            }
        })
        updateUiState() // Initial UI update based on initial (empty) state
    }

    private fun setupListeners() {
        binding.btnSendOtp.setOnClickListener { sendOtpToEmail() }
        binding.btnResetPasswordFinal.setOnClickListener { resetPasswordSequence() } // <-- Changed to a sequence starter

        // Observe OTP input to update reset button
        binding.etOtp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateUiState()
            }
        })
        binding.etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateUiState()
            }
        })
        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateUiState()
            }
        })
    }

    private fun updateUiState() {
        val email = binding.etEmail.text.toString().trim()
        val otp = binding.etOtp.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Enable "Send OTP" button if email is valid and timer is NOT running
        val isTimerRunning = otpTimer != null && (binding.btnSendOtp.text.contains("Resend") || otpSent)
        binding.btnSendOtp.isEnabled = Patterns.EMAIL_ADDRESS.matcher(email).matches() && !isTimerRunning


        // --- CRITICAL FIX: "Reset password" button enabled based on form validity only ---
        // It's always clickable if all text fields are filled and match,
        // The internal logic will handle the OTP verification step.
        binding.btnResetPasswordFinal.isEnabled =
            Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    otp.isNotEmpty() && otp.length == 6 && // Assuming OTP is 6 digits
                    newPassword.isNotEmpty() && confirmPassword.isNotEmpty() &&
                    newPassword == confirmPassword
        // REMOVED: actualResetToken != null <-- This is now handled by the sequence
    }

    private fun sendOtpToEmail() {
        val email = binding.etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email.", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        binding.btnSendOtp.isEnabled = false
        otpSent = true // Mark OTP as sent
        startOtpResendTimer()

        lifecycleScope.launch {
            try {
                val request = OTPRequest(email, OTPPurpose.reset.name)
                val response = RetrofitClient.instance.sendOtp(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@ResetPasswordActivity, "OTP sent to your email!", Toast.LENGTH_LONG).show()
                    // Reset verification status when a new OTP is sent
                    otpVerificationAttempted = false
                    actualResetToken = null
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@ResetPasswordActivity, "Failed to send OTP: ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    resetOtpTimer() // Reset timer if API call failed
                }
            } catch (e: Exception) {
                Toast.makeText(this@ResetPasswordActivity, "Error sending OTP: ${e.message}", Toast.LENGTH_LONG).show()
                resetOtpTimer() // Reset timer if API call failed
            } finally {
                updateUiState()
            }
        }
    }

    private fun startOtpResendTimer() {
        otpTimer?.cancel()
        otpTimer = object : CountDownTimer((OTP_RESEND_DELAY_SECONDS * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.btnSendOtp.text = "Resend (${millisUntilFinished / 1000}s)"
                binding.btnSendOtp.isEnabled = false
            }

            override fun onFinish() {
                binding.btnSendOtp.text = "Send OTP"
                binding.btnSendOtp.isEnabled = true
                otpSent = false // Allow sending again
                updateUiState()
            }
        }.start()
        updateUiState()
    }

    private fun resetOtpTimer() {
        otpTimer?.cancel()
        binding.btnSendOtp.text = "Send OTP"
        binding.btnSendOtp.isEnabled = true
        otpSent = false
    }

    // --- NEW: This function orchestrates the two API calls ---
    private fun resetPasswordSequence() {
        val email = binding.etEmail.text.toString().trim()
        val otp = binding.etOtp.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Frontend validations
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email.", Toast.LENGTH_SHORT).show()
            return
        }
        if (otp.length != 6) { // Basic OTP length validation
            Toast.makeText(this, "Please enter a 6-digit OTP.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword.isEmpty() || newPassword != confirmPassword) {
            Toast.makeText(this, "New passwords do not match or are empty.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!otpSent) { // Must have requested OTP first
            Toast.makeText(this, "Please send OTP first.", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        binding.btnResetPasswordFinal.isEnabled = false // Disable during API call

        lifecycleScope.launch {
            try {
                // --- Step 1: Verify OTP if not already verified in this session ---
                if (!otpVerificationAttempted || actualResetToken == null) {
                    val verifyResponse = RetrofitClient.instance.verifyOtp(OTPVerifyRequest(email, otp, OTPPurpose.reset.name))
                    if (verifyResponse.isSuccessful && verifyResponse.body() != null) {
                        actualResetToken = verifyResponse.body()!!.verificationToken // Capture token
                        otpVerificationAttempted = true // Mark as verified for this session
                        Log.d("ResetPassword", "OTP verified. Received reset_token: $actualResetToken")
                        // Continue to the actual reset password call immediately
                        callFinalResetPasswordApi(email, newPassword)
                    } else {
                        val errorBody = verifyResponse.errorBody()?.string()
                        Toast.makeText(this@ResetPasswordActivity, "OTP verification failed: ${verifyResponse.code()} - ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        otpVerificationAttempted = false // Reset on failure
                        actualResetToken = null
                    }
                } else {
                    // OTP was already verified, and we have the token, proceed directly to final reset
                    Log.d("ResetPassword", "OTP already verified in session. Using stored token: $actualResetToken")
                    callFinalResetPasswordApi(email, newPassword)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ResetPasswordActivity, "Error during password reset sequence: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ResetPassword", "Exception during reset sequence: ${e.message}", e)
            } finally {
                binding.btnResetPasswordFinal.isEnabled = true // Re-enable button
                updateUiState()
            }
        }
    }

    // --- NEW: Helper function to call the final password reset API ---
    private suspend fun callFinalResetPasswordApi(email: String, newPassword: String) {
        if (actualResetToken == null) {
            // This case should ideally not happen due to the logic above
            Toast.makeText(this, "Internal error: Reset token not available.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val resetResponse = RetrofitClient.instance.resetPassword(ResetPasswordRequest(email, actualResetToken!!, newPassword))
            if (resetResponse.isSuccessful) {
                Toast.makeText(this@ResetPasswordActivity, "Password reset successfully! Please log in.", Toast.LENGTH_LONG).show()
                finish() // Go back to Login screen
            } else {
                val errorBody = resetResponse.errorBody()?.string()
                Toast.makeText(this@ResetPasswordActivity, "Password reset failed: ${resetResponse.code()} - ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                actualResetToken = null // Clear token on final reset failure
                otpVerificationAttempted = false // Reset verification state
            }
        } catch (e: Exception) {
            Toast.makeText(this@ResetPasswordActivity, "Error during final password reset: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("ResetPassword", "Exception during final reset: ${e.message}", e)
        } finally {
            updateUiState() // Update UI state after API call
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        otpTimer?.cancel()
    }
}