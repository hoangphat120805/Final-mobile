package com.example.vaiche_driver.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.SettingsRepository
import com.example.vaiche_driver.model.UpdatePassword
import com.example.vaiche_driver.model.UpdateProfileRequest
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<Event<String>>()
    val toastMessage: LiveData<Event<String>> = _toastMessage

    private val _loggedOut = MutableLiveData(false)
    val loggedOut: LiveData<Boolean> = _loggedOut

    // ===== Signals for navigation =====
    private val _passwordUpdated = MutableLiveData<Event<Unit>>()
    val passwordUpdated: LiveData<Event<Unit>> = _passwordUpdated

    private val _profileUpdated = MutableLiveData<Event<Unit>>()
    val profileUpdated: LiveData<Event<Unit>> = _profileUpdated

    fun updatePassword(old: String, new: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repo.updatePassword(UpdatePassword(old, new))
                if (result.isSuccess) {
                    _toastMessage.value = Event("Password updated")
                    _passwordUpdated.value = Event(Unit)
                } else {
                    _toastMessage.value = Event(result.exceptionOrNull()?.message ?: "Update password failed")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadAvatar(part: MultipartBody.Part) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repo.uploadAvatar(part)
                if (result.isSuccess) {
                    _toastMessage.value = Event("Avatar updated")
                } else {
                    _toastMessage.value = Event(result.exceptionOrNull()?.message ?: "Upload avatar failed")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(fullName: String?, gender: String?, birthDateIso: String?, email: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repo.updateProfile(UpdateProfileRequest(fullName, gender, birthDateIso, email))
                if (result.isSuccess) {
                    _toastMessage.value = Event("Profile updated")
                    _profileUpdated.value = Event(Unit)
                } else {
                    _toastMessage.value = Event(result.exceptionOrNull()?.message ?: "Update profile failed")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout(context: Context) {
        val result = repo.logout()
        if (result.isSuccess) {
            _toastMessage.value = Event("Logged out")

            // Restart app về màn hình splash/login
            restartApp(context)

            _loggedOut.value = true
        } else {
            _toastMessage.value = Event(result.exceptionOrNull()?.message ?: "Logout failed")
        }
    }

    private fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
