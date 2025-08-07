package fitech.tutorials.rsmgraphlast.ui

import android.annotation.SuppressLint
import android.location.Location
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import fitech.tutorials.rsmgraphlast.MyApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class LocationViewModel : ViewModel() {
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var startLocation: Location? = null
    private var lastLocation: Location? = null
    private var totalDistance: Float = 0f
    private var lastSpeed: Float = 0f
    private val maxSpeedDifference = 10f // Maximum allowed speed difference in km/h
    private var csvFile : File? = null
    private var csvWriter : BufferedWriter? = null


    private val _speed = MutableStateFlow(0f)
    val speed = _speed.asStateFlow()

    private val _position = MutableStateFlow(0f)
    val position = _position.asStateFlow()

    private val _isTracking = mutableStateOf(false)
    val isTracking = _isTracking

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { currentLocation ->
                if (startLocation == null) {
                    println("Bu ilk deneme. startLocation = null burada.")
                    startLocation = currentLocation
                    lastLocation = currentLocation
                    totalDistance = 0f
                    lastSpeed = 0f
                    return
                }

                val timeDiffInSeconds = (currentLocation.time - lastLocation!!.time) / 1000f
                val distanceInMeters = lastLocation!!.distanceTo(currentLocation)
                var speedInKmh = (distanceInMeters / timeDiffInSeconds) * 3.6f // Convert m/s to km/h

                println("Current Speed: ${speedInKmh}, Distance: ${distanceInMeters}, Total Distance: $totalDistance timeDiff: ${timeDiffInSeconds}")
                println("Latitude: ${currentLocation.latitude}, Longitude: ${currentLocation.longitude}")

                // Check for outlier speed changes
                val speedDifference = kotlin.math.abs(speedInKmh - lastSpeed)
                
                if (speedDifference <= maxSpeedDifference) {
                    // Speed change is acceptable, update values
                    totalDistance += distanceInMeters

                    val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

                    csvWriter?.apply {
                        write("$timeStamp,${currentLocation.latitude},${currentLocation.longitude},${totalDistance.roundToInt()},${speedInKmh.roundToInt()}")
                        newLine()
                        flush()
                    }

                    if(speedDifference < 1.5f){
                        speedInKmh = lastSpeed
                    }

                    viewModelScope.launch {
                        _speed.emit(speedInKmh)
                        _position.emit(totalDistance)
                    }
                }
                // If speed difference is too large, ignore this location update

                lastSpeed = speedInKmh
                lastLocation = currentLocation
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(locationClient: FusedLocationProviderClient) {
        fusedLocationClient = locationClient
        _isTracking.value = true
        startLocation = null
        lastLocation = null
        totalDistance = 0f
        lastSpeed = 0f

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TrackingLog_$timeStamp.csv"

        val context = MyApplication.appContext
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        csvFile = File(documentsDir, fileName)
        csvWriter = BufferedWriter(FileWriter(csvFile!!))

        csvWriter?.write("Timestamp,Latitude,Longitude,Position(m),Speed(km/h)")
        csvWriter?.newLine()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,50)
            .setMinUpdateIntervalMillis(50)
            .setWaitForAccurateLocation(true)
            .setMaxUpdateAgeMillis(50)
            .setIntervalMillis(50)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .build()

        fusedLocationClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }

    fun stopTracking() {
        csvWriter?.close()
        csvWriter = null
        csvFile = null
        _isTracking.value = false
        fusedLocationClient?.removeLocationUpdates(locationCallback)
        startLocation = null
        lastLocation = null
        totalDistance = 0f
        lastSpeed = 0f

    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient?.removeLocationUpdates(locationCallback)
    }
}
