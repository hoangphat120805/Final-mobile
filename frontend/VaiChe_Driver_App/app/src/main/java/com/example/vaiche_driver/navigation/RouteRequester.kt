package com.example.vaiche_driver.navigation

import android.content.Context
import android.widget.Toast
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation

/**
 * Request route theo đúng API v3.11.x
 */
object RouteRequester {

    /**
     * origin, destination: Point(fromLngLat)
     * bearingDeg: nếu có hướng di chuyển hiện tại (để tránh U-turn), truyền vào. Có thể null.
     * allowAlternatives: có muốn trả về route thay thế hay không.
     */
    fun request(
        context: Context,
        mapboxNavigation: MapboxNavigation,
        origin: Point,
        destination: Point,
        bearingDeg: Double? = null,
        allowAlternatives: Boolean = true,
        onReady: (List<NavigationRoute>) -> Unit = {},
        onFailed: (List<RouterFailure>) -> Unit = { errs ->
            Toast.makeText(context, "Route failed: $errs", Toast.LENGTH_LONG).show()
        }
    ) {
        val builder = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(context)
            .coordinatesList(listOf(origin, destination))
            .alternatives(allowAlternatives)

        // Nếu có bearing hiện tại thì set để route hướng theo chiều di chuyển
        if (bearingDeg != null) {
            builder.bearingsList(
                listOf(
                    Bearing.builder().angle(bearingDeg).degrees(45.0).build(),
                    null
                )
            )
        }

        val routeOptions = builder.build()

        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: String
                ) {
                    // gán routes cho NavSDK để các observer (RoutesObserver/RouteLine/Camera) nhận
                    mapboxNavigation.setNavigationRoutes(routes)
                    onReady(routes)
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    onFailed(reasons)
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: String
                ) {
                    // no-op
                }
            }
        )
    }
}
