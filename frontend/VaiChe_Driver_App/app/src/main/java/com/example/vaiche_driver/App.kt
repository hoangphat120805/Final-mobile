package com.example.vaiche_driver

import android.app.Application
import com.example.vaiche_driver.data.network.RetrofitClient
import com.mapbox.common.MapboxOptions

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)

        // nạp token vào MapboxOptions
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
    }
}