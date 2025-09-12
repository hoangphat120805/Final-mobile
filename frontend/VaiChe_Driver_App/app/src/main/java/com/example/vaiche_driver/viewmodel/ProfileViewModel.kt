package com.example.vaiche_driver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.ProfileRepository
import com.example.vaiche_driver.model.Review
import com.example.vaiche_driver.model.UserPublic
import com.example.vaiche_driver.model.UserStats
import kotlinx.coroutines.launch

/**
 * ViewModel cho màn hình Profile.
 * - Nạp thông tin user
 * - Nạp review (paging)
 * - Cập nhật avg rating
 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    // Dùng ApiProvider để luôn lấy ApiService mới nhất (tránh token cũ gây 401)
    private val repository = ProfileRepository(
        apiProvider = { RetrofitClient.instance }
    )

    // ========== UI States ==========
    private val _userProfile = MutableLiveData<UserPublic?>()
    val userProfile: LiveData<UserPublic?> = _userProfile

    private val _userStats = MutableLiveData<UserStats>()
    val userStats: LiveData<UserStats> = _userStats

    private val _reviews = MutableLiveData<List<Review>>(emptyList())
    val reviews: LiveData<List<Review>> = _reviews

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    // ========== Paging controls ==========
    private var currentPage = 1
    private var isLastPage = false
    private var isLoadingReviews = false
    private val pageSize = 5

    // ========== NEW: chặn loadInitial trùng ==========
    private var isInitialLoading = false

    /** Tải dữ liệu ban đầu cho màn hình Profile */
    fun loadInitialProfileData(force: Boolean = false) {
        // nếu đang load initial thì bỏ
        if (isInitialLoading) return

        // nếu đã có dữ liệu và không force thì bỏ
        if (!force && _userProfile.value != null && !_reviews.value.isNullOrEmpty()) return

        isInitialLoading = true
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1) Profile
                repository.getUserProfile()
                    .onSuccess { user -> _userProfile.value = user }
                    .onFailure { e -> emitError(e.message ?: "Failed to load profile") }

                // 2) Reset paging trước khi tải trang đầu
                resetPaging(keepList = false)

                // 3) Trang đầu review + avg rating
                loadMoreReviewsInternal(updateAvg = true)
            } finally {
                _isLoading.value = false
                isInitialLoading = false
            }
        }
    }

    /** Tải trang tiếp theo cho RecyclerView review. */
    fun loadMoreReviews() = loadMoreReviewsInternal(updateAvg = false)

    /** Làm tươi toàn bộ */
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
            try {
                repository.getReviewsPage(
                    page = currentPage,
                    pageSize = pageSize,
                    fetchOwnerName = true
                ).onSuccess { (newItems, avg) ->
                    if (newItems.isNotEmpty()) {
                        val cur = _reviews.value.orEmpty()
                        _reviews.value = cur + newItems
                        currentPage++
                    } else {
                        isLastPage = true
                    }

                    if (updateAvg) {
                        _userStats.value = UserStats(
                            averageRating = if (avg.isFinite()) avg else 0f,
                            satisfactionRate = 100,   // placeholder
                            cancellationRate = 0     // placeholder
                        )
                    }
                }.onFailure { e ->
                    emitError(e.message ?: "Failed to load reviews")
                }
            } finally {
                isLoadingReviews = false
            }
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
