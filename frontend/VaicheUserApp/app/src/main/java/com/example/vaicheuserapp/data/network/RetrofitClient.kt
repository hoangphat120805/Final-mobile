package com.example.vaicheuserapp.data.network

import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import okhttp3.Request
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL = "http://160.30.192.11:8000/"

    // We will hold a reference to the OkHttpClient
    private lateinit var okHttpClient: OkHttpClient

    lateinit var imageLoader: ImageLoader
        private set

    // Add an init block to be called from the Application class
    fun init(context: Context) {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context)) // Add our custom interceptor
            .addInterceptor(loggingInterceptor)
            .build()

        imageLoader = ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request: Request = chain.request().newBuilder()
                            // This header makes the request look like it's from a browser
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .build()
    }

    val instance: ApiService by lazy {
        if (!::okHttpClient.isInitialized) {
            throw IllegalStateException("RetrofitClient must be initialized in Application class")
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Use the configured client
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}