package com.example.vaiche_driver.data.network
import android.content.Context
import com.example.vaiche_driver.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
/**
Object này chịu trách nhiệm khởi tạo và cung cấp một instance duy nhất (singleton)
của Retrofit và ApiService cho toàn bộ ứng dụng.
 */
object RetrofitClient {
    private const val BASE_URL = "http://160.30.192.11:8000/"
    // @Volatile đảm bảo rằng giá trị của biến này luôn được đọc từ bộ nhớ chính,
// rất quan trọng trong môi trường đa luồng.
    @Volatile
    private var apiServiceInstance: ApiService? = null
    /**
    Hàm chính để lấy instance của ApiService.
    Nếu instance chưa được tạo, nó sẽ tạo mới.
    Các lần gọi sau sẽ trả về instance đã được tạo sẵn.
    @param context Cần cung cấp Context (thường là applicationContext) cho lần khởi tạo đầu tiên.
    @return Một instance của ApiService.
     */
    fun getInstance(context: Context): ApiService {
// Sử dụng kỹ thuật "Double-checked locking" để đảm bảo an toàn trong môi trường đa luồng
// và chỉ thực hiện việc khởi tạo một lần duy nhất.
        return apiServiceInstance ?: synchronized(this) {
            apiServiceInstance ?: buildApiService(context).also {
                apiServiceInstance = it
            }
        }
    }
    /**
    Một thuộc tính tiện ích để truy cập instance sau khi nó đã được khởi tạo.
    Sẽ báo lỗi nếu bạn cố gắng truy cập trước khi getInstance(context) được gọi.
    Hữu ích cho các Repository.
     */
    val instance: ApiService
        get() = apiServiceInstance ?: throw IllegalStateException("RetrofitClient has not been initialized. Call getInstance(context) first.")
    /**
    Hàm private để xây dựng toàn bộ hệ thống Retrofit.
     */
    private fun buildApiService(context: Context): ApiService {
// Cấu hình OkHttpClient
        val okHttpClient = OkHttpClient.Builder().apply {
// Thêm AuthInterceptor để tự động gắn token vào header của mỗi request
            addInterceptor(AuthInterceptor(context.applicationContext))
// Chỉ log body của request/response khi ở chế độ debug
            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor()
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                addInterceptor(loggingInterceptor)
            }

            // Thiết lập thời gian chờ (timeout) cho các kết nối
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            writeTimeout(30, TimeUnit.SECONDS)
        }.build()
// Cấu hình Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
// Tạo và trả về instance của ApiService
        return retrofit.create(ApiService::class.java)
    }
}