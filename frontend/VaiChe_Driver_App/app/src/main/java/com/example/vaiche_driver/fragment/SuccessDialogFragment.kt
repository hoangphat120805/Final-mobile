package com.example.vaiche_driver.fragment

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.vaiche_driver.R

/**
 * Hiển thị khi app ở trạng thái FINDING_ORDER.
 */
class SuccessDialogFragment : DialogFragment() {

    private val TAG = "SuccessDialog"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.w(TAG, "onCreateView()")
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialog_success, container, false)
    }

    override fun onStart() {
        super.onStart()
        Log.w(TAG, "onStart() -> isCancelable=false")
        isCancelable = false
    }

    companion object {
        const val TAG = "SuccessScheduleDialog"
    }
}
