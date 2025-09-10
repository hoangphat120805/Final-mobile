// com.example.vaiche_driver.data.network.AuthInterceptor.kt
package com.example.vaiche_driver.data.network

import android.content.Context
import com.example.vaiche_driver.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.fetchAuthToken() // đọc mỗi lần
        val req = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else chain.request()
        return chain.proceed(req)
    }
}
