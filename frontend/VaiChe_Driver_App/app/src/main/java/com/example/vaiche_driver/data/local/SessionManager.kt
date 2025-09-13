package com.example.vaiche_driver.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Quản lý việc lưu trữ và truy xuất session token.
 */
class SessionManager(context: Context) {
    private var prefs: SharedPreferences =
        context.getSharedPreferences("VaicheDriverAppPrefs", Context.MODE_PRIVATE)

    companion object {
        const val AUTH_TOKEN = "auth_token"
    }

    fun saveAuthToken(token: String) {
        prefs.edit().putString(AUTH_TOKEN, token).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(AUTH_TOKEN, null)
    }

    fun clearAuthToken() {
        prefs.edit().remove(AUTH_TOKEN).apply()
    }

    /** ✨ Dùng khi muốn logout cứng, đảm bảo ghi xuống đĩa ngay */
    fun clearAllSync() {
        prefs.edit().clear().commit()   // commit = đồng bộ
    }
}
