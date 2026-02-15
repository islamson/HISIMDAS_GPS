package fitech.tutorials.rsmgraphlast.data.location

import android.annotation.SuppressLint
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GpsEngine {
    fun defaultRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 50)
            .setMinUpdateIntervalMillis(50)
            .setWaitForAccurateLocation(true)
            .setMaxUpdateAgeMillis(50)
            .setIntervalMillis(50)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

    @SuppressLint("MissingPermission")
    fun updates(
        fused: FusedLocationProviderClient,
        request: LocationRequest
    ): Flow<Location> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { trySend(it).isSuccess }
            }
        }
        fused.requestLocationUpdates(request, callback, null)
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}