package fitech.tutorials.rsmgraphlast.data.models

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import fitech.tutorials.rsmgraphlast.data.LocationProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sqrt


class LocationViewModel(homeViewModel: HomeViewModel) : ViewModel(), SensorEventListener {
    private var kalmanFilter : KalmanFilter? = null
    private var totalData = 0
    private var isGPSReady = true
    private var gpsCalibrationCounter = 1
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var lastLocation: Location? = null

    private var lastProjectedLocation : Location? = null
    private var trackLocationData : List<Location>? = null

    private var isSystemReady : Boolean = false
    private var sensorManager : SensorManager? = null   //SensorManager to get data from AccelerationSensor
    private var location_first : Location? = null
    private var location_second : Location? = null
    private var location_third : Location? = null

    private var locationProcessor = LocationProcessor
    private var xPositionTotal : Double = 0.0
    private var yPositionTotal : Double = 0.0
    private var totalDistance = 0f
    private var totalProjectedDistance = 0f

    private var dt : Double = 0.0 // Common time difference in seconds between two data for usage in Gps & acceleration
    private var currentLinearAcc = FloatArray(3)
    private var lastLinearAcc = FloatArray(3)
    private var isFirstAccData : Boolean = true
    private var rotationMatrix = FloatArray(9)
    private var a_world = FloatArray(3)
    private var currentAccTime : Long = 0L
    private var lastAccTime : Long = 0L

    private var lastVelocity: Float? = null
    private var currentVelocity : Float? = null
    private val maxSpeedDifference = homeViewModel.allConfigParams.value.maxSpeedDiff  // Maximum allowed speed difference in km/h
    private val minSpeedDifference = homeViewModel.allConfigParams.value.minSpeedDiff //Minimum allowed speed difference in km/h
    private val minPositionDifference = homeViewModel.allConfigParams.value.minPositionDiff //Minimum allowed position difference in m
    private val calibrationDataCount = homeViewModel.allConfigParams.value.calibrationDataNumber
    private val accSamplingTime = homeViewModel.allConfigParams.value.accSamplingTime
    private val gpsNoDataTime = homeViewModel.allConfigParams.value.gpsNoDataTime

    suspend fun loadTrackFromAssets(context: Context, trackId: Int){
        trackLocationData = withContext(Dispatchers.IO){
            locationProcessor.loadTrackLocations(context, trackId)
        }
    }

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

    //Closed Position Counter for Speed = 0
    private var closedPositionCounter = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { currentLocation ->
                var currentConvertedLocation = currentLocation
                var currentProjectedLocation = currentLocation
                if(gpsCalibrationCounter == 0){
                    lastLocation = currentLocation
                    lastProjectedLocation = currentLocation
                    lastVelocity = null
                }
                Log.d("Config params:", "minSpeedDiff:${minSpeedDifference} maksSpeedDiff:${maxSpeedDifference} minPositionDiff:${minPositionDifference} accDt:${accSamplingTime} gpsNoDt:${gpsNoDataTime} calibrationCount:${calibrationDataCount} isAutoEnabled:${homeViewModel.autoStateTransition.value}")
                if (lastLocation == null) {
                    println("Bu ilk deneme. lastLocation = null burada.")
                    lastLocation = currentLocation
                    return
                }
                location_first = location_second
                location_second = location_third
                location_third = currentLocation

                if(location_first != null && location_second != null)
                    currentConvertedLocation = locationProcessor.locationMeanCalculater(location_first!!, location_second!!, location_third!!)

                trackLocationData?.let{
                    currentProjectedLocation = locationProcessor.findNearestPoint(currentConvertedLocation, it)!!
                }
                if(lastProjectedLocation == null)
                    lastProjectedLocation = currentProjectedLocation

                dt = (currentConvertedLocation.time - lastLocation!!.time) / 1000.0  //Time difference in seconds
                val positionDifference = lastLocation!!.distanceTo(currentConvertedLocation)
                currentVelocity = positionDifference.div(dt).times(3.6).toFloat()

                lastVelocity?.let {
                    if(abs(it - currentVelocity!!) > maxSpeedDifference)
                        currentVelocity = it
                    else if(abs(it - currentVelocity!!) < minSpeedDifference){
                        currentVelocity = it
                    }
                    if(abs(it - currentVelocity!!) <= maxSpeedDifference){
                        if(positionDifference >= minPositionDifference){
                            totalDistance += positionDifference
                            totalProjectedDistance += lastProjectedLocation!!.distanceTo(currentProjectedLocation)
                        }
                    }
                }

                Log.d("GPS Measurement", "Last Velocity:${lastVelocity}, Curr Velocity:${currentVelocity}, Pos Diff:${positionDifference}")


                val xyPositions = locationProcessor.latLongToXY(currentConvertedLocation.latitude, currentConvertedLocation.longitude, lastLocation!!.latitude, lastLocation!!.longitude)
                xPositionTotal += xyPositions.first.absoluteValue
                yPositionTotal += xyPositions.second.absoluteValue

                viewModelScope.launch {
                    if(totalData < calibrationDataCount){
                        _speed.emit(0f)
                        _position.emit(0f)
                    }

                    else if (totalData == calibrationDataCount){
                        val lastXY = locationProcessor.latLongToXY(currentConvertedLocation.latitude, currentConvertedLocation.longitude, lastLocation!!.latitude, lastLocation!!.longitude)
                        val initialXVelocity = lastXY.first.absoluteValue / dt
                        val initialYVelocity = lastXY.second.absoluteValue / dt
                        xPositionTotal = 0.0
                        yPositionTotal = 0.0
                        kalmanFilter = KalmanFilter(1.0, 10.0)
                        kalmanFilter!!.init(0.0, 0.0, initialXVelocity, initialYVelocity)
                        println("GPS is ready now. initialXVelocity:${initialXVelocity}, initialYVelocity:${initialYVelocity}")
                        isSystemReady = true
                    }
                    else{
                        if(isGPSReady){
                            kalmanFilter!!.predict(0.0,0.0, dt)
                            kalmanFilter!!.update(xPositionTotal, yPositionTotal)
                            val kalmanUpdatedSpeed = kalmanFilter!!.getSpeed().toInt()
                            println("Mean Speed From Kalman:${kalmanUpdatedSpeed}, Gps Speed:${currentVelocity}")

                            if(lastLocation!!.distanceTo(currentConvertedLocation) < minPositionDifference){
                                closedPositionCounter++
                                if(abs(currentVelocity!! - lastVelocity!!) > minSpeedDifference)
                                    _speed.emit(currentVelocity!!)
                                if(closedPositionCounter >= 8)
                                    _speed.emit(0f)
                            }
                            else{
                                closedPositionCounter = 0
                                _speed.emit(currentVelocity!!)
                                if(trackLocationData == null)
                                    _position.emit(totalDistance)
                                else
                                    _position.emit(totalProjectedDistance)
                            }

                            val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                            csvWriter?.apply {
                                write("$timeStamp,${currentConvertedLocation.latitude},${currentConvertedLocation.longitude},${currentConvertedLocation.altitude},${"-"},${"-"},${"-"},${_position.value.roundToInt()},${_speed.value.roundToInt()}")
                                newLine()
                                flush()
                            }
                        }
                        gpsCalibrationCounter++
                        if(gpsCalibrationCounter > 3)
                            isGPSReady = true
                    }
                    totalData++
                    _dataNumber.emit(totalData)
                }
                lastProjectedLocation = currentProjectedLocation
                lastLocation!!.set(currentConvertedLocation)
                if(isGPSReady)
                    lastVelocity = currentVelocity
            }
            // If speed difference is too large, ignore this location update
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(locationClient: FusedLocationProviderClient, sensorManager: SensorManager) {
        fusedLocationClient = locationClient
        _isTracking.value = true
        lastLocation = null
        lastProjectedLocation = null
        lastVelocity = null
        totalDistance = 0f
        location_first = null
        location_second = null
        location_third = null
        totalData = 0
        _dataNumber.value = 0
        this.sensorManager = sensorManager

        val linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if(linearAccSensor != null){
            sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TrackingLog_$timeStamp.csv"

        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if(!documentsDir.exists())
            documentsDir.mkdirs()
        csvFile = File(documentsDir, fileName)
        csvWriter = BufferedWriter(FileWriter(csvFile!!))

        csvWriter?.write("Timestamp,Latitude,Longitude,Altitude,X Acceleration(m/s²),Y Acceleration(m/s²),Z Acceleration(m/s²),Position(m),Speed(km/h)")
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
        isSystemReady = false
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
            if(isSystemReady){
                val currentTime = System.currentTimeMillis()
                val timeDiffLastLocat = (currentTime - lastLocation!!.time) / 1000.0    //Time difference in seconds between last location and current time
                dt = (currentAccTime - lastAccTime) / 1_000_000_000.0 //time difference in seconds
                if(dt > accSamplingTime){
                    if(timeDiffLastLocat > gpsNoDataTime) {
                        kalmanFilter!!.predict(a_world[0].toDouble(), a_world[1].toDouble(), dt)
                        val kalmanPredictedSpeed = kalmanFilter!!.getSpeed().toInt()
                        val kalmanPredictedPosition = kalmanFilter!!.getPosition()
                        viewModelScope.launch {
                            //First state is so low position diff
                            //It can because of either so frequent acc data or so low speed (almost 0)
                            if ((kalmanPredictedPosition - _position.value) < minPositionDifference/5f) { //Since data come from acc sensor more frequently, pos difference is less than gps data so we divided min diff to 5
                                closedPositionCounter++
                                if (abs(_speed.value - kalmanPredictedSpeed) > minSpeedDifference)
                                    _speed.emit(kalmanPredictedSpeed.toFloat())
                                Log.d("Closed Position", "closedPositionCounter:${closedPositionCounter}")
                                if (closedPositionCounter >= 15) {
                                    _speed.emit(0f) //To prevent oscillation of speed around 0
                                }
                            }
                            //Second state is remarkable position diff
                            //However, if speed is so high and speed diff is almost 0, position diff still can be high, so check if speed diff is important or not before changing speed
                            else if ((kalmanPredictedPosition - _position.value) > minPositionDifference/5f) { //Since data come from acc sensor more frequently, pos difference is less than gps data so we divided min diff to 5
                                if (abs(_speed.value - kalmanPredictedSpeed) > minSpeedDifference)
                                    _speed.emit(kalmanPredictedSpeed.toFloat())
                                _position.emit(kalmanPredictedPosition)
                                closedPositionCounter = 0
                                Log.d("Closed Position", "counter 0 landı (ivme)")
                            }
                        }
                        totalProjectedDistance = _position.value
                        totalDistance = _position.value
                        lastVelocity = null
                        isGPSReady = false
                        gpsCalibrationCounter = 0
                        location_first = null
                        location_second = null
                        location_third = null
                        val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

                        csvWriter?.apply {
                            write("$timeStamp,${"-"},${"-"},${"-"},${a_world[0]},${a_world[1]},${a_world[2]},${_position.value.roundToInt()},${_speed.value.roundToInt()}")
                            newLine()
                            flush()
                        }
                    }
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
