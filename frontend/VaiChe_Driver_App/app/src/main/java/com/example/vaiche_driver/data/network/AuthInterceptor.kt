package com.example.vaiche_driver.data.network

import android.content.Context
import com.example.vaiche_driver.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor này tự động thêm "Authorization" header vào mỗi request.
 * Nó sẽ đọc Access Token đã được lưu trong SharedPreferences.
 */
class AuthInterceptor(context: Context) : Interceptor {

    // Sử dụng SharedPreferences để lưu và đọc token
    private val sessionManager = SessionManager(context)


    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        // Lấy token từ SharedPreferences
        sessionManager.fetchAuthToken()?.let {
            // Nếu có token, thêm header "Authorization: Bearer [token]"
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        return chain.proceed(requestBuilder.build())
    }
}