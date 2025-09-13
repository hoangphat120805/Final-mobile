package com.example.vaiche_driver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.ProfileRepository
import com.example.vaiche_driver.model.Review
import com.example.vaiche_driver.model.UserPublic
import com.example.vaiche_driver.model.UserStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

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

    // Chặn load initial trùng
    private var isInitialLoading = false

    // Nếu đang initial load mà có yêu cầu force thì defer để chạy lại ngay sau đó
    private var pendingForceLoad = false

    // (tuỳ chọn) chặn refresh đúp cực sát
    private var isRefreshing = false

    // job theo dõi vòng initial load để join khi refresh
    private var initialLoadJob: Job? = null

    // LiveData tiện lấy userId
    val myUserId: LiveData<String?> = userProfile.map { it?.id }

    /** Đảm bảo có dữ liệu ban đầu nếu chưa có */
    fun ensureUserLoaded() {
        if (userProfile.value == null && isLoading.value != true) {
            loadInitialProfileData(force = false)
        }
    }

    /** Tải dữ liệu ban đầu cho màn hình Profile */
    fun loadInitialProfileData(force: Boolean = false) {
        // Nếu đang loading:
        if (isInitialLoading) {
            // Nếu có yêu cầu force, đánh dấu chờ chạy lại sau
            if (force) pendingForceLoad = true
            return
        }

        isInitialLoading = true
        _isLoading.value = true
        initialLoadJob = viewModelScope.launch {
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

                // Nếu lúc đang load có yêu cầu force, chạy lại ngay một vòng force
                if (pendingForceLoad) {
                    pendingForceLoad = false
                    repository.invalidateCache()
                    resetPaging(keepList = false)
                    // gọi lại force (lần này không bị chặn vì isInitialLoading = false)
                    loadInitialProfileData(force = true)
                }
            }
        }
    }

    /** Tải trang tiếp theo cho RecyclerView review. */
    fun loadMoreReviews() = loadMoreReviewsInternal(updateAvg = false)

    /** Làm tươi toàn bộ (được gọi sau khi Settings báo changed) */
    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            try {
                repository.invalidateCache()
                resetPaging(keepList = false)
                loadInitialProfileData(force = true)
                // chờ vòng initial load kết thúc để tránh overlap
                initialLoadJob?.join()
            } finally {
                isRefreshing = false
            }
        }
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
                            cancellationRate = 0      // placeholder
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
