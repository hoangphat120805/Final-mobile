package com.example.vaiche_driver.viewmodel

/**
 * Một lớp bao bọc (wrapper) cho dữ liệu được gửi qua LiveData,
 * được thiết kế để chỉ xử lý nội dung của nó một lần duy nhất.
 *
 * Rất hữu ích cho các sự kiện như điều hướng, hiển thị SnackBar, Toast...
 * để tránh chúng bị kích hoạt lại một cách không mong muốn sau khi thay đổi cấu hình
 * (ví dụ: xoay màn hình), hoặc khi một observer mới đăng ký lắng nghe.
 *
 * @param T Loại dữ liệu của nội dung (ví dụ: Boolean, String, Int...).
 */
open class Event<out T>(private val content: T) {

    // Cờ để kiểm tra xem sự kiện này đã được xử lý hay chưa.
    // `private set` nghĩa là chỉ có thể ghi giá trị từ bên trong lớp này.
    var hasBeenHandled = false
        private set

    /**
     * Trả về nội dung của sự kiện và ngay lập tức đánh dấu nó là đã được xử lý.
     * Nếu sự kiện đã được xử lý trước đó, hàm này sẽ trả về null.
     * Đây là hàm chính bạn sẽ sử dụng trong observer của LiveData.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Trả về nội dung của sự kiện, ngay cả khi nó đã được xử lý.
     * Hữu ích trong trường hợp bạn chỉ cần xem (peek) giá trị mà không muốn "tiêu thụ" nó.
     */
    fun peekContent(): T = content
}