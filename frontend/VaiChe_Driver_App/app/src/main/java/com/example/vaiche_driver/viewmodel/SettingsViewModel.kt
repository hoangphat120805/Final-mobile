package com.example.vaiche_driver.viewmodel

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

    fun updatePassword(old: String, new: String) {
        _isLoading.value = true
        viewModelScope.launch {
            repo.updatePassword(UpdatePassword(old, new))
                .onSuccess { _toastMessage.value = Event("Password updated") }
                .onFailure { e -> _toastMessage.value = Event(e.message ?: "Update password failed") }
            _isLoading.value = false
        }
    }

    fun uploadAvatar(part: MultipartBody.Part) {
        _isLoading.value = true
        viewModelScope.launch {
            repo.uploadAvatar(part)
                .onSuccess { _toastMessage.value = Event("Avatar updated") }
                .onFailure { e -> _toastMessage.value = Event(e.message ?: "Upload avatar failed") }
            _isLoading.value = false
        }
    }

    fun updateProfile(fullName: String?, gender: String?, birthDateIso: String?, email: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            repo.updateProfile(UpdateProfileRequest(fullName, gender, birthDateIso, email))
                .onSuccess { _toastMessage.value = Event("Profile updated") }
                .onFailure { e -> _toastMessage.value = Event(e.message ?: "Update profile failed") }
            _isLoading.value = false
        }
    }

    fun logout() {
        val result = repo.logout()
        if (result.isSuccess) {
            _toastMessage.value = Event("Logged out")
            _loggedOut.value = true
        } else {
            _toastMessage.value = Event(result.exceptionOrNull()?.message ?: "Logout failed")
        }
    }
}

