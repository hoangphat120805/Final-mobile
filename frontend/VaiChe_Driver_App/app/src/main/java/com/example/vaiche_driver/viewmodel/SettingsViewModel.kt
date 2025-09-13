package com.example.vaiche_driver.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.SettingsRepository
import com.example.vaiche_driver.model.UpdatePassword
import com.example.vaiche_driver.model.UpdateProfileRequest
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

/**
 * ViewModel cho Settings screen
 * - Đổi mật khẩu
 * - Upload avatar
 * - Update profile info
 * - Logout (chỉ phát tín hiệu, không tự restart app trong ViewModel)
 */
class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<Event<String>>()
    val toastMessage: LiveData<Event<String>> = _toastMessage


    // ===== Signals for navigation / UI =====
    private val _passwordUpdated = MutableLiveData<Event<Unit>>()
    val passwordUpdated: LiveData<Event<Unit>> = _passwordUpdated

    /** Bắn riêng khi avatar đổi thành công, kèm signatureKey để bust cache Glide */
    private val _avatarUpdated = MutableLiveData<Event<String>>() // signatureKey
    val avatarUpdated: LiveData<Event<String>> = _avatarUpdated

    /** Bắn khi chỉ thay đổi các trường profile text (fullName/gender/birthDate/email) */
    private val _profileUpdated = MutableLiveData<Event<Unit>>()
    val profileUpdated: LiveData<Event<Unit>> = _profileUpdated

    /** Đổi mật khẩu */
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

    /** Upload avatar (ảnh) */
    fun uploadAvatar(part: MultipartBody.Part) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repo.uploadAvatar(part)
                if (result.isSuccess) {
                    _toastMessage.value = Event("Avatar updated")
                    // signatureKey có thể dùng timestamp hoặc version trả về từ server (nếu có)
                    val signatureKey = System.currentTimeMillis().toString()
                    _avatarUpdated.value = Event(signatureKey)
                } else {
                    _toastMessage.value = Event(result.exceptionOrNull()?.message ?: "Upload avatar failed")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Update thông tin cá nhân (không bao gồm avatar) */
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
    } /** Logout – chỉ phát loggedOut, để Fragment quyết định restart app */

}
