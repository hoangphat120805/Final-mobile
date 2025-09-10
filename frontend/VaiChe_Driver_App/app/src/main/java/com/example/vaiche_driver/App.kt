package com.example.vaiche_driver

import android.app.Application
import com.example.vaiche_driver.data.network.RetrofitClient

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }
}