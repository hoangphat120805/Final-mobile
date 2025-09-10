package com.example.vaiche_driver.data.local

import android.content.Context
import com.bumptech.glide.Glide
import com.example.vaiche_driver.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.CookieManager

object SessionCleaner {

    suspend fun hardLogout(appContext: Context) {
        // 1) Token & prefs
        runCatching {
            // Nếu bạn dùng 1 prefs duy nhất:
            appContext.deleteSharedPreferences("VaicheDriverAppPrefs")
        }

        // 2) DB & cache
        runCatching {
            // Xoá cache app
            appContext.cacheDir?.deleteRecursively()
            appContext.externalCacheDir?.deleteRecursively()

            // Glide
            Glide.get(appContext).clearMemory()               // main thread
            withContext(Dispatchers.IO) { Glide.get(appContext).clearDiskCache() }
        }

        // 5) Reset retrofit / clear singletons in-memory
        runCatching {
            RetrofitClient.reset(appContext)          // hàm bạn đã thêm trong object RetrofitClient
            // Nếu bạn dùng CookieJar custom: cookieJar.clear() tại đây
        }
    }
}
