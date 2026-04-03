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
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.location.*
import com.google.gson.Gson
import fitech.tutorials.rsmgraphlast.data.LocationProcessor
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DasLogsAcc
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DasLogsGps
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
import kotlin.math.max
import kotlin.math.sqrt

class LocationViewModel(private val homeViewModel: HomeViewModel) : ViewModel(), SensorEventListener {

    private var fallbackFilter: TrackFallbackFilter? = null

    private var totalData = 0
    private var isGPSReady = true
    private var gpsCalibrationCounter = 1

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var lastLocation: Location? = null               // son kabul edilen GPS (average edilmiş)
    private var lastAcceptedGpsLocation: Location? = null    // hız hesabı için son kabul edilen GPS

    private var isSystemReady: Boolean = false
    private var sensorManager: SensorManager? = null

    private var location_first: Location? = null
    private var location_second: Location? = null
    private var location_third: Location? = null

    private var locationProcessor = LocationProcessor

    private var totalDistance = 0f

    private var dt: Double = 0.0
    private var currentLinearAcc = FloatArray(3)
    private var lastLinearAcc = FloatArray(3)
    private var isFirstAccData: Boolean = true
    private var currentAccTime: Long = 0L
    private var lastAccTime: Long = 0L

    private var accSign: Float = 1f
    private var accSignCalibrated: Boolean = false
    private var lastAccX: Float = 0f
    private var lastAccY: Float = 0f

    private var filteredAccX: Float = 0f
    private var filteredAccY: Float = 0f
    private var filteredAccZ: Float = 0f

    private var lastVelocity: Float? = null
    private var currentVelocity: Float? = null

    private var lastAcceptedMeasuredSpeedKmh: Float? = null

    private val calibrationDataCount = homeViewModel.allConfigParams.value.calibrationDataNumber
    private val accSamplingTime = homeViewModel.allConfigParams.value.accSamplingTime
    private val gpsNoDataTime = homeViewModel.allConfigParams.value.gpsNoDataTime

    // GPS-only filtre parametreleri
    private val minValidGpsDtSec = 0.01
    private val maxReasonableSpeedKmh = 220f
    private val maxPositiveGpsAccMps2 = 9
    private val maxNegativeGpsAccMps2 = 12

    // EMA / median benzeri yumuşatma için
    private val recentMeasuredSpeeds = ArrayDeque<Float>()
    private val speedWindowSize = 3
    private var lastSmoothedGpsSpeed = 0f

    private var gpsReacquireBlendCounter = 0
    private val gpsReacquireBlendSamples = 4
    private var lastFallbackSpeedBeforeGpsReturn = 0f

    private var rejectedByDt = 0
    private var rejectedBySpeed = 0
    private var rejectedByAcceleration = 0

    // Loglama için tutulacak listler
    private val logGPSTime = mutableListOf<Double>()
    private val logGPSLat = mutableListOf<Double>()
    private val logGPSLon = mutableListOf<Double>()
    private val logGPSAlt = mutableListOf<Double>()
    private val logGPSPos = mutableListOf<Double>()
    private val logGPSSpeed = mutableListOf<Double>()
    private var logGPSDataNumber = 0

    private val logAccTime = mutableListOf<Double>()
    private val logAccX = mutableListOf<Double>()
    private val logAccY = mutableListOf<Double>()
    private val logAccZ = mutableListOf<Double>()
    private var logAccDataNumber = 0

    private lateinit var appContext: Context

    private var tripOriginAbsCenter: Double? = null
    private var trackStartMs: Long? = null

    private val _absCenterPos = MutableStateFlow(0f)
    val absCenterPos = _absCenterPos.asStateFlow()

    private val _trackMovementTime = MutableStateFlow(0.0)
    val trackMovementTime = _trackMovementTime.asStateFlow()

    // Bu fonksiyon çağrılmaya devam edebilir diye imzayı koruyoruz.
    // Yeni mimaride projected track kullanılmadığı için no-op.
    suspend fun loadTrackFromAssets(context: Context, trackId: Int) {
        withContext(Dispatchers.IO) {
            // no-op
        }
    }

    private var csvFile: File? = null
    private var csvWriter: BufferedWriter? = null

    private val _speed = MutableStateFlow(0f)
    val speed = _speed.asStateFlow()

    private val _position = MutableStateFlow(0f)
    val position = _position.asStateFlow()

    private val _isTracking = mutableStateOf(false)
    val isTracking = _isTracking

    private val _dataNumber = MutableStateFlow(0)
    val dataNumber = _dataNumber.asStateFlow()

    private var closedPositionCounter = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { rawLocation ->

                location_first = location_second
                location_second = location_third
                location_third = rawLocation

                val currentConvertedLocation =
                    if (location_first != null && location_second != null) {
                        locationProcessor.locationMeanCalculater(
                            location_first!!,
                            location_second!!,
                            location_third!!
                        )
                    } else {
                        rawLocation
                    }

                // Fallback modundan GPS'e dönüşte ilk veriyi anchor olarak kullan
                if (gpsCalibrationCounter == 0) {
                    lastLocation = cloneLocation(currentConvertedLocation)
                    lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                    lastVelocity = _speed.value
                    lastAcceptedMeasuredSpeedKmh = _speed.value

                    lastFallbackSpeedBeforeGpsReturn = _speed.value
                    gpsReacquireBlendCounter = gpsReacquireBlendSamples

                    gpsCalibrationCounter = 1
                    return
                }

                if (lastLocation == null || lastAcceptedGpsLocation == null) {
                    lastLocation = cloneLocation(currentConvertedLocation)
                    lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                    return
                }

                val acceptedPrevLocation = lastAcceptedGpsLocation!!
                val rawDtSec = (currentConvertedLocation.time - acceptedPrevLocation.time) / 1000.0
                dt = rawDtSec

                // dt saçmaysa GPS verisini hız hesabında kullanma, sadece anchor güncelle
                if (rawDtSec <= 0.0) {
                    lastLocation = cloneLocation(currentConvertedLocation)
                    return
                }

                val distanceMeters = acceptedPrevLocation.distanceTo(currentConvertedLocation)
                val measuredSpeedKmh = ((distanceMeters / rawDtSec) * 3.6).toFloat().coerceAtLeast(0f)
                currentVelocity = measuredSpeedKmh

                var isCurrentGpsValid = true

                if (rawDtSec < minValidGpsDtSec) {
                    isCurrentGpsValid = false
                    rejectedByDt++
                }

                if (measuredSpeedKmh > maxReasonableSpeedKmh) {
                    isCurrentGpsValid = false
                    rejectedBySpeed++
                }

                lastAcceptedMeasuredSpeedKmh?.let { prevMeasuredSpeed ->
                    val gpsAccMps2 = ((measuredSpeedKmh - prevMeasuredSpeed) / 3.6f) / rawDtSec.toFloat()

                    if (totalData > calibrationDataCount) {
                        if (gpsAccMps2 > maxPositiveGpsAccMps2 || gpsAccMps2 < -maxNegativeGpsAccMps2) {
                            isCurrentGpsValid = false
                            rejectedByAcceleration++
                        }
                    }
                }

                if (!isCurrentGpsValid) {
                    Log.d(
                        "GPS_FILTER",
                        "Rejected GPS -> dt=$rawDtSec, dist=$distanceMeters, speed=$measuredSpeedKmh, " +
                                "rejDt=$rejectedByDt, rejSpeed=$rejectedBySpeed, rejAcc=$rejectedByAcceleration"
                    )

                    lastLocation = cloneLocation(currentConvertedLocation)
                    lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                    return
                }

                // Kabul edilen GPS verisi
                totalDistance += distanceMeters
                lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                lastAcceptedMeasuredSpeedKmh = measuredSpeedKmh

                val filteredMeasuredSpeed = pushAndMedianLikeSmooth(measuredSpeedKmh)
                var smoothedGpsSpeed = smoothGpsSpeed(filteredMeasuredSpeed)

                if (gpsReacquireBlendCounter > 0) {
                    val blendRatio =
                        (gpsReacquireBlendSamples - gpsReacquireBlendCounter + 1).toFloat() / gpsReacquireBlendSamples.toFloat()

                    smoothedGpsSpeed =
                        (1f - blendRatio) * lastFallbackSpeedBeforeGpsReturn + blendRatio * smoothedGpsSpeed

                    gpsReacquireBlendCounter--
                }

                viewModelScope.launch {
                    trackStartMs?.let { start ->
                        _trackMovementTime.emit((System.currentTimeMillis() - start) / 1000.0)
                    }

                    if (totalData < calibrationDataCount) {
                        _speed.emit(0f)
                        _position.emit(0f)
                    } else if (totalData == calibrationDataCount) {
                        fallbackFilter = TrackFallbackFilter(
                            processAccelStd = 0.8,
                            positionStd = 3.0,
                            speedStd = 2.0
                        ).apply {
                            init(
                                initialPositionM = totalDistance.toDouble(),
                                initialSpeedMps = (smoothedGpsSpeed / 3.6).toDouble()
                            )
                        }

                        isSystemReady = true
                        lastSmoothedGpsSpeed = smoothedGpsSpeed

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
                    } else {
                        if (isGPSReady) {
                            val gpsSpeedMps = (smoothedGpsSpeed / 3.6).toDouble()
                            val nowMs = currentConvertedLocation.time

                            // 1 kerelik ivme yön kalibrasyonu
                            if (!accSignCalibrated) {
                                val prevV = (lastVelocity ?: 0f) / 3.6
                                val prevT = acceptedPrevLocation.time
                                val dtS = (nowMs - prevT) / 1000.0

                                if (dtS > 0.15 && dtS < 2.0) {
                                    val gpsAccMps2 = (gpsSpeedMps - prevV) / dtS
                                    val ax = lastAccX.toDouble()
                                    if (abs(gpsAccMps2) > 0.01 && abs(ax) > 0.01) {
                                        accSign = if (gpsAccMps2 * ax >= 0) 1f else -1f
                                        accSignCalibrated = true
                                        Log.d("ACC_SIGN", "Calibrated accSign=$accSign (gpsAcc=$gpsAccMps2, ax=$ax)")
                                    }
                                }
                            }

                            // GPS varken UI tamamen GPS-only
                            _position.emit(totalDistance)
                            _speed.emit(smoothedGpsSpeed)
                            closedPositionCounter = 0
                            updateAbsCenterFromPosition()

                            // Fallback filtresini sıcak tut
                            fallbackFilter?.predict((accSign * lastAccX).toDouble(), rawDtSec)
                            fallbackFilter?.update(
                                positionM = totalDistance.toDouble(),
                                speedMps = smoothedGpsSpeed.toDouble() / 3.6
                            )

                            val timeStamp = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss",
                                Locale.getDefault()
                            ).format(Date())

                            csvWriter?.apply {
                                write(
                                    "$timeStamp," +
                                            "${currentConvertedLocation.latitude}," +
                                            "${currentConvertedLocation.longitude}," +
                                            "${currentConvertedLocation.altitude}," +
                                            "${"-"},${"-"},${"-"}," +
                                            "${_position.value.toInt()}," +
                                            "${_speed.value.toInt()}"
                                )
                                newLine()
                                flush()
                            }

                            logGPSSpeed.add(_speed.value.toDouble())
                            logGPSPos.add(_position.value.toDouble())
                            logGPSAlt.add(currentConvertedLocation.altitude)
                            logGPSLat.add(currentConvertedLocation.latitude)
                            logGPSLon.add(currentConvertedLocation.longitude)
                            logGPSTime.add(_trackMovementTime.value)
                            logGPSDataNumber++
                        }

                        gpsCalibrationCounter++
                        if (gpsCalibrationCounter > 3) {
                            isGPSReady = true
                        }
                    }

                    totalData++
                    _dataNumber.emit(totalData)
                }

                lastLocation = cloneLocation(currentConvertedLocation)
                if (isGPSReady) {
                    lastVelocity = _speed.value
                }
            }
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
    fun startTracking(
        locationClient: FusedLocationProviderClient,
        sensorManager: SensorManager,
        context: Context
    ) {
        fusedLocationClient = locationClient
        _isTracking.value = true

        lastLocation = null
        lastAcceptedGpsLocation = null
        lastVelocity = null
        currentVelocity = null
        lastAcceptedMeasuredSpeedKmh = null

        totalDistance = 0f
        totalData = 0
        _dataNumber.value = 0

        location_first = null
        location_second = null
        location_third = null

        isSystemReady = false
        isGPSReady = true
        gpsCalibrationCounter = 1
        closedPositionCounter = 0

        fallbackFilter = null

        accSign = 1f
        accSignCalibrated = false
        lastAccX = 0f
        lastAccY = 0f
        filteredAccX = 0f
        filteredAccY = 0f
        filteredAccZ = 0f

        lastSmoothedGpsSpeed = 0f
        gpsReacquireBlendCounter = 0
        lastFallbackSpeedBeforeGpsReturn = 0f

        rejectedByDt = 0
        rejectedBySpeed = 0
        rejectedByAcceleration = 0
        recentMeasuredSpeeds.clear()

        isFirstAccData = true
        currentAccTime = 0L
        lastAccTime = 0L
        currentLinearAcc = FloatArray(3)
        lastLinearAcc = FloatArray(3)

        this.sensorManager = sensorManager
        appContext = context.applicationContext
        trackStartMs = System.currentTimeMillis()
        tripOriginAbsCenter = null

        val linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAccSensor != null) {
            sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_UI)
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TrackingLog_$timeStamp.csv"

        val documentsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        csvFile = File(documentsDir, fileName)
        csvWriter = BufferedWriter(FileWriter(csvFile!!))
        csvWriter?.write("Timestamp,Latitude,Longitude,Altitude,X Acceleration(m/s²),Y Acceleration(m/s²),Z Acceleration(m/s²),Position(m),Speed(km/h)")
        csvWriter?.newLine()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 50)
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
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

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
        val refFile = homeViewModel.writeTripReferenceToFile(appContext)

        gpsFile.writeText(gson.toJson(gpsBody))
        accFile.writeText(gson.toJson(accBody))

        val token = homeViewModel.currentToken()
        val driverId = homeViewModel.driverId.value
        val trainId = homeViewModel.selectedTrain.value?.id
        val trackId = homeViewModel.selectedTrack.value?.id
        val direction = homeViewModel.selectedDirection.value ?: "West to East"

        if (token.isNullOrBlank() || driverId == null || trainId == null || trackId == null) {
            return
        }

        val netConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = workDataOf(
            UploadTripWorker.KEY_TOKEN to token,
            UploadTripWorker.KEY_GPS_PATH to gpsFile.absolutePath,
            UploadTripWorker.KEY_ACC_PATH to accFile.absolutePath,
            UploadTripWorker.KEY_REF_PATH to refFile.absolutePath,
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

        logGPSTime.clear()
        logGPSLat.clear()
        logGPSLon.clear()
        logGPSAlt.clear()
        logGPSPos.clear()
        logGPSSpeed.clear()

        logAccTime.clear()
        logAccX.clear()
        logAccY.clear()
        logAccZ.clear()

        logGPSDataNumber = 0
        logAccDataNumber = 0
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient?.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        currentAccTime = event.timestamp

        // Hafif EMA low-pass
        val alpha = 0.18f
        filteredAccX = alpha * event.values[0] + (1f - alpha) * filteredAccX
        filteredAccY = alpha * event.values[1] + (1f - alpha) * filteredAccY
        filteredAccZ = alpha * event.values[2] + (1f - alpha) * filteredAccZ

        currentLinearAcc[0] = if (abs(filteredAccX) < 0.03f) 0f else filteredAccX
        currentLinearAcc[1] = if (abs(filteredAccY) < 0.03f) 0f else filteredAccY
        currentLinearAcc[2] = if (abs(filteredAccZ) < 0.03f) 0f else filteredAccZ

        lastAccX = currentLinearAcc[0]
        lastAccY = currentLinearAcc[1]

        if (isFirstAccData) {
            lastAccTime = currentAccTime
            isFirstAccData = false
            return
        }

        if (!isSystemReady || lastLocation == null) {
            lastLinearAcc = currentLinearAcc.copyOf()
            lastAccTime = currentAccTime
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeDiffLastLocat = (currentTime - lastLocation!!.time) / 1000.0
        dt = (currentAccTime - lastAccTime) / 1_000_000_000.0

        if (dt <= accSamplingTime) {
            return
        }

        val axSigned = (accSign * lastAccX).toDouble()
        val aySigned = (accSign * lastAccY).toDouble()
        val azSigned = (accSign * currentLinearAcc[2]).toDouble()

        if (timeDiffLastLocat > gpsNoDataTime) {
            fallbackFilter?.predict(axSigned, dt)
            lastFallbackSpeedBeforeGpsReturn = _speed.value
            val predictedSpeedKmh = fallbackFilter?.getSpeedKmh()?.coerceAtLeast(0f) ?: _speed.value
            val predictedPositionM = fallbackFilter?.getPositionM()?.toFloat()?.coerceAtLeast(_position.value) ?: _position.value

            viewModelScope.launch {
                val displaySpeed = smoothFallbackSpeed(predictedSpeedKmh)

                if (predictedPositionM - _position.value < 0.05f && displaySpeed < 2f) {
                    closedPositionCounter++
                    if (closedPositionCounter >= 15) {
                        _speed.emit(0f)
                    } else {
                        _speed.emit(displaySpeed)
                    }
                } else {
                    closedPositionCounter = 0
                    _speed.emit(displaySpeed)
                    _position.emit(predictedPositionM)
                    updateAbsCenterFromPosition()
                }
            }

            totalDistance = max(totalDistance, predictedPositionM)
            lastVelocity = null
            isGPSReady = false
            gpsCalibrationCounter = 0
            location_first = null
            location_second = null
            location_third = null

            val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

            csvWriter?.apply {
                write(
                    "$timeStamp," +
                            "${"-"},${"-"},${"-"}," +
                            "$axSigned,$aySigned,$azSigned," +
                            "${_position.value.toInt()}," +
                            "${_speed.value.toInt()}"
                )
                newLine()
                flush()
            }

            // GPS yokken de GPS log listesine placeholder olarak ekleme mantığını koruyoruz
            logGPSSpeed.add(_speed.value.toDouble())
            logGPSPos.add(_position.value.toDouble())
            logGPSAlt.add(-1.0)
            logGPSLat.add(-1.0)
            logGPSLon.add(-1.0)
            logGPSTime.add(-timeDiffLastLocat)
            logGPSDataNumber++
        }

        lastLinearAcc = currentLinearAcc.copyOf()
        lastAccTime = currentAccTime

        logAccX.add(axSigned)
        logAccY.add(aySigned)
        logAccZ.add(azSigned)
        logAccTime.add(_trackMovementTime.value)
        logAccDataNumber++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    fun calculateNorm(acceleration: FloatArray): Float {
        return sqrt(
            acceleration[0] * acceleration[0] +
                    acceleration[1] * acceleration[1] +
                    acceleration[2] * acceleration[2]
        )
    }

    private fun pushAndMedianLikeSmooth(speed: Float): Float {
        recentMeasuredSpeeds.add(speed)
        while (recentMeasuredSpeeds.size > speedWindowSize) {
            recentMeasuredSpeeds.removeFirstOrNull()
        }

        val sorted = recentMeasuredSpeeds.sorted()
        return when (sorted.size) {
            0 -> speed
            1 -> sorted[0]
            2 -> (sorted[0] + sorted[1]) / 2f
            else -> sorted[1]
        }
    }

    private fun smoothGpsSpeed(rawSpeed: Float): Float {
        val alpha = when {
            rawSpeed < 10f -> 0.18f
            rawSpeed < 20f -> 0.28f
            rawSpeed < 40f -> 0.40f
            else -> 0.52f
        }

        val out = if (lastSmoothedGpsSpeed == 0f) {
            rawSpeed
        } else {
            alpha * rawSpeed + (1f - alpha) * lastSmoothedGpsSpeed
        }

        lastSmoothedGpsSpeed = out.coerceAtLeast(0f)
        return lastSmoothedGpsSpeed
    }

    private fun smoothFallbackSpeed(rawSpeed: Float): Float {
        val alpha = 0.10f
        val out = alpha * rawSpeed + (1f - alpha) * _speed.value
        return out.coerceAtLeast(0f)
    }

    private fun cloneLocation(location: Location): Location {
        return Location(location)
    }
}

private class TrackFallbackFilter(
    private val processAccelStd: Double,
    private val positionStd: Double,
    private val speedStd: Double
) {
    private var positionM = 0.0
    private var speedMps = 0.0

    // Kovaryans matrisi
    private var p00 = 10.0
    private var p01 = 0.0
    private var p10 = 0.0
    private var p11 = 10.0

    fun init(initialPositionM: Double, initialSpeedMps: Double) {
        positionM = initialPositionM
        speedMps = initialSpeedMps
        p00 = 5.0
        p01 = 0.0
        p10 = 0.0
        p11 = 5.0
    }

    fun predict(accMps2: Double, dtRaw: Double) {
        val dt = dtRaw.coerceIn(0.001, 1.0)

        positionM += speedMps * dt + 0.5 * accMps2 * dt * dt
        speedMps += accMps2 * dt
        if (speedMps < 0.0) speedMps = 0.0

        val q = processAccelStd * processAccelStd
        val dt2 = dt * dt
        val dt3 = dt2 * dt
        val dt4 = dt2 * dt2

        val q00 = q * dt4 / 4.0
        val q01 = q * dt3 / 2.0
        val q10 = q * dt3 / 2.0
        val q11 = q * dt2

        val newP00 = p00 + dt * (p10 + p01) + dt2 * p11 + q00
        val newP01 = p01 + dt * p11 + q01
        val newP10 = p10 + dt * p11 + q10
        val newP11 = p11 + q11

        p00 = newP00
        p01 = newP01
        p10 = newP10
        p11 = newP11
    }

    fun update(positionM: Double, speedMps: Double) {
        // H = I olduğu için sade form
        val r00 = positionStd * positionStd
        val r11 = speedStd * speedStd

        val s00 = p00 + r00
        val s01 = p01
        val s10 = p10
        val s11 = p11 + r11

        val det = s00 * s11 - s01 * s10
        if (abs(det) < 1e-9) return

        val invS00 = s11 / det
        val invS01 = -s01 / det
        val invS10 = -s10 / det
        val invS11 = s00 / det

        val k00 = p00 * invS00 + p01 * invS10
        val k01 = p00 * invS01 + p01 * invS11
        val k10 = p10 * invS00 + p11 * invS10
        val k11 = p10 * invS01 + p11 * invS11

        val y0 = positionM - this.positionM
        val y1 = speedMps - this.speedMps

        this.positionM += k00 * y0 + k01 * y1
        this.speedMps += k10 * y0 + k11 * y1
        if (this.speedMps < 0.0) this.speedMps = 0.0

        val newP00 = (1.0 - k00) * p00 - k01 * p10
        val newP01 = (1.0 - k00) * p01 - k01 * p11
        val newP10 = -k10 * p00 + (1.0 - k11) * p10
        val newP11 = -k10 * p01 + (1.0 - k11) * p11

        p00 = newP00
        p01 = newP01
        p10 = newP10
        p11 = newP11
    }

    fun getPositionM(): Double = positionM
    fun getSpeedKmh(): Float = (speedMps * 3.6).toFloat()
}