package com.example.vaiche_driver.data.network

import android.content.Context
import com.example.vaiche_driver.BuildConfig
import com.example.vaiche_driver.data.local.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Quản lý singleton Retrofit + ApiService.
 * - AuthInterceptor: gắn Authorization mỗi request bằng token đọc trực tiếp từ SessionManager.
 * - UnauthorizedAuthenticator (tuỳ chọn): khi 401 sẽ clear token local.
 */
object RetrofitClient {

    private const val BASE_URL = "http://160.30.192.11:8000/"

    @Volatile
    private var apiServiceInstance: ApiService? = null

    /**
     * Khởi tạo sớm (gọi ở Application.onCreate). An toàn khi gọi nhiều lần.
     */
    fun init(context: Context) {
        if (apiServiceInstance == null) {
            synchronized(this) {
                if (apiServiceInstance == null) {
                    apiServiceInstance = buildApiService(context.applicationContext)
                }
            }
        }
    }

    /**
     * Lấy instance, nếu chưa có sẽ tự build.
     */
    fun getInstance(context: Context): ApiService {
        return apiServiceInstance ?: synchronized(this) {
            apiServiceInstance ?: buildApiService(context.applicationContext).also {
                apiServiceInstance = it
            }
        }
    }

    /**
     * Truy cập nhanh sau khi đã init/getInstance trước đó.
     */
    val instance: ApiService
        get() = apiServiceInstance
            ?: throw IllegalStateException("RetrofitClient has not been initialized. Call init(context) or getInstance(context) first.")

    // ----------------------------------------

    private fun buildApiService(appContext: Context): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        val sessionManager = SessionManager(appContext)

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))          // truyền SessionManager
            .authenticator(UnauthorizedAuthenticator(appContext))     // thằng này vẫn cần Context
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    fun reset(context: Context) {
        synchronized(this) {
            apiServiceInstance = buildApiService(context.applicationContext)
        }
    }
}


/**
 * Tuỳ chọn: Khi gặp 401, clear token local.
 * Bạn có thể phát broadcast / dùng SharedViewModel để MainActivity điều hướng về Login.
 */
class UnauthorizedAuthenticator(private val appContext: Context) : okhttp3.Authenticator {
    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
        // Chỉ xử lý 401 nếu request có Authorization (tức là request protected)
        val hadAuth = response.request.header("Authorization")?.isNotBlank() == true
        if (!hadAuth) return null

        // Tránh vòng lặp: nếu đã thử lại rồi thì thôi
        if (response.priorResponse != null) return null

        // Token cũ có thể hết hạn -> xoá local
        SessionManager(appContext).clearAuthToken()
        // Không retry với token cũ
        return null
    }
}

