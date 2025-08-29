package com.example.vaiche_driver.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.vaiche_driver.model.FakeDataSource
import com.example.vaiche_driver.model.OrderDetail
import com.example.vaiche_driver.model.OrderStatus

import com.example.vaiche_driver.fragment.OrderDetailFragment


/**
 * ViewModel này là "bộ não" quản lý dữ liệu và logic cho màn hình OrderDetailFragment.
 * Nó tương tác với nguồn dữ liệu (FakeDataSource) và cung cấp dữ liệu đã được xử lý
 * cho Fragment thông qua LiveData.
 */
class OrderDetailViewModel : ViewModel() {

    // LiveData để giữ chi tiết đơn hàng. Giao diện sẽ "lắng nghe" sự thay đổi của nó.
    // `private` _orderDetail để chỉ có ViewModel mới có thể thay đổi giá trị.
    private val _orderDetail = MutableLiveData<OrderDetail?>()
    // `public` orderDetail để Fragment có thể quan sát (observe) mà không thể thay đổi.
    val orderDetail: LiveData<OrderDetail?> = _orderDetail

    /**
     * Lấy dữ liệu chi tiết đơn hàng lần đầu từ DataSource.
     * @param orderId ID của đơn hàng cần tải.
     */
    fun loadOrder(orderId: String?) {
        // Lấy dữ liệu và cập nhật giá trị của LiveData.
        // Thao tác này sẽ kích hoạt observer trong Fragment.
        _orderDetail.value = FakeDataSource.getOrderDetailById(orderId)
    }

    /**
     * Xử lý logic sau khi chụp ảnh thành công.
     * @param uri URI của file ảnh đã chụp.
     * @param target Xác định đây là ảnh cho Pick-up hay Drop-off.
     */
    fun onPhotoTaken(uri: Uri, target: OrderDetailFragment.PhotoTarget) {
        val currentOrder = _orderDetail.value ?: return // Lấy giá trị hiện tại, nếu null thì thoát.

        // Cập nhật dữ liệu trong FakeDataSource
        FakeDataSource.updatePhotoUrl(currentOrder.id, uri.toString(), target)

        // Tải lại dữ liệu mới nhất từ DataSource để làm mới LiveData
        // Thao tác này sẽ tự động kích hoạt observer trong Fragment và vẽ lại giao diện.
        _orderDetail.value = FakeDataSource.getOrderDetailById(currentOrder.id)
    }

    /**
     * Xử lý logic khi người dùng xác nhận đã Pick-Up.
     */
    fun onPickupConfirmed() {
        val currentOrder = _orderDetail.value ?: return

        // Cập nhật trạng thái thành "delivering"
        FakeDataSource.updateOrderStatus(currentOrder.id, OrderStatus.delivering)

        // Tải lại dữ liệu mới nhất để làm mới LiveData
        _orderDetail.value = FakeDataSource.getOrderDetailById(currentOrder.id)
    }

    /**
     * Xử lý logic khi người dùng xác nhận đã Drop-Off.
     * Hàm này được gọi TRƯỚC KHI chuyển sang màn hình Rating.
     */
    fun onDeliveryCompleted() {
        val currentOrder = _orderDetail.value ?: return

        // Cập nhật trạng thái cuối cùng thành "completed"
        FakeDataSource.updateOrderStatus(currentOrder.id, OrderStatus.completed)

        // Không cần tải lại LiveData ở đây vì chúng ta sắp chuyển sang màn hình khác.
        // Màn hình Schedule sau khi quay về sẽ tự động tải lại danh sách mới nhất.
    }
}