package fitech.tutorials.rsmgraphlast.data.models

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Environment
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import fitech.tutorials.rsmgraphlast.MyApplication
import fitech.tutorials.rsmgraphlast.data.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt


class LocationViewModel : ViewModel(), SensorEventListener {
    private var kalmanFilter : KalmanFilter? = null
    private var totalData = 0
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var lastLocation: Location? = null
    private var isGPSReady : Boolean = false
    private var sensorManager : SensorManager? = null   //SensorManager to get data from AccelerationSensor
    private var location_first : Location? = null
    private var location_second : Location? = null
    private var location_third : Location? = null

    private var latLongConverter = GeoUtils
    private var xPositionTotal : Double = 0.0
    private var yPositionTotal : Double = 0.0

    private var dt : Double = 0.0 // Common time difference in seconds between two data for usage in Gps & acceleration
    private var currentLinearAcc = FloatArray(3)
    private var lastLinearAcc = FloatArray(3)
    private var isFirstAccData : Boolean = true
    private var rotationMatrix = FloatArray(9)
    private var a_world = FloatArray(3)
    private var currentAccTime : Long = 0L
    private var lastAccTime : Long = 0L

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

    private val _dataNumber = MutableStateFlow(0)
    val dataNumber = _dataNumber.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { currentLocation ->
                if (lastLocation == null) {
                    println("Bu ilk deneme. lastLocation = null burada.")
                    lastLocation = currentLocation
                    totalDistance = 0f
                    return
                }

//                location_first = location_second      Şimdilik dursun bunlar
//                location_second = location_third
//                location_third = currentLocation

                dt = (currentLocation.time - lastLocation!!.time) / 1000.0  //Time difference in seconds
                val xyPositions = latLongConverter.latLongToXY(currentLocation.latitude, currentLocation.longitude, lastLocation!!.latitude, lastLocation!!.longitude)
                xPositionTotal += xyPositions.first
                yPositionTotal += xyPositions.second
                totalDistance = sqrt(xPositionTotal * xPositionTotal + yPositionTotal * yPositionTotal).toFloat()

                viewModelScope.launch {
                    if(totalData < 30){
                        _speed.emit(0f)
                        _position.emit(0f)
                        println("GPS: X Position: ${xPositionTotal}, Y Position: ${yPositionTotal}")
                    }
                    else if (totalData == 30){
                        val lastXY = latLongConverter.latLongToXY(currentLocation.latitude, currentLocation.longitude, lastLocation!!.latitude, lastLocation!!.longitude)
                        val initialXVelocity = lastXY.first / dt
                        val initialYVelocity = lastXY.second / dt
                        xPositionTotal = 0.0
                        yPositionTotal = 0.0
                        totalDistance = 0f
                        kalmanFilter = KalmanFilter(1.0, 10.0)
                        kalmanFilter!!.init(0.0, 0.0, initialXVelocity, initialYVelocity)
                        println("GPS is ready now. initialXVelocity:${initialXVelocity}, initialYVelocity:${initialYVelocity}")
                        isGPSReady = true
                    }
                    else{
                        println("GPS: X Position: ${xPositionTotal}, Y Position: ${yPositionTotal}")
                        kalmanFilter!!.update(xPositionTotal, yPositionTotal)
                        _speed.emit(kalmanFilter!!.getSpeed())
                        _position.emit(totalDistance)
                    }
                    totalData++
                    _dataNumber.emit(totalData)
                }

                val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

                csvWriter?.apply {
                    write("$timeStamp,${currentLocation.latitude},${currentLocation.longitude},${totalDistance.roundToInt()},${_speed.value.roundToInt()}")
                    newLine()
                    flush()
                }
                lastLocation = currentLocation
            }
            // If speed difference is too large, ignore this location update
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(locationClient: FusedLocationProviderClient, sensorManager: SensorManager) {
        fusedLocationClient = locationClient
        _isTracking.value = true
        lastLocation = null
        totalDistance = 0f
        lastSpeed = 0f
        this.sensorManager = sensorManager

        val linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if(linearAccSensor != null){
            sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        }

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
        this.sensorManager!!.unregisterListener(this)
        lastLocation = null
        totalDistance = 0f
        lastSpeed = 0f
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient?.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR){
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            a_world = getWorldAcceleration(rotationMatrix, currentLinearAcc)
        }
        if(event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION){
            currentAccTime = event.timestamp
            val threshold = 0.02f
            for(i in 0..2){
                if(abs(currentLinearAcc[i]) < threshold){
                    currentLinearAcc[i] = 0f
                }

                if(abs(calculateNorm(event.values) - calculateNorm(lastLinearAcc)) >= 0.05f){
                    currentLinearAcc[i] = event.values[i]
                }
            }
            a_world = getWorldAcceleration(rotationMatrix, currentLinearAcc)
            //lastLinearAcceleration ile kontrole devam et unutmaAaAaAA!!!
            if(isFirstAccData){
                lastAccTime = currentAccTime
                isFirstAccData = false
                return
            }
            if(isGPSReady){
                dt = (currentAccTime - lastAccTime) / 1_000_000_000.0 //time difference in seconds
                if(dt > 0.1){
                    kalmanFilter!!.predict(a_world[0].toDouble(), a_world[1].toDouble(), dt)
                    val kalmanSpeed = kalmanFilter!!.getSpeed()
                    val kalmanPosition = kalmanFilter!!.getPosition()
                    viewModelScope.launch {
                        _speed.emit(kalmanSpeed)
                        _position.emit(kalmanPosition)
                    }
                    println("İvme: X: ${a_world[0]} Y: ${a_world[1]} Z: ${a_world[2]}")
                    lastLinearAcc = currentLinearAcc
                    lastAccTime = currentAccTime
                }
            }
            else{
                lastLinearAcc = currentLinearAcc
                lastAccTime = currentAccTime
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    fun getWorldAcceleration(RotationMatrix: FloatArray, acceleration : FloatArray) : FloatArray{
        a_world[0] = RotationMatrix[0] * acceleration[0] + RotationMatrix[1] * acceleration[1] + RotationMatrix[2] * acceleration[2]
        a_world[1] = RotationMatrix[3] * acceleration[0] + RotationMatrix[4] * acceleration[1] + RotationMatrix[5] * acceleration[2]
        a_world[2] = RotationMatrix[6] * acceleration[0] + RotationMatrix[7] * acceleration[1] + RotationMatrix[8] * acceleration[2]

        return a_world
    }

    fun calculateNorm(acceleration: FloatArray): Float {
        return sqrt(acceleration[0] * acceleration[0] + acceleration[1] * acceleration[1] + acceleration[2] * acceleration[2])
    }
}
