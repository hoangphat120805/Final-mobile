package com.example.vaiche_driver.data.local

import android.content.Context
import android.webkit.WebStorage
import com.bumptech.glide.Glide
import com.example.vaiche_driver.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieManager

object SessionCleaner {

    /**
     * Logout “mềm” sạch sẽ, KHÔNG kill app:
     * 1) Xoá prefs/token ĐỒNG BỘ (clearAllSync)
     * 2) Dọn WebView storage + cookies
     * 3) Dọn cache file (internal/external cache)
     * 4) Dọn Glide cache (disk + memory)
     * 5) Huỷ mọi WorkManager jobs
     * 6) Rebuild Retrofit (ngắt Authorization ngay lập tức)
     *
     * Gọi xong hàm này, hãy điều hướng về Login (xem MainActivity.logoutToLogin()).
     */
    suspend fun hardLogout(appContext: Context) {
        val ctx = appContext.applicationContext

        // ——— IO thread: các thao tác nặng ———
        withContext(Dispatchers.IO) {
            // 1) Xoá toàn bộ SharedPreferences (token, v.v.) ĐỒNG BỘ
            runCatching { SessionManager(ctx).clearAllSync() }

            // 2) Dọn WebView & Cookies (nếu app có webview/cookie)
            runCatching {
                WebStorage.getInstance().deleteAllData()
            }

            // 3) Dọn cache files
            runCatching { ctx.cacheDir?.deleteRecursively() }
            runCatching { ctx.externalCacheDir?.deleteRecursively() }
            runCatching { ctx.externalCacheDirs?.forEach { it?.deleteRecursively() } }

            // 4) Glide disk cache
            runCatching { Glide.get(ctx).clearDiskCache() }


            // (Tùy chọn) Nếu Room DB của bạn CHỈ là cache, có thể xoá như sau:
            // runCatching { ctx.databaseList()?.forEach { name -> ctx.deleteDatabase(name) } }
            // Hoặc: MyRoomDatabase.getInstance(ctx).clearAllTables()
        }

        // ——— Main thread: phần cần chạy trên UI ———
        // Glide memory cache phải clear trên main
        runCatching { Glide.get(ctx).clearMemory() }

        // Rebuild Retrofit/OkHttp để chắc chắn các request sau KHÔNG còn Authorization
        runCatching { RetrofitClient.reset(ctx) }
    }
}
