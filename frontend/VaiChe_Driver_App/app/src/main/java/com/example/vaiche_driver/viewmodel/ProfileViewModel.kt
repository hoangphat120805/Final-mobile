package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.ProfileRepository
import com.example.vaiche_driver.model.*
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val repository = ProfileRepository()

    private val _userProfile = MutableLiveData<UserPublic?>()
    val userProfile: LiveData<UserPublic?> = _userProfile

    private val _userStats = MutableLiveData<UserStats>()
    val userStats: LiveData<UserStats> = _userStats

    // --- THAY ĐỔI 1: QUẢN LÝ DANH SÁCH REVIEW ---
    private val _reviews = MutableLiveData<List<Review>>()
    val reviews: LiveData<List<Review>> = _reviews

    // Biến để theo dõi trang hiện tại của danh sách review
    private var currentPage = 1
    private var isLastPage = false
    private var isLoadingReviews = false
    // ---------------------------------------------

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    /**
     * Tải dữ liệu ban đầu cho màn hình Profile.
     */
    fun loadInitialProfileData() {
        if (_userProfile.value != null) return // Chỉ tải lần đầu

        _isLoading.value = true
        viewModelScope.launch {
            // Lấy dữ liệu profile và stats
            repository.getUserProfile().onSuccess { user -> _userProfile.value = user }
            _userStats.value = repository.getFakeUserStats()

            // Tải trang review đầu tiên
            loadMoreReviews()

            _isLoading.value = false
        }
    }

    /**
     * --- THAY ĐỔI 2: HÀM TẢI THÊM REVIEW ---
     * Tải trang tiếp theo của danh sách review.
     */
    fun loadMoreReviews() {
        // Ngăn việc gọi liên tục khi đang tải hoặc đã hết trang
        if (isLoadingReviews || isLastPage) return

        isLoadingReviews = true
        viewModelScope.launch {
            // Giả lập gọi API lấy review theo trang
            val result = repository.getFakeReviews(page = currentPage)

            result.onSuccess { newReviews ->
                if (newReviews.isNotEmpty()) {
                    // Nối danh sách cũ với danh sách mới
                    val currentList = _reviews.value ?: emptyList()
                    _reviews.value = currentList + newReviews
                    currentPage++ // Tăng số trang để lần sau gọi trang tiếp theo
                } else {
                    // Nếu API trả về danh sách rỗng, nghĩa là đã hết trang
                    isLastPage = true
                }
            }.onFailure { error ->
                _errorMessage.value = Event(error.message ?: "Failed to load reviews")
            }
            isLoadingReviews = false
        }
    }
}