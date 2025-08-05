package fitech.tutorials.rsmgraphlast.data

import java.nio.DoubleBuffer

object GeoUtils {
    fun latLongToXY(latitude : Double, longitude : Double, originLatitude : Double, originLongitude : Double) : Pair<Double, Double>{
        val earthRadius = 6371000.0 // metre
        val dLat = Math.toRadians(latitude - originLatitude)
        val dLon = Math.toRadians(longitude - originLongitude)
        val meanLat = Math.toRadians((latitude + originLatitude) / 2.0)

        val x = dLon * earthRadius * Math.cos(meanLat)
        val y = dLat * earthRadius
        return Pair(x, y)
    }
}