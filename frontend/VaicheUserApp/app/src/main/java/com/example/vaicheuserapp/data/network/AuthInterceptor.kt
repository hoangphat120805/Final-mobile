package com.example.vaicheuserapp.data.network

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(context: Context) : Interceptor {

    private val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = sharedPreferences.getString("auth_token", null)

        // If a token exists, add it to the request header
        val newRequest = if (token != null) {
            Log.d("AuthInterceptor", "Attaching token to request: ${originalRequest.url}")
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            Log.w("AuthInterceptor", "No auth token found for request: ${originalRequest.url}")
            originalRequest // For requests like login/signup that don't need a token
        }

        return chain.proceed(newRequest)
    }
}