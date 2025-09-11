package com.example.vaicheuserapp // Make sure this matches your package name

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings.Global.putString
import android.util.Log
import androidx.core.content.edit
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.LoginActivity
import com.example.vaicheuserapp.MainActivity
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.network.RetrofitClient
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2000 // 2.0 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        setTheme(R.style.Theme_VaicheUserApp)
        super.onCreate(savedInstanceState)
        // --- CRITICAL FIX: Keep setContentView() to display your custom layout after theme ---
        setContentView(R.layout.activity_splash)


        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatusAndNavigate()
        }, SPLASH_DELAY)
    }

    private fun checkUserStatusAndNavigate() {
        // This is a placeholder for your real login check logic.
        // For now, we'll assume the user is not logged in.
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserMe()
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    saveUserId(user.id)
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Toast.makeText(this@SplashActivity, "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
                val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                startActivity(intent)
            } finally {
                // Finish this activity so the user can't go back to it
                finish()
            }
        }
    }

    private fun saveUserId(userId: String) {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit{ putString("user_id", userId) }
        Log.d("LoginActivity", "User ID saved: $userId")
    }
}
