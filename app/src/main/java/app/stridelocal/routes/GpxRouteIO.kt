package app.stridelocal.routes

import android.util.Xml
import app.stridelocal.analysis.RunAnalysis
import app.stridelocal.data.local.RoutePointEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class ImportedGpxRoute(val name: String, val points: List<RoutePointEntity>, val distanceMeters: Double)

object GpxRouteIO {
    fun read(input: InputStream): ImportedGpxRoute {
        val parser = Xml.newPullParser()
        // GPX is user-provided input. Disable document type declarations and
        // bound the number of parsed coordinates.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(input, "UTF-8")
        var name = "Imported route"
        val coordinates = mutableListOf<Triple<Double, Double, Double?>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "name" -> if (coordinates.isEmpty()) name = parser.nextText().ifBlank { name }
                    "trkpt", "rtept", "wpt" -> {
                        val pointTag = parser.name
                        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                            var elevation: Double? = null
                            var nested = parser.next()
                            while (!(nested == XmlPullParser.END_TAG && parser.name.equals(pointTag, ignoreCase = true))) {
                                if (nested == XmlPullParser.START_TAG && parser.name.equals("ele", ignoreCase = true)) {
                                    elevation = parser.nextText().toDoubleOrNull()
                                }
                                nested = parser.next()
                                if (nested == XmlPullParser.END_DOCUMENT) break
                            }
                            require(coordinates.size < MAX_POINTS) { "The GPX file contains too many route points." }
                            coordinates += Triple(lat, lon, elevation)
                        }
                    }
                }
            }
            event = parser.next()
        }
        require(coordinates.size >= 2) { "The GPX file does not contain a route with at least two points." }
        val points = coordinates.mapIndexed { index, coordinate ->
            RoutePointEntity(0L, index.toLong(), coordinate.first, coordinate.second, coordinate.third)
        }
        val distance = coordinates.zipWithNext().sumOf { (a, b) -> RunAnalysis.distanceMeters(a.first, a.second, b.first, b.second) }
        return ImportedGpxRoute(name, points, distance)
    }

    private const val MAX_POINTS = 1_000_000
}
