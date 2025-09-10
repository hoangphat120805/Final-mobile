package com.example.vaiche_driver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.AuthRepository
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.launch

/**
 * ViewModel này quản lý toàn bộ logic và trạng thái cho luồng xác thực
 * (Đăng nhập, Đăng ký, Quên mật khẩu).
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    // Khởi tạo Repository bằng cách sử dụng applicationContext
    private val authRepository = AuthRepository.getInstance(application)

    // --- LIVE DATA CHO TRẠNG THÁI CHUNG ---
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    // --- LIVE DATA CHO CÁC SỰ KIỆN THÀNH CÔNG ---
    private val _loginSuccessEvent = MutableLiveData<Event<Boolean>>()
    val loginSuccessEvent: LiveData<Event<Boolean>> = _loginSuccessEvent

    private val _registrationSuccessEvent = MutableLiveData<Event<UserPublic>>()
    val registrationSuccessEvent: LiveData<Event<UserPublic>> = _registrationSuccessEvent

    private val _otpSentEvent = MutableLiveData<Event<Boolean>>()
    val otpSentEvent: LiveData<Event<Boolean>> = _otpSentEvent

    private val _otpVerifiedEvent = MutableLiveData<Event<String>>() // String là token
    val otpVerifiedEvent: LiveData<Event<String>> = _otpVerifiedEvent

    private val _passwordResetSuccessEvent = MutableLiveData<Event<Boolean>>()
    val passwordResetSuccessEvent: LiveData<Event<Boolean>> = _passwordResetSuccessEvent

    //==================== LOGIC ====================

    /**
     * Kiểm tra xem người dùng đã đăng nhập từ trước hay chưa.
     */
    fun isUserLoggedIn(): Boolean {
        return authRepository.isUserLoggedIn()
    }

    /**
     * Xử lý logic đăng nhập.
     */
    fun login(phoneNumber: String, password: String) {
        if (_isLoading.value == true) return
        _isLoading.value = true
        viewModelScope.launch {
            val result = authRepository.login(UserLoginRequest(phoneNumber, password))
            result.onSuccess {
                _loginSuccessEvent.value = Event(true)
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Login failed")
            }
            _isLoading.value = false
        }
    }

    /**
     * Yêu cầu gửi mã OTP.
     */
    fun sendOtp(email: String, purpose: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val enumPurpose = OTPPurpose.fromString(purpose)
            val result = authRepository.sendOtp(OtpRequest(email, enumPurpose))
            result.onSuccess { _otpSentEvent.value = Event(true) }
                .onFailure { _errorMessage.value = Event(it.message ?: "Failed to send OTP") }
            _isLoading.value = false
        }
    }

    fun verifyOtp(email: String, otp: String, purpose: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val enumPurpose = OTPPurpose.fromString(purpose)
            val result = authRepository.verifyOtp(OtpVerifyRequest(email, otp, enumPurpose))
            result.onSuccess { _otpVerifiedEvent.value = Event(it.verificationToken) }
                .onFailure { _errorMessage.value = Event(it.message ?: "OTP verification failed") }
            _isLoading.value = false
        }
    }


    /**
     * Xử lý logic đăng ký.
     */
    fun register(email: String, phone: String, pass: String, fullName: String, token: String) {
        _isLoading.value = true
        viewModelScope.launch {
            // TẠO ĐÚNG ĐỐI TƯỢNG REQUEST
            val request = CollectorRegisterRequest(
                email = email,
                phoneNumber = phone,
                password = pass,
                fullName = fullName,
                registerToken = token
            )
            val result = authRepository.signup(request) // authRepository cũng cần được cập nhật
            result.onSuccess { user ->
                _registrationSuccessEvent.value = Event(user)
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Registration failed")
            }
            _isLoading.value = false
        }
    }

    /**
     * Xử lý logic đặt lại mật khẩu.
     */
    fun resetPassword(email: String, token: String, newPass: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val request = ResetPasswordRequest(email, token, newPass)
            val result = authRepository.resetPassword(request)
            result.onSuccess {
                _passwordResetSuccessEvent.value = Event(true)
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Password reset failed")
            }
            _isLoading.value = false
        }
    }
}