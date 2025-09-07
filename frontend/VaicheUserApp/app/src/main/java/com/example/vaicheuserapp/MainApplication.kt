package com.example.vaicheuserapp

import android.app.Application
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.mapbox.common.MapboxOptions

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)

        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
    }
}