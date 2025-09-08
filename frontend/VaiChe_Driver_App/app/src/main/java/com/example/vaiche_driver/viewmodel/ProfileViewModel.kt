package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.ProfileRepository
import com.example.vaiche_driver.model.Review
import com.example.vaiche_driver.model.UserPublic
import com.example.vaiche_driver.model.UserStats
import kotlinx.coroutines.launch

/**
 * ViewModel cho màn hình Profile.
 * - Nạp thông tin user
 * - Nạp review (paging)
 * - Cập nhật avg rating (trung bình tất cả review) để hiển thị ở tv_rating_value
 */
class ProfileViewModel : ViewModel() {

    private val repository = ProfileRepository()

    // ========== UI States ==========
    private val _userProfile = MutableLiveData<UserPublic?>()
    val userProfile: LiveData<UserPublic?> = _userProfile

    private val _userStats = MutableLiveData<UserStats>()
    val userStats: LiveData<UserStats> = _userStats

    private val _reviews = MutableLiveData<List<Review>>()
    val reviews: LiveData<List<Review>> = _reviews

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    // ========== Paging controls ==========
    private var currentPage = 1
    private var isLastPage = false
    private var isLoadingReviews = false
    private val pageSize = 5

    /**
     * Tải dữ liệu ban đầu cho màn hình Profile:
     *  - getMe()
     *  - trang review đầu tiên + avg rating
     */
    fun loadInitialProfileData(force: Boolean = false) {
        if (!force && _userProfile.value != null && _reviews.value?.isNotEmpty() == true) return

        _isLoading.value = true
        viewModelScope.launch {
            // 1) Profile
            repository.getUserProfile()
                .onSuccess { user -> _userProfile.value = user }
                .onFailure { e -> emitError(e.message ?: "Failed to load profile") }

            // 2) Reset paging trước khi tải trang đầu
            resetPaging(keepList = false)

            // 3) Trang đầu review + avg rating
            loadMoreReviewsInternal(updateAvg = true)

            _isLoading.value = false
        }
    }

    /**
     * Tải trang tiếp theo cho RecyclerView review.
     */
    fun loadMoreReviews() {
        loadMoreReviewsInternal(updateAvg = false)
    }

    /**
     * Làm tươi toàn bộ:
     *  - Xoá cache avg rating trong repository
     *  - Reset paging
     *  - Nạp lại tất cả
     */
    fun refresh() {
        repository.invalidateCache()
        resetPaging(keepList = false)
        loadInitialProfileData(force = true)
    }

    // ================== Internal ==================

    private fun loadMoreReviewsInternal(updateAvg: Boolean) {
        if (isLoadingReviews || isLastPage) return

        isLoadingReviews = true
        viewModelScope.launch {
            repository.getReviewsPage(page = currentPage, pageSize = pageSize, fetchOwnerName = true)
                .onSuccess { (newItems, avg) ->
                    // Cập nhật danh sách
                    if (newItems.isNotEmpty()) {
                        val cur = _reviews.value ?: emptyList()
                        _reviews.value = cur + newItems
                        currentPage++
                    } else {
                        isLastPage = true
                    }

                    // Trang đầu tiên: set avg rating lấy từ backend
                    if (updateAvg) {
                        _userStats.value = UserStats(
                            averageRating = if (avg.isFinite()) avg else 0f,
                            satisfactionRate = 60,   // chờ backend cung cấp số liệu thật
                            cancellationRate = 8
                        )
                    }
                }
                .onFailure { e ->
                    emitError(e.message ?: "Failed to load reviews")
                }
            isLoadingReviews = false
        }
    }

    private fun resetPaging(keepList: Boolean) {
        currentPage = 1
        isLastPage = false
        isLoadingReviews = false
        if (!keepList) _reviews.value = emptyList()
    }

    private fun emitError(message: String) {
        _errorMessage.value = Event(message)
    }
}
