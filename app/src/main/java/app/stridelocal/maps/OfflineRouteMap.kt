package app.stridelocal.maps

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
data class MapPoint(val latitude: Double, val longitude: Double, val key: String)

/** Shows a cached-only basemap and route trace. Network access is disabled before map setup. */
@Composable
fun OfflineRouteMap(points: List<MapPoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.setConnected(false)
        MapView(context).apply { onCreate(Bundle()) }
    }
    var configuredRoute by remember(mapView) { mutableStateOf("") }
    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier, update = {
        if (points.size < 2) return@AndroidView
        val signature = "${points.size}:${points.first().key}:${points.last().key}"
        if (configuredRoute == signature) return@AndroidView
        configuredRoute = signature
        MapLibre.setConnected(false)
        mapView.getMapAsync { map ->
            val route = LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
            val features = FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(route)))
            map.setStyle(OfflineMapPacks.STYLE_URL) { style ->
                style.addSource(GeoJsonSource("run-route", features))
                style.addLayer(LineLayer("run-route-line", "run-route").withProperties(
                    lineColor("#177D68"), lineWidth(4.0f),
                ))
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(points[points.size / 2].latitude, points[points.size / 2].longitude))
                    .zoom(13.0)
                    .build()
            }
        }
    })
}
