package com.example.vaicheuserapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupListeners()
    }

    private fun setupListeners() {
        // *** CORRECTED SECTION ***
        // We only have ONE listener for the sign-in button now.
        binding.btnSignIn.setOnClickListener {
            val phone = binding.etPhoneNumber.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter phone and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginUser(phone, password)
        }

        binding.tvSignUpLink.setOnClickListener {
            navigateToRegistration()
        }

        binding.tvForgotPassword.setOnClickListener {
            val intent = Intent(this, ResetPasswordActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser(phone: String, password: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.login(phoneNumber = phone, password = password)
                if (response.isSuccessful && response.body() != null) {
                    val accessToken = response.body()!!.accessToken
                    saveAuthToken(accessToken) // Save access token

                    // --- CRITICAL FIX: Fetch user details and save ID ---
                    val userMeResponse = RetrofitClient.instance.getUserMe()
                    if (userMeResponse.isSuccessful && userMeResponse.body() != null) {
                        val user = userMeResponse.body()!!
                        saveUserId(user.id) // Save user ID
                        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    } else {
                        Log.e("LoginActivity", "Failed to fetch user profile after login: ${userMeResponse.code()} - ${userMeResponse.errorBody()?.string()}")
                        Toast.makeText(this@LoginActivity, "Login successful, but failed to load user data.", Toast.LENGTH_LONG).show()
                        // Decide whether to navigate to dashboard anyway or log out.
                        // For now, let's navigate, but user-specific features might break.
                        navigateToDashboard()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@LoginActivity, "Login failed: $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveUserId(userId: String) {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit { putString("user_id", userId) }
        Log.d("LoginActivity", "User ID saved: $userId")
    }

    private fun saveAuthToken(token: String) {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit { putString("auth_token", token) }
        Log.d("LoginActivity", "Token saved to SharedPreferences.")
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationActivity::class.java)
        startActivity(intent)
    }
}