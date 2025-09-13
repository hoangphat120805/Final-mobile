package com.example.vaiche_driver.navigation

import android.os.Build
import android.location.Location as AndroidLocation
import com.mapbox.common.location.Location as MbxLocation
import com.mapbox.maps.MapView
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState

class CameraController(private val mapView: MapView) {
    private val mapboxMap = mapView.getMapboxMap()
    private val viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
    private val navigationCamera = NavigationCamera(mapboxMap, mapView.camera, viewportDataSource)

    init {
        val density = mapView.context.resources.displayMetrics.density
        // Chừa chỗ cho maneuver (trên) + trip progress & bottom nav (dưới)
        viewportDataSource.followingPadding = EdgeInsets(
            180.0 * density, 40.0 * density, 150.0 * density, 40.0 * density
        )
        viewportDataSource.overviewPadding = EdgeInsets(
            140.0 * density, 40.0 * density, 120.0 * density, 40.0 * density
        )
        viewportDataSource.evaluate() // sau khi đổi padding
    }

    fun onRoutesChanged(routes: List<NavigationRoute>) {
        if (routes.isNotEmpty()) viewportDataSource.onRouteChanged(routes.first())
        else viewportDataSource.clearRouteData()
        viewportDataSource.evaluate()
        requestFollowing()
    }

    fun seedWith(location: AndroidLocation) {
        // Seed vị trí đầu vào trước khi request FOLLOWING
        viewportDataSource.onLocationChanged(toCommon(location))
        viewportDataSource.evaluate()
    }

    fun onEnhancedLocationChanged(location: AndroidLocation) {
        viewportDataSource.onLocationChanged(toCommon(location))
        viewportDataSource.evaluate()
        requestFollowing()
    }

    fun onRouteProgressChanged(progress: RouteProgress) {
        viewportDataSource.onRouteProgressChanged(progress)
        viewportDataSource.evaluate()
        requestFollowing()
    }

    fun requestFollowing() = navigationCamera.requestNavigationCameraToFollowing()
    fun requestOverview()  = navigationCamera.requestNavigationCameraToOverview()
    fun reset() {
        viewportDataSource.clearRouteData()
        viewportDataSource.evaluate()
        navigationCamera.requestNavigationCameraToIdle()
    }

    private fun toCommon(loc: AndroidLocation): MbxLocation = MbxLocation.Builder()
        .latitude(loc.latitude).longitude(loc.longitude)
        .altitude(loc.altitude).bearing(loc.bearing.toDouble())
        .speed(loc.speed.toDouble())
        .apply { if (Build.VERSION.SDK_INT >= 26) verticalAccuracy(loc.verticalAccuracyMeters?.toDouble() ?: 0.0) }
        .build()
}
