package com.example.vaiche_driver.navigation

import android.content.Context
import android.graphics.Color
import com.mapbox.maps.Style
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources

/**
 * Route line renderer cho Mapbox Navigation UI 3.11.x
 * - Dùng ApiOptions/ViewOptions
 * - Vanishing route line đã tích hợp sẵn
 * - Render qua Style
 */
class RouteRenderer(context: Context) {

    private val apiOptions = MapboxRouteLineApiOptions
        .Builder()
        .build()

    // 👇 Màu cho phần đã đi: TRONG SUỐT
    private val colorRes = RouteLineColorResources
        .Builder()
        .routeLineTraveledColor(Color.TRANSPARENT)
        .routeLineTraveledCasingColor(Color.TRANSPARENT)
        // (tuỳ chọn) làm casing nhạt hơn, màu còn lại giữ nguyên
        // .inactiveRouteLegsColor(…)
        .build()
    private val viewOptions = MapboxRouteLineViewOptions
        .Builder(context)
        .build()

    private val routeLineApi  = MapboxRouteLineApi(apiOptions)
    private val routeLineView = MapboxRouteLineView(viewOptions)

    /** Gán danh sách routes và vẽ lên map */
    fun setRoutes(style: Style, routes: List<NavigationRoute>) {
        routeLineApi.setNavigationRoutes(routes) { drawData ->
            routeLineView.renderRouteDrawData(style, drawData)
        }
    }

    /** Gọi trong RouteProgressObserver để cập nhật vanishing/active leg */
    fun onRouteProgress(style: Style, progress: RouteProgress) {
        routeLineApi.updateWithRouteProgress(progress) { update ->
            routeLineView.renderRouteLineUpdate(style, update)
        }
    }

    /** Xóa route line khỏi Style */
    fun clear(style: Style) {
        routeLineApi.clearRouteLine { cleared ->
            routeLineView.renderClearRouteLineValue(style, cleared)
        }
    }

    /** Hủy tác vụ nền khi destroy */
    fun cancel() {
        routeLineApi.cancel()
        routeLineView.cancel()
    }
}
