package com.example.vaiche_driver.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.repository.OrderRepository
import com.example.vaiche_driver.fragment.OrderDetailFragment
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.model.TransactionMethod
import com.example.vaiche_driver.model.CompletedOrderItemPayload
import com.example.vaiche_driver.model.OrderCompletionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.InputStream

class OrderDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val orderRepository = OrderRepository()

    private val _orderDetail = MutableLiveData<OrderDetail?>()
    val orderDetail: LiveData<OrderDetail?> = _orderDetail

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<Event<String>>()
    val errorMessage: LiveData<Event<String>> = _errorMessage

    private val _orderCompletedEvent = MutableLiveData<Event<Boolean>>()
    val orderCompletedEvent: LiveData<Event<Boolean>> = _orderCompletedEvent

    fun loadOrder(orderId: String?) {
        if (orderId == null) {
            _errorMessage.value = Event("Order ID is missing.")
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            val result = orderRepository.getOrderDetail(orderId)
            result.onSuccess { detail -> _orderDetail.value = detail }
                .onFailure { e -> _errorMessage.value = Event(e.message ?: "Failed to load order details.") }
            _isLoading.value = false
        }
    }

    /**
     * Giai đoạn PICK-UP:
     * - Backend yêu cầu 2 file (file1, file2).
     * - Khi mới có ảnh pickup, ta tạm upload (pickup, pickup).
     * - Sau đó chuyển UI sang delivering (map local).
     */
    fun uploadPickupPhase(orderId: String, pickupUri: Uri?) {
        viewModelScope.launch {
            val pickupPart = buildPartFromUri(pickupUri, "file1")
                ?: run {
                    // Nếu không có local pickupUri (nhưng server đã có ảnh rồi) -> chỉ cần reload.
                    loadOrder(orderId)
                    return@launch
                }
            // Tạm thời dùng cùng ảnh cho file2 để pass validation
            val file2Part = buildPartFromUri(pickupUri, "file2")!!

            _isLoading.value = true
            val uploadResult = orderRepository.uploadOrderImages(orderId, pickupPart, file2Part)
            uploadResult.onFailure { e ->
                _errorMessage.value = Event(e.message ?: "Upload pickup failed.")
            }
            // Dù upload thành công hay không, vẫn reload để sync trạng thái
            loadOrder(orderId)
            _isLoading.value = false
        }
    }

    /**
     * Giai đoạn DROP-OFF + COMPLETE:
     * - Upload đầy đủ (pickup + dropoff) để cập nhật ảnh chuẩn.
     * - Sau đó complete đơn.
     */
    fun uploadDropoffAndComplete(orderId: String, pickupUri: Uri?, dropoffUri: Uri?) {
        viewModelScope.launch {
            _isLoading.value = true

            // Build 2 parts. Nếu thiếu pickupUri (do trước đó đã upload), ta vẫn cần 2 file;
            // workaround: dùng dropoff cho cả 2 — backend nên cho update từng file trong tương lai.
            val file1 = buildPartFromUri(pickupUri ?: dropoffUri, "file1")
            val file2 = buildPartFromUri(dropoffUri ?: pickupUri, "file2")

            if (file1 == null || file2 == null) {
                _errorMessage.value = Event("Missing images to upload.")
                _isLoading.value = false
                return@launch
            }

            val uploadRes = orderRepository.uploadOrderImages(orderId, file1, file2)
            uploadRes.onFailure { e ->
                _errorMessage.value = Event(e.message ?: "Upload images failed.")
            }

            // Chuẩn bị body complete (tạm: lấy item hiện có, dùng quantity làm actual_quantity; payment = cash)
            val current = _orderDetail.value
            val completedItems = current?.items?.map { item ->
                CompletedOrderItemPayload(
                    orderItemId = item.id,
                    actualQuantity = item.quantity
                )
            } ?: emptyList()

            val completionBody = OrderCompletionRequest(
                paymentMethod = TransactionMethod.CASH,
                items = completedItems
            )


            val completeRes = orderRepository.completeOrder(orderId, completionBody)
            completeRes.onSuccess {
                _orderCompletedEvent.value = Event(true)
                // reload để đảm bảo đã completed
                loadOrder(orderId)
            }.onFailure { e ->
                _errorMessage.value = Event(e.message ?: "Complete order failed.")
            }

            _isLoading.value = false
        }
    }

    // ===== Helpers =====

    private suspend fun buildPartFromUri(uri: Uri?, formName: String): MultipartBody.Part? {
        if (uri == null) return null
        val resolver = getApplication<Application>().contentResolver

        return withContext(Dispatchers.IO) {
            try {
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val input: InputStream? = resolver.openInputStream(uri)
                val bytes = input?.readBytes() ?: return@withContext null
                val body = RequestBody.create(mime.toMediaTypeOrNull(), bytes)
                MultipartBody.Part.createFormData(formName, "photo.jpg", body)
            } catch (e: Exception) {
                null
            }
        }
    }
}
