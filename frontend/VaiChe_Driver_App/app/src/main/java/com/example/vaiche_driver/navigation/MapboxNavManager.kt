package com.example.vaiche_driver.navigation

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.example.vaiche_driver.BuildConfig
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

/**
 * Quản lý lifecycle & instance MapboxNavigation theo v3.11.x
 * - Gọi setup() 1 lần (ví dụ trong Application hoặc Fragment.onCreate)
 * - Gọi attach(owner) khi màn hình sẵn sàng
 * - Lấy instance đồng bộ bằng current()
 */
object MapboxNavManager {

    /** Gọi 1 lần trước khi attach (Application.onCreate hoặc Fragment.onCreate). */
    fun setup(context: Context) {
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup {
                NavigationOptions.Builder(context)
                    .build()
            }
        }
    }

    /** Gắn lifecycle để MapboxNavigation được tạo & giữ theo vòng đời. */
    fun attach(owner: LifecycleOwner) {
        MapboxNavigationApp.attach(owner)
    }

    /** Tháo lifecycle khi không còn dùng (tùy tình huống). */
    fun detach(owner: LifecycleOwner) {
        MapboxNavigationApp.detach(owner)
    }

    /** Lấy instance đang active; trả về null nếu chưa được setup/attach (CREATED+). */
    fun current(): MapboxNavigation? = MapboxNavigationApp.current()
}
