package com.example.vaiche_driver.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.vaiche_driver.BuildConfig

/**
 * Object này chịu trách nhiệm khởi tạo và cung cấp một instance duy nhất (singleton)
 * của Retrofit và ApiService cho toàn bộ ứng dụng.
 */
object RetrofitClient {

    // !!! THAY THẾ BẰNG ĐỊA CHỈ IP HOẶC DOMAIN CỦA BACKEND SERVER CỦA BẠN !!!
    // Nếu chạy backend trên cùng máy tính với emulator, dùng 10.0.2.2
    private const val BASE_URL = "http://160.30.192.11:8000/"
    /**
     * Khởi tạo OkHttpClient.
     * Chúng ta thêm một Logging Interceptor để có thể xem chi tiết các request/response
     * trong Logcat, rất hữu ích cho việc debug.
     */
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()

        // Chỉ log khi đang ở chế độ debug
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            builder.addInterceptor(loggingInterceptor)
        }

        // TODO: Thêm Interceptor để tự động gắn Access Token vào header cho các request cần xác thực
        // builder.addInterceptor(AuthInterceptor("YOUR_TOKEN_HERE"))

        builder.build()
    }

    /**
     * Khởi tạo Retrofit.
     * Nó được cấu hình với Base URL, OkHttpClient, và GsonConverterFactory
     * để tự động chuyển đổi JSON.
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Đây là instance của ApiService mà toàn bộ ứng dụng sẽ sử dụng để gọi API.
     * `lazy` đảm bảo rằng nó chỉ được khởi tạo một lần duy nhất khi được gọi lần đầu.
     */
    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}