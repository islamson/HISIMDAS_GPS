package fitech.tutorials.rsmgraphlast.data

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
}