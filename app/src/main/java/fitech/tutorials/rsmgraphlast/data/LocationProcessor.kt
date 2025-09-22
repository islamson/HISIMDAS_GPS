package fitech.tutorials.rsmgraphlast.data

import android.content.Context
import android.location.Location

object LocationProcessor {
    fun latLongToXY(latitude : Double, longitude : Double, originLatitude : Double, originLongitude : Double) : Pair<Double, Double>{
        val earthRadius = 6371000.0 // metre
        val dLat = Math.toRadians(latitude - originLatitude)
        val dLon = Math.toRadians(longitude - originLongitude)
        val meanLat = Math.toRadians((latitude + originLatitude) / 2.0)

        val x = dLon * earthRadius * Math.cos(meanLat)
        val y = dLat * earthRadius
        return Pair(x, y)
    }

    fun locationMeanCalculater(first_location : Location, second_location : Location, third_location : Location) : Location{
        val mean_latitude = (first_location.latitude + second_location.latitude + third_location.latitude) / 3.0
        val mean_longitude = (first_location.longitude + second_location.longitude + third_location.longitude) / 3.0
        val meanLocation = Location("meanCalculater").apply {
            this.latitude = mean_latitude
            this.longitude = mean_longitude
            this.time = third_location.time
        }
        return meanLocation
    }

    /** path içindeki en yakın Location’ı döndürür; yoksa null */
    fun findNearestPoint(current: Location, path: List<Location>): Location? {
        if (path.isEmpty()) return null
        var best: Location? = null
        var bestDist = Float.MAX_VALUE
        for (p in path) {
            val d = current.distanceTo(p)
            if (d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return best
    }

    /** current’ı en yakına “yapıştırılmış” yeni bir Location olarak döndürür */
    fun snapToNearest(current: Location, path: List<Location>): Location {
        val nearest = findNearestPoint(current, path) ?: return current
        // current’ın zaman/diğer alanlarını koruyup sadece lat/lon’u değiştiriyoruz
        return Location(current).apply {
            latitude = nearest.latitude
            longitude = nearest.longitude
        }
    }

    fun loadTrackLocations(context: Context, trackId: Int): List<Location> {
        val path = "TrackLocationData/$trackId.csv"
        context.assets.open(path).bufferedReader().use { br ->
            val lines = br.lineSequence()
                .filter { it.isNotBlank() }
                .toList()

            if (lines.isEmpty()) return emptyList()

            // İlk satır başlık, veriler ikinci satırdan itibaren
            val dataLines = lines.drop(1)
            if (dataLines.isEmpty()) return emptyList()

            // Ayraç tespiti (ilk veri satırına bak)
            val probe = dataLines.first()
            val delim = when {
                probe.contains(';') -> ';'
                probe.contains('\t') -> '\t'
                else -> ',' // default
            }

            val result = ArrayList<Location>(dataLines.size)
            for (line in dataLines) {
                val parts = line.split(delim).map { it.trim() }
                if (parts.size < 2) continue

                // TR ondalık desteği: "39,92123" → "39.92123"
                val lat = parts[0].toDoubleOrNull()
                val lon = parts[1].toDoubleOrNull()
                if (lat != null && lon != null) {
                    result += Location("track_csv").apply {
                        latitude = lat
                        longitude = lon
                    }
                }
            }
            return result
        }
    }

}