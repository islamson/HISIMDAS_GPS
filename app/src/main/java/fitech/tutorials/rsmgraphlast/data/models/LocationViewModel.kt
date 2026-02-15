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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.location.*
import com.google.gson.Gson
import fitech.tutorials.rsmgraphlast.data.LocationProcessor
import fitech.tutorials.rsmgraphlast.work.UploadTripWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sqrt


class LocationViewModel(private val homeViewModel: HomeViewModel) : ViewModel(), SensorEventListener {
    private var kalmanFilter : KalmanFilter? = null
    private var totalData = 0
    private var isGPSReady = true
    private var gpsCalibrationCounter = 1
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var lastLocation: Location? = null

    private var lastProjectedLocation : Location? = null
    private var trackLocationData : List<Location>? = null

    private var isSystemReady : Boolean = false
    private var sensorManager : SensorManager? = null   // SensorManager to get data from AccelerationSensor
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

    // Loglama için tutulacak listler
    private val logGPSTime = mutableListOf<Double>()
    private val logGPSLat  = mutableListOf<Double>()
    private val logGPSLon  = mutableListOf<Double>()
    private val logGPSAlt  = mutableListOf<Double>()
    private val logGPSPos  = mutableListOf<Double>()
    private val logGPSSpeed  = mutableListOf<Double>()
    private var logGPSDataNumber = 0

    private val logAccTime = mutableListOf<Double>()
    private val logAccX    = mutableListOf<Double>()
    private val logAccY    = mutableListOf<Double>()
    private val logAccZ    = mutableListOf<Double>()
    private var logAccDataNumber = 0

    // Logları app local database ine kaydetmek için
    private lateinit var appContext: android.content.Context

    private var tripOriginAbsCenter: Double? = null
    private var trackStartMs: Long? = null

    private val _absCenterPos = MutableStateFlow(0f)
    val absCenterPos = _absCenterPos.asStateFlow()

    private val _trackMovementTime = MutableStateFlow(0.0) // seconds
    val trackMovementTime = _trackMovementTime.asStateFlow()

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
                // Last Location ile Current Location arasındaki dt 0.3 ten küçükse
                // O veriyi salla! Böylelikle min position difference 0.92 olursa 1 km/h altını elemiş olursun
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
                        return  //If maxSpeedDiff is so high, it means that the current gps data is anomaly, so we pass it and don't save in lastLocation object, if it was saved, the next valid data would be seen as anomaly because of the difference between valid currentLocation and invalid lastLocation
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
                    trackStartMs?.let { start ->
                        _trackMovementTime.emit((System.currentTimeMillis() - start) / 1000.0)
                    }

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
                        val dir = homeViewModel.selectedDirection.value ?: "West to East"
                        val sign = if (dir == "West to East") 1 else -1
                        val trainLength = homeViewModel.selectedTrain.value!!.totalLength
                        val initialBerth = homeViewModel.selectedInitialStation.value!!.berthingPosition

                        tripOriginAbsCenter = initialBerth + (sign * trainLength / 2.0)
                        homeViewModel.resetSegmentTimer()

                        homeViewModel.startCoastingSimLoop(
                            positionProvider = {
                                val origin = tripOriginAbsCenter ?: 0.0
                                val traveled = position.value.toDouble()
                                (if (dir == "West to East") origin + traveled else origin - traveled).toFloat()
                            },
                            speedProvider = { speed.value }
                        )

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
                                    if(currentVelocity!! <= 220f)
                                        _speed.emit(currentVelocity!!)
                                if(closedPositionCounter >= 8)
                                    _speed.emit(0f)
                            }

                            else{
                                closedPositionCounter = 0
                                if(currentVelocity!! <= 220f)
                                    _speed.emit(currentVelocity!!)
                                if(trackLocationData == null)
                                    _position.emit(totalDistance)
                                else
                                    _position.emit(totalProjectedDistance)
                            }

                            updateAbsCenterFromPosition()

                            val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                            csvWriter?.apply {
                                write("$timeStamp,${currentConvertedLocation.latitude},${currentConvertedLocation.longitude},${currentConvertedLocation.altitude},${"-"},${"-"},${"-"},${_position.value.roundToInt()},${_speed.value.roundToInt()}")
                                newLine()
                                flush()
                            }

                            //Loglama için current data ların listlere aktarımı
                            logGPSSpeed.add(_speed.value.toDouble())
                            logGPSPos.add(_position.value.toDouble())
                            logGPSAlt.add(currentConvertedLocation.altitude)
                            logGPSLat.add(currentConvertedLocation.latitude)
                            logGPSLon.add(currentConvertedLocation.longitude)
                            logGPSTime.add(_trackMovementTime.value)
                            logGPSDataNumber++
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
                    lastVelocity = _speed.value
            }
            // If speed difference is too large, ignore this location update
        }
    }

    private suspend fun updateAbsCenterFromPosition() {
        val origin = tripOriginAbsCenter ?: return
        val dir = homeViewModel.selectedDirection.value ?: "West to East"
        val traveled = _position.value.toDouble()
        val abs = if (dir == "West to East") origin + traveled else origin - traveled
        _absCenterPos.emit(abs.toFloat())
    }


    @SuppressLint("MissingPermission")
    fun startTracking(locationClient: FusedLocationProviderClient, sensorManager: SensorManager, context: Context) {
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
        appContext = context.applicationContext
        trackStartMs = System.currentTimeMillis()
        tripOriginAbsCenter = null // yeni trip başlıyor


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
        homeViewModel.stopCoastingSimLoop()
        csvWriter?.close()
        csvWriter = null
        csvFile = null
        _isTracking.value = false
        fusedLocationClient?.removeLocationUpdates(locationCallback)
        this.sensorManager?.unregisterListener(this)
        isSystemReady = false

        val createdAtUtc = OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)     // "2026-02-01T12:54:40+00:00"

        val gpsBody = DasLogsGps(
            createdAt = createdAtUtc,
            dataNumber = logGPSDataNumber,
            time = logGPSTime.toList(),
            latitude = logGPSLat.toList(),
            longitude = logGPSLon.toList(),
            altitude = logGPSAlt.toList(),
            position = logGPSPos.toList(),
            speed = logGPSSpeed.toList()
        )
        val accBody = DasLogsAcc(
            createdAt = createdAtUtc,
            dataNumber = logAccDataNumber,
            time = logAccTime.toList(),
            axisX = logAccX.toList(),
            axisY = logAccY.toList(),
            axisZ = logAccZ.toList()
        )

        val logsDir = File(appContext.filesDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()

        val gson = Gson()
        val gpsFile = File(logsDir, "gps_${UUID.randomUUID()}.json")
        val accFile = File(logsDir, "acc_${UUID.randomUUID()}.json")

        gpsFile.writeText(gson.toJson(gpsBody))
        accFile.writeText(gson.toJson(accBody))

        val token = homeViewModel.currentToken()
        val driverId = homeViewModel.driverId.value
        val trainId = homeViewModel.selectedTrain.value?.id
        val trackId = homeViewModel.selectedTrack.value?.id
        val direction = homeViewModel.selectedDirection.value ?: "West to East"

        if (token.isNullOrBlank() || driverId == null || trainId == null || trackId == null) {
            // kuyruğu kilitleme, sadece log dosyaları lokal kalır
            return
        }

        val netConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = workDataOf(
            UploadTripWorker.KEY_TOKEN to token,
            UploadTripWorker.KEY_GPS_PATH to gpsFile.absolutePath,
            UploadTripWorker.KEY_ACC_PATH to accFile.absolutePath,
            UploadTripWorker.KEY_DRIVER_ID to driverId,
            UploadTripWorker.KEY_TRAIN_ID to trainId,
            UploadTripWorker.KEY_TRACK_ID to trackId,
            UploadTripWorker.KEY_GENERAL_ID to 1,
            UploadTripWorker.KEY_DIRECTION to direction
        )

        val req = OneTimeWorkRequestBuilder<UploadTripWorker>()
            .setInputData(input)
            .setConstraints(netConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(appContext).enqueue(req)


        logGPSTime.clear(); logGPSLat.clear(); logGPSLon.clear()
        logGPSAlt.clear();  logGPSPos.clear(); logGPSSpeed.clear()
        logAccTime.clear(); logAccX.clear();   logAccY.clear(); logAccZ.clear()
        logGPSDataNumber = 0
        logAccDataNumber = 0
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
                val timeDiffLastLocat = (currentTime - lastLocation!!.time) / 1000.0    // Time difference in seconds between last location and current time
                dt = (currentAccTime - lastAccTime) / 1_000_000_000.0 // time difference in seconds
                if(dt > accSamplingTime){
                    // Calculate speed and position via KalmanFilter
                    kalmanFilter!!.predict(a_world[0].toDouble(), a_world[1].toDouble(), dt)
                    val kalmanPredictedSpeed = kalmanFilter!!.getSpeed().toInt()
                    val kalmanPredictedPosition = kalmanFilter!!.getPosition()
                    if(abs(kalmanPredictedSpeed - _speed.value) > maxSpeedDifference)
                        return  // If acc data is invalid, pass it and finishes the function so, data is not saved.

                    if(timeDiffLastLocat > gpsNoDataTime) {
                        viewModelScope.launch {
                            // First state is so low position diff
                            // It can because of either so frequent acc data or so low speed (almost 0)
                            if ((kalmanPredictedPosition - _position.value) < minPositionDifference/5f) { //Since data come from acc sensor more frequently, pos difference is less than gps data so we divided min diff to 5
                                closedPositionCounter++
                                if (abs(_speed.value - kalmanPredictedSpeed) > minSpeedDifference)
                                    _speed.emit(kalmanPredictedSpeed.toFloat())
                                Log.d("Closed Position", "closedPositionCounter:${closedPositionCounter}")
                                if (closedPositionCounter >= 15) {
                                    _speed.emit(0f) // To prevent oscillation of speed around 0
                                }
                            }
                            //Second state is remarkable position diff
                            //However, if speed is high enough and speed diff is almost 0, position diff still can be high, so check if speed diff is important or not before changing speed
                            else if ((kalmanPredictedPosition - _position.value) > minPositionDifference/5f) { // Since data come from acc sensor more frequently, pos difference is less than gps data so we divided min diff to 5
                                if (abs(_speed.value - kalmanPredictedSpeed) > minSpeedDifference)
                                    _speed.emit(kalmanPredictedSpeed.toFloat())
                                _position.emit(kalmanPredictedPosition)
                                updateAbsCenterFromPosition()
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
                        //Loglama için current data ların listlere aktarımı
                        logGPSSpeed.add(_speed.value.toDouble())
                        logGPSPos.add(_position.value.toDouble())
                        logGPSAlt.add(-1.0)
                        logGPSLat.add(-1.0)
                        logGPSLon.add(-1.0)
                        logGPSTime.add(-timeDiffLastLocat)
                        logGPSDataNumber++
                    }
                    lastLinearAcc = currentLinearAcc
                    lastAccTime = currentAccTime

                    //Acc Logları için
                    logAccX.add(a_world[0].toDouble())
                    logAccY.add(a_world[1].toDouble())
                    logAccZ.add(a_world[2].toDouble())
                    logAccTime.add(_trackMovementTime.value)
                    logAccDataNumber++
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
