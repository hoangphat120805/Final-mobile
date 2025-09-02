package com.example.vaicheuserapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.data.model.OTPRequest
import com.example.vaicheuserapp.data.model.OTPPurpose
import com.example.vaicheuserapp.data.model.OTPVerifyRequest
import com.example.vaicheuserapp.data.model.UserRegisterRequest // <-- Ensure this is imported
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityRegistrationBinding
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private var actualRegisterToken: String? = null // To store the token received after OTP verification
    private var otpSent = false // True if send OTP API call succeeded
    private var otpTimer: CountDownTimer? = null
    private val OTP_RESEND_DELAY_SECONDS = 60 // 60 seconds delay for OTP resend

    // --- NEW STATE VARIABLE ---
    private var otpVerificationAttempted = false // True if we've successfully called verifyOtp and got a token

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        // Add TextWatchers for all relevant fields
        binding.etFullName.addTextChangedListener(textWatcher)
        binding.etEmail.addTextChangedListener(textWatcher)
        binding.etOtp.addTextChangedListener(textWatcher)
        binding.etPhoneNumber.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)

        updateUiState() // Initial UI update based on initial (empty) state
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {
            updateUiState()
        }
    }

    private fun setupListeners() {
        binding.btnSendOtp.setOnClickListener { sendOtpForRegistration() }
        binding.btnRegister.setOnClickListener { registerUserSequence() } // <-- Changed to a sequence starter
        binding.tvSignInLink.setOnClickListener { navigateToLoginScreen() }
    }

    private fun updateUiState() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val otp = binding.etOtp.text.toString().trim()
        val phone = binding.etPhoneNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isOtpValid = otp.length == 6 // Assuming 6-digit OTP
        val passwordsMatch = password.isNotEmpty() && password == confirmPassword
        val allFieldsFilledForInitialData = fullName.isNotEmpty() && isEmailValid && phone.isNotEmpty()

        // Enable "Send OTP" button if email is valid and timer is NOT running
        val isTimerRunning = otpTimer != null && (binding.btnSendOtp.text.contains("Resend") || otpSent)
        binding.btnSendOtp.isEnabled = isEmailValid && !isTimerRunning


        // --- CRITICAL FIX: "Register" button enabled based on form validity only ---
        binding.btnRegister.isEnabled =
            allFieldsFilledForInitialData &&
                    isOtpValid &&
                    passwordsMatch
        // The actualRegisterToken != null check is handled by the sequence.
    }

    private fun sendOtpForRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val fullName = binding.etFullName.text.toString().trim()
        val phone = binding.etPhoneNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email.", Toast.LENGTH_SHORT).show()
            return
        }
        if (fullName.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all registration fields first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        binding.btnSendOtp.isEnabled = false // Disable to prevent multiple clicks
        otpSent = true // Mark OTP as sent
        startOtpResendTimer()

        lifecycleScope.launch {
            try {
                val request = OTPRequest(email, OTPPurpose.register.name)
                val response = RetrofitClient.instance.sendOtp(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@RegistrationActivity, "OTP sent to your email!", Toast.LENGTH_LONG).show()
                    // Reset verification status when a new OTP is sent
                    otpVerificationAttempted = false
                    actualRegisterToken = null
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@RegistrationActivity, "Failed to send OTP: ${response.code()} - ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    resetOtpTimer() // Reset timer if API call failed
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegistrationActivity, "Error sending OTP: ${e.message}", Toast.LENGTH_LONG).show()
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

    // --- NEW: This function orchestrates the two API calls for registration ---
    private fun registerUserSequence() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val otp = binding.etOtp.text.toString().trim()
        val phone = binding.etPhoneNumber.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        // Frontend validations (redundant from sendOtpForRegistration, but good for final check)
        if (fullName.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches() || otp.length != 6 ||
            phone.isEmpty() || password.isEmpty() || password != confirmPassword || !otpSent) {
            Toast.makeText(this, "Please check all inputs and ensure OTP was sent.", Toast.LENGTH_LONG).show()
            return
        }

        hideKeyboard()
        binding.btnRegister.isEnabled = false // Disable during API call

        lifecycleScope.launch {
            try {
                // --- Step 1: Verify OTP if not already verified in this session ---
                if (!otpVerificationAttempted || actualRegisterToken == null) {
                    val verifyResponse = RetrofitClient.instance.verifyOtp(OTPVerifyRequest(email, otp, OTPPurpose.register.name))
                    if (verifyResponse.isSuccessful && verifyResponse.body() != null) {
                        actualRegisterToken = verifyResponse.body()!!.verificationToken // Capture token
                        otpVerificationAttempted = true // Mark as verified for this session
                        Log.d("Registration", "OTP verified. Received register_token: $actualRegisterToken")
                        // Continue to the actual signup call immediately
                        callFinalSignupApi(fullName, email, phone, password)
                    } else {
                        val errorBody = verifyResponse.errorBody()?.string()
                        Toast.makeText(this@RegistrationActivity, "OTP verification failed: ${verifyResponse.code()} - ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        otpVerificationAttempted = false // Reset on failure
                        actualRegisterToken = null
                    }
                } else {
                    // OTP was already verified, and we have the token, proceed directly to final signup
                    Log.d("Registration", "OTP already verified in session. Using stored token: $actualRegisterToken")
                    callFinalSignupApi(fullName, email, phone, password)
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegistrationActivity, "Error during registration sequence: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("Registration", "Exception during registration sequence: ${e.message}", e)
            } finally {
                binding.btnRegister.isEnabled = true // Re-enable button
                updateUiState()
            }
        }
    }

    // --- NEW: Helper function to call the final signup API ---
    private suspend fun callFinalSignupApi(fullName: String, email: String, phone: String, password: String) {
        if (actualRegisterToken == null) {
            // This case should ideally not happen due to the logic above
            Toast.makeText(this, "Internal error: Registration token not available.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val signupRequest = UserRegisterRequest(
                fullName = fullName,
                email = email,
                phoneNumber = phone,
                password = password,
                registerToken = actualRegisterToken!! // Use the captured token
            )
            val signupResponse = RetrofitClient.instance.signup(signupRequest)
            if (signupResponse.isSuccessful) {
                Toast.makeText(this@RegistrationActivity, "Registration successful! Please log in.", Toast.LENGTH_LONG).show()
                val intent = Intent(this@RegistrationActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                val errorBody = signupResponse.errorBody()?.string()
                Toast.makeText(this@RegistrationActivity, "Registration failed: ${signupResponse.code()} - ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                actualRegisterToken = null // Clear token on final signup failure
                otpVerificationAttempted = false // Reset verification state
            }
        } catch (e: Exception) {
            Toast.makeText(this@RegistrationActivity, "Error during final signup: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("Registration", "Exception during final signup: ${e.message}", e)
        } finally {
            updateUiState() // Update UI state after API call
        }
    }

    private fun navigateToLoginScreen() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
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