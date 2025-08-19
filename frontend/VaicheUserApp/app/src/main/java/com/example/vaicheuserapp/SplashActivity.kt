package com.example.vaicheuserapp // Make sure this matches your package name

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.vaicheuserapp.LoginActivity
import com.example.vaicheuserapp.MainActivity
import com.example.vaicheuserapp.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val SPLASH_DELAY: Long = 2500 // 2.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Use a Handler to delay the execution
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatusAndNavigate()
        }, SPLASH_DELAY)
    }

    private fun checkUserStatusAndNavigate() {
        // This is a placeholder for your real login check logic.
        // For now, we'll assume the user is not logged in.
        val isUserLoggedIn = false // TODO: Replace with actual logic later

        if (isUserLoggedIn) {
            // If user is logged in, go to the main dashboard
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } else {
            // If user is not logged in, go to the Login screen
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // Finish this activity so the user can't go back to it
        finish()
    }
}