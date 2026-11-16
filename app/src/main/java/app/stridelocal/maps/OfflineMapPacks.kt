package app.stridelocal.maps

import android.content.Context
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*
import org.maplibre.android.MapLibre
import org.json.JSONObject
import kotlin.math.*

data class OfflineMapPack(val id: Long, val name: String, val downloadedBytes: Long, val complete: Boolean)

object OfflineMapPacks {
    const val RADIUS_KM = 100.0
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    private const val TILE_LIMIT = 3_000L
    private const val TARGET_TILE_BUDGET = 2_000L
    private const val STOP_DOWNLOAD_BYTES = 48_000_000L
    private const val MAX_DOWNLOAD_BYTES = 50_000_000L

    fun downloadAround(
        context: Context,
        latitude: Double,
        longitude: Double,
        onStatus: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit,
    ) {
        require(latitude.isFinite() && latitude in -85.0..85.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        val latDelta = RADIUS_KM / 111.32
        val lonDelta = min(180.0, RADIUS_KM / (111.32 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01)))
        val south = (latitude - latDelta).coerceAtLeast(-85.0)
        val north = (latitude + latDelta).coerceAtMost(85.0)
        val west = (longitude - lonDelta).coerceAtLeast(-180.0)
        val east = (longitude + lonDelta).coerceAtMost(180.0)
        val bounds = LatLngBounds.Builder().include(LatLng(north, east)).include(LatLng(south, west)).build()
        val maxZoom = chooseMaxZoom(south, west, north, east)
        val definition = OfflineTilePyramidRegionDefinition(STYLE_URL, bounds, 5.0, maxZoom.toDouble(), 1.0f)
        val manager = OfflineManager.getInstance(context.applicationContext)
        manager.setOfflineMapboxTileCountLimit(TILE_LIMIT)
        val metadata = JSONObject().put("name", "100 km around ${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}")
            .put("provider", "OpenFreeMap").put("createdAt", System.currentTimeMillis()).toString().toByteArray()
        MapLibre.getInstance(context).setConnected(true)
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(region: OfflineRegion) {
                var complete = false
                region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (complete) return
                        val sizeMb = status.completedResourceSize / 1_000_000.0
                        if (status.isComplete) {
                            if (status.completedResourceSize >= MAX_DOWNLOAD_BYTES) {
                                complete = true
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                    override fun onDelete() = Unit
                                    override fun onError(error: String) = Unit
                                })
                                MapLibre.getInstance(context).setConnected(false)
                                onComplete(false, "Map download exceeded the 50 MB provider limit and was removed.")
                                return
                            }
                            complete = true
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            onStatus("Map saved offline (${"%.1f".format(sizeMb)} MB)")
                            MapLibre.getInstance(context).setConnected(false)
                            onComplete(true, "Map saved for offline use.")
                        } else {
                            onStatus("Downloading map: ${status.completedTileCount} tiles, ${"%.1f".format(sizeMb)} MB")
                            if (status.completedResourceSize >= STOP_DOWNLOAD_BYTES) {
                                complete = true
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                    override fun onDelete() = Unit
                                    override fun onError(error: String) = Unit
                                })
                                MapLibre.getInstance(context).setConnected(false)
                                onComplete(false, "Download stopped near the provider's 50 MB limit. Try a smaller map area.")
                            }
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        if (complete) return
                        complete = true
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() = Unit
                            override fun onError(error: String) = Unit
                        })
                        MapLibre.getInstance(context).setConnected(false)
                        onStatus("Map download error: $error")
                        onComplete(false, error.toString())
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        if (complete) return
                        complete = true
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() = Unit
                            override fun onError(error: String) = Unit
                        })
                        MapLibre.getInstance(context).setConnected(false)
                        onComplete(false, "The map exceeded the provider's $limit tile limit. Try a smaller area.")
                    }
                })
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                onStatus("Map download started at zoom levels 5–$maxZoom")
            }

            override fun onError(error: String) {
                MapLibre.getInstance(context).setConnected(false)
                onComplete(false, error)
            }
        })
    }

    fun list(context: Context, onResult: (List<OfflineMapPack>) -> Unit) {
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>) {
                if (regions.isEmpty()) { onResult(emptyList()); return }
                val packs = mutableListOf<OfflineMapPack>()
                var remaining = regions.size
                regions.forEach { region ->
                    var name = "Offline map ${region.id}"
                    try { name = JSONObject(String(region.metadata)).optString("name", name) } catch (_: Exception) { }
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus) {
                            packs += OfflineMapPack(region.id, name, status.completedResourceSize.coerceAtLeast(0L), status.isComplete)
                            remaining--
                            if (remaining == 0) onResult(packs.toList())
                        }
                        override fun onError(error: String) {
                            packs += OfflineMapPack(region.id, name, 0L, false)
                            remaining--
                            if (remaining == 0) onResult(packs.toList())
                        }
                    })
                }
            }

            override fun onError(error: String) { onResult(emptyList()) }
        })
    }

    fun delete(context: Context, id: Long, onResult: (Boolean) -> Unit) {
        OfflineManager.getInstance(context.applicationContext).getOfflineRegion(id, object : OfflineManager.GetOfflineRegionCallback {
            override fun onRegion(region: OfflineRegion) {
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = onResult(true)
                    override fun onError(error: String) = onResult(false)
                })
            }
            override fun onRegionNotFound() = onResult(false)
            override fun onError(error: String) = onResult(false)
        })
    }

    fun pauseAll(context: Context) {
        MapLibre.getInstance(context).setConnected(false)
        OfflineManager.getInstance(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>) = regions.forEach { it.setDownloadState(OfflineRegion.STATE_INACTIVE) }
            override fun onError(error: String) = Unit
        })
    }

    private fun chooseMaxZoom(south: Double, west: Double, north: Double, east: Double): Int {
        for (zoom in 12 downTo 5) if (estimatedTiles(south, west, north, east, zoom) <= TARGET_TILE_BUDGET) return zoom
        return 5
    }

    private fun estimatedTiles(south: Double, west: Double, north: Double, east: Double, maxZoom: Int): Long {
        var total = 0L
        for (zoom in 5..maxZoom) {
            val n = 1L shl zoom
            val x0 = floor((west + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1)
            val x1 = floor((east + 180.0) / 360.0 * n).toLong().coerceIn(0, n - 1)
            val y0 = tileY(north, n)
            val y1 = tileY(south, n)
            total += (x1 - x0 + 1).coerceAtLeast(0) * (y1 - y0 + 1).coerceAtLeast(0)
        }
        return total
    }

    private fun tileY(latitude: Double, n: Long): Long {
        val lat = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        return floor((1.0 - ln(tan(lat) + 1.0 / cos(lat)) / Math.PI) / 2.0 * n).toLong().coerceIn(0, n - 1)
    }
}
