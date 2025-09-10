package com.example.vaiche_driver.data.common

import com.example.vaiche_driver.data.network.ApiService
import org.json.JSONObject
import retrofit2.Response

// Luôn lấy instance mới nhất của ApiService
typealias ApiProvider = () -> ApiService

// Hàm gọi API an toàn
suspend inline fun <T> safeApiCall(
    crossinline call: suspend () -> Response<T>
): Result<T> {
    return try {
        val response = call()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Lấy message lỗi đẹp hơn
fun <T> parseError(res: Response<T>): String {
    val raw = res.errorBody()?.string().orEmpty()
    val message = try {
        if (raw.isNotBlank()) {
            JSONObject(raw).optString("message").takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) { null }
    return message ?: "HTTP ${res.code()} ${res.message()}"
}

// Extension để map Result<T> -> Result<R>
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { Result.success(transform(it)) },
        onFailure = { Result.failure(it) }
    )
