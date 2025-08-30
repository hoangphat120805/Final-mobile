package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationPublic(
    val id: String, // uuid
    val title: String, // <-- ADDED
    val message: String,
    @SerializedName("is_read") // <-- CRITICAL: CHANGED FIELD NAME
    val isRead: Boolean,
    @SerializedName("created_at")
    val createdAt: String, // date-time string
) : Parcelable

// The API spec showed a complex UsersPublic schema, but for 'Get Notifications', it returns List<NotificationPublic>
// So, we don't need a specific response class for a list, Retrofit handles List<NotificationPublic> directly.