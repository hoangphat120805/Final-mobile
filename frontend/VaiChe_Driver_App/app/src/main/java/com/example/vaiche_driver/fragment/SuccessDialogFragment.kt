package com.example.vaiche_driver.fragment

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.vaiche_driver.R

/**
 * Dialog này được hiển thị sau khi người dùng xác nhận kế hoạch (Set Plan),
 * trong khi ứng dụng đang ở trạng thái tìm kiếm đơn hàng mới (FINDING_ORDER).
 */
class SuccessDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Làm cho nền của cửa sổ Dialog trong suốt để thấy được bo góc của layout tùy chỉnh.
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Nạp layout cho dialog.
        return inflater.inflate(R.layout.dialog_success, container, false)
    }

    /**
     * onStart() được gọi sau khi dialog đã được hiển thị.
     * Đây là nơi tốt nhất để cấu hình các thuộc tính của dialog.
     */
    override fun onStart() {
        super.onStart()
        // Đặt isCancelable = false để ngăn người dùng đóng dialog này.
        // Nó sẽ chỉ được đóng lại theo logic của DashboardFragment.
        isCancelable = false
    }

    companion object {
        /**
         * Tag (nhãn) công khai để DashboardFragment có thể tìm và đóng dialog này
         * bằng cách sử dụng `parentFragmentManager.findFragmentByTag(TAG)`.
         */
        const val TAG = "SuccessScheduleDialog"
    }
}