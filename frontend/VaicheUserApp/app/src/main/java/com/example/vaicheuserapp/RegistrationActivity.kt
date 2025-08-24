package com.example.vaicheuserapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.data.model.UserCreateRequest
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.ActivityRegistrationBinding
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupListeners()
    }

    private fun setupListeners() {
        // *** CORRECTED SECTION ***
        // We only have ONE listener for the register button now.
        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val phone = binding.etPhoneNumber.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (fullName.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = UserCreateRequest(phoneNumber = phone, password = password)
            registerUser(request)
        }

        // Handle "Sign In" link click
        binding.tvSignInLink.setOnClickListener {
            navigateToLoginScreen()
        }
    }

    private fun registerUser(request: UserCreateRequest) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.signup(request)
                if (response.isSuccessful) {
                    Toast.makeText(this@RegistrationActivity, "Registration Successful! Please Sign In.", Toast.LENGTH_LONG).show()
                    navigateToLoginScreen()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Toast.makeText(this@RegistrationActivity, "Registration failed: $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegistrationActivity, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToLoginScreen() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
    }
}