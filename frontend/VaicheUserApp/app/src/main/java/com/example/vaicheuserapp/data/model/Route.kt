package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class RoutePublic(
    @SerializedName("distance_meters") val distanceMeters: Double,
    @SerializedName("duration_seconds") val durationSeconds: Double,
    val polyline: String // Encoded polyline string
) : Parcelable