package com.example.vaicheuserapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class TransactionReadResponse(
    val id: String,
    @SerializedName("order_id") val orderId: String,
    val amount: Double,
    val method: TransactionMethod,
    val status: TransactionStatus,
    @SerializedName("transaction_date") val transactionDate: String,
    val payer: UserReadMinimal, // Assuming UserReadMinimal is Parcelable
    val payee: UserReadMinimal // Assuming UserReadMinimal is Parcelable
) : Parcelable

enum class TransactionMethod {
    @SerializedName("cash") CASH,
    @SerializedName("wallet") WALLET
    // Add others like "bank_transfer" if applicable from your UI
}

enum class TransactionStatus {
    @SerializedName("successful") SUCCESSFUL,
    @SerializedName("failed") FAILED,
    @SerializedName("pending") PENDING
}