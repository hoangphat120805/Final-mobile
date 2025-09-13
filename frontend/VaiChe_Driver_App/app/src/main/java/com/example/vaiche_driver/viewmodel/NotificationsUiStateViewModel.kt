package com.example.vaiche_driver.fragment

import android.os.Parcelable
import androidx.lifecycle.ViewModel

enum class Tab { NOTI, MSG }

/** Giữ tab đang chọn và state cuộn cho từng tab */
class NotificationsUiStateViewModel : ViewModel() {
    var currentTab: Tab = Tab.NOTI
    var notiListState: Parcelable? = null
    var msgListState: Parcelable? = null
}
