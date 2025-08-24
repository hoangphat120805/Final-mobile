package com.example.vaicheuserapp

import android.app.Application
import com.example.vaicheuserapp.data.network.RetrofitClient

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize RetrofitClient once when the app starts
        RetrofitClient.init(this)
    }
}