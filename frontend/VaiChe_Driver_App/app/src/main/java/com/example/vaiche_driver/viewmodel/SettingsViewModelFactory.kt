package com.example.vaiche_driver.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.vaiche_driver.data.local.SessionManager
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.SettingsRepository

class SettingsViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            val sessionManager = SessionManager(appContext.applicationContext)
            val repo = SettingsRepository(
                apiProvider = { RetrofitClient.instance }, // luôn lấy ApiService mới nhất
                sessionManager = sessionManager
            )
            return SettingsViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
