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

    private var lastProjectedLocation: Location? = null
    private var trackLocationData: List<Location>? = null

    private var trackPositionData: List<Double>? = null
    private var lastTrackSearchIdx: Int = 0
    private var initialTrackAbsPos: Float? = null

    private var isSystemReady: Boolean = false
    private var sensorManager: SensorManager? = null


    private var locationProcessor = LocationProcessor

    private var totalDistance = 0f
    private var totalProjectedDistance = 0f

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

    // --- Learned forward axis (device-frame horizontal direction of motion) ---
    private var forwardAxisX: Float = 0f
    private var forwardAxisY: Float = 0f
    private var forwardAxisReady: Boolean = false
    private var faSumXX: Double = 0.0
    private var faSumYY: Double = 0.0
    private var faSumXY: Double = 0.0
    private var faSumXa: Double = 0.0
    private var faSumYa: Double = 0.0
    private var faSampleCount: Int = 0

    private var rotationVectorValues: FloatArray? = null
    private val rotationMatrix = FloatArray(9)

    private var accOffsetX = 0f
    private var accOffsetY = 0f
    private var accOffsetZ = 0f

    private var accOffsetSampleCount = 0
    private var accOffsetSumX = 0f
    private var accOffsetSumY = 0f
    private var accOffsetSumZ = 0f
    private var accOffsetsReady = false

    private var lastTrackBearingDeg: Float? = null

    private var lastDisplayedGpsSpeedKmh = 0f

    private var filteredAccX: Float = 0f
    private var filteredAccY: Float = 0f
    private var filteredAccZ: Float = 0f

    private var lastVelocity: Float? = null
    private var currentVelocity: Float? = null

    private var lastAcceptedMeasuredSpeedKmh: Float? = null

    private val calibrationDataCount = homeViewModel.allConfigParams.value.calibrationDataNumber
    private val accSamplingTime =  0.02 //homeViewModel.allConfigParams.value.accSamplingTime Bunu düzelt unutma!
    private val gpsNoDataTime = homeViewModel.allConfigParams.value.gpsNoDataTime

    // GPS filtre parametreleri: reject yerine clamp + hafif adaptive smoothing
    private val maxReasonableSpeedKmh = 220f
    private val maxPositiveGpsAccMps2 = 2.2f
    private val maxNegativeGpsAccMps2 = 3.0f
    private val gpsSpeedMarginKmh = 6f

    private var fallbackStartedAtNs: Long? = null
    private var lastGpsElapsedRealtimeNs: Long = 0L

    private var gpsReacquireBlendCounter = 0
    private val gpsReacquireBlendSamples = 2
    private var lastFallbackSpeedBeforeGpsReturn = 0f

    private var rejectedByDt = 0
    private var rejectedBySpeed = 0
    private var rejectedBySpeedDiff = 0
    private var rejectedByAcceleration = 0

    // Loglama için tutulacak listler
    private val logGPSTime = mutableListOf<Double>()
    private val logGPSLat = mutableListOf<Double>()
    private val logGPSLon = mutableListOf<Double>()
    private val logGPSAlt = mutableListOf<Double>()
    private val logGPSPos = mutableListOf<Double>()
    private val logGPSSpeed = mutableListOf<Double>()
    private var logGPSDataNumber = 0
    private val logGPSIsAvailable = mutableListOf<Boolean>()

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

    suspend fun loadTrackFromAssets(context: Context, trackId: Int) {
        val selectedTrack = homeViewModel.selectedTrack.value

        trackLocationData = withContext(Dispatchers.IO) {
            val latitudes = selectedTrack?.latitude
            val longitudes = selectedTrack?.longitude
            val altitudes = selectedTrack?.altitude

            if (
                latitudes != null &&
                longitudes != null &&
                latitudes.isNotEmpty() &&
                longitudes.isNotEmpty() &&
                latitudes.size == longitudes.size
            ) {
                latitudes.indices.map { i ->
                    Location("track_model").apply {
                        latitude = latitudes[i]
                        longitude = longitudes[i]
                        if (altitudes != null && i < altitudes.size) {
                            altitude = altitudes[i]
                        }
                    }
                }
            } else {
                locationProcessor.loadTrackLocations(context, trackId)
            }
        }

        // Track'teki position listesini kaydet (hat metre bilgisi)
        trackPositionData = selectedTrack?.position

        Log.d("TRACK_LOAD", "trackLocationData size=${trackLocationData?.size}, trackPositionData size=${trackPositionData?.size}, trackPositionData null=${trackPositionData == null}")
        if (trackPositionData != null && trackPositionData!!.isNotEmpty()) {
            Log.d("TRACK_LOAD", "First pos=${trackPositionData!![0]}, Last pos=${trackPositionData!!.last()}")
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

                refreshMovementDurationNow()

                val currentConvertedLocation = rawLocation

                if (currentConvertedLocation.elapsedRealtimeNanos > 0L) {
                    lastGpsElapsedRealtimeNs = currentConvertedLocation.elapsedRealtimeNanos
                }

                // Fallback modundan GPS'e dönüşte ilk veriyi anchor olarak kullan
                if (gpsCalibrationCounter == 0) {
                    lastLocation = cloneLocation(currentConvertedLocation)
                    lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                    lastVelocity = _speed.value
                    lastAcceptedMeasuredSpeedKmh = _speed.value

                    lastFallbackSpeedBeforeGpsReturn = _speed.value
                    gpsReacquireBlendCounter = gpsReacquireBlendSamples

                    // GPS geri geldi — track arama indexini tam aramayla güncelle
                    val tLocs = trackLocationData
                    val tPos = trackPositionData
                    if (tLocs != null && tPos != null && tLocs.size == tPos.size) {
                        var bestI = 0
                        var bestD = Float.MAX_VALUE
                        for (i in tLocs.indices) {
                            val d = currentConvertedLocation.distanceTo(tLocs[i])
                            if (d < bestD) { bestD = d; bestI = i }
                        }
                        if (bestD < 200f) {
                            lastTrackSearchIdx = bestI
                            val newAbsPos = tPos[bestI].toFloat()
                            val initPos = initialTrackAbsPos
                            if (initPos != null) {
                                val dir = homeViewModel.selectedDirection.value ?: "West to East"
                                val newProjected = if (dir == "West to East") {
                                    (newAbsPos - initPos).coerceAtLeast(0f)
                                } else {
                                    (initPos - newAbsPos).coerceAtLeast(0f)
                                }
                                if (newProjected >= totalProjectedDistance) {
                                    totalProjectedDistance = newProjected
                                }
                            }
                            Log.d("TRACK_POS", "GPS reacquired: full search idx=$bestI pos=${tPos[bestI]} dist=${bestD}m")
                        }
                    }

                    gpsCalibrationCounter = 1
                    return
                }

                if (lastLocation == null || lastAcceptedGpsLocation == null) {
                    lastLocation = cloneLocation(currentConvertedLocation)
                    lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                    return
                }

                val acceptedPrevLocation = lastAcceptedGpsLocation!!

                val rawDtSec = if (
                    currentConvertedLocation.elapsedRealtimeNanos > 0L &&
                    acceptedPrevLocation.elapsedRealtimeNanos > 0L
                ) {
                    (currentConvertedLocation.elapsedRealtimeNanos - acceptedPrevLocation.elapsedRealtimeNanos) / 1_000_000_000.0
                } else {
                    (currentConvertedLocation.time - acceptedPrevLocation.time) / 1000.0
                }

                dt = rawDtSec

                if (rawDtSec <= 0.0) {
                    rejectedByDt++
                    lastLocation = cloneLocation(currentConvertedLocation)
                    return
                }

                val distanceMeters = acceptedPrevLocation.distanceTo(currentConvertedLocation)

                if (distanceMeters > 2f) {
                    lastTrackBearingDeg = acceptedPrevLocation.bearingTo(currentConvertedLocation)
                }

                val geometricSpeedKmh =
                    ((distanceMeters / rawDtSec) * 3.6).toFloat().coerceAtLeast(0f)

                val providerSpeedKmh =
                    if (currentConvertedLocation.hasSpeed()) {
                        (currentConvertedLocation.speed * 3.6f).coerceAtLeast(0f)
                    } else {
                        geometricSpeedKmh
                    }

                val rawSpeedKmh = (0.7f * providerSpeedKmh + 0.3f * geometricSpeedKmh)
                    .coerceAtMost(maxReasonableSpeedKmh)

                currentVelocity = rawSpeedKmh

                totalDistance += distanceMeters

                // Track position lookup (izdüşüm)
                val initAbsPos = initialTrackAbsPos
                if (initAbsPos != null) {
                    val trackLookupPos = lookupTrackPosition(currentConvertedLocation)
                    if (trackLookupPos != null) {
                        val dir = homeViewModel.selectedDirection.value ?: "West to East"
                        val newProjected = if (dir == "West to East") {
                            (trackLookupPos - initAbsPos).coerceAtLeast(0f)
                        } else {
                            (initAbsPos - trackLookupPos).coerceAtLeast(0f)
                        }
                        // Monoton artan: sadece ileri gitsin
                        if (newProjected >= totalProjectedDistance) {
                            totalProjectedDistance = newProjected
                        }
                        Log.d("TRACK_POS", "lookup=${trackLookupPos} init=$initAbsPos projected=$totalProjectedDistance idx=$lastTrackSearchIdx")
                    }
                }

                lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)

                var smoothedGpsSpeed = adaptiveGpsSpeedFilter(rawSpeedKmh, rawDtSec)

                Log.d(
                    "GPS_FILTER",
                    "dt=$rawDtSec dist=$distanceMeters provider=$providerSpeedKmh geom=$geometricSpeedKmh disp=$smoothedGpsSpeed"
                )

                if (gpsReacquireBlendCounter > 0) {
                    val blendRatio =
                        (gpsReacquireBlendSamples - gpsReacquireBlendCounter + 1).toFloat() / gpsReacquireBlendSamples.toFloat()

                    smoothedGpsSpeed =
                        (1f - blendRatio) * lastFallbackSpeedBeforeGpsReturn + blendRatio * smoothedGpsSpeed

                    gpsReacquireBlendCounter--
                }

                viewModelScope.launch {
                    if (totalData < calibrationDataCount) {
                        _speed.emit(0f)
                        _position.emit(0f)
                    } else if (totalData == calibrationDataCount) {
                        fallbackFilter = TrackFallbackFilter(
                            processAccelStd = 1.8,
                            positionStd = 3.0,
                            speedStd = 1.2
                        ).apply {
                            init(
                                initialPositionM = getDisplayedPosition().toDouble(),
                                initialSpeedMps = 0.0
                            )
                        }

                        isSystemReady = true
                        totalDistance = 0f
                        totalProjectedDistance = 0f
                        lastProjectedLocation = null

                        // Track lookup: başlangıç noktasını tam aramayla belirle
                        run {
                            Log.d("TRACK_POS", "=== INIT SEARCH START === gpsLat=${currentConvertedLocation.latitude} gpsLon=${currentConvertedLocation.longitude}")
                            val tLocs = trackLocationData
                            val tPos = trackPositionData
                            Log.d("TRACK_POS", "tLocs null=${tLocs==null} tPos null=${tPos==null} tLocsSize=${tLocs?.size} tPosSize=${tPos?.size}")
                            if (tLocs != null && tPos != null && tLocs.size == tPos.size && tLocs.isNotEmpty()) {
                                var bestI = 0
                                var bestD = Float.MAX_VALUE
                                for (i in tLocs.indices) {
                                    val d = currentConvertedLocation.distanceTo(tLocs[i])
                                    if (d < bestD) { bestD = d; bestI = i }
                                }
                                Log.d("TRACK_POS", "Full search done: bestI=$bestI bestD=$bestD bestPos=${tPos[bestI]}")
                                if (bestD < 200f) {
                                    initialTrackAbsPos = tPos[bestI].toFloat()
                                    lastTrackSearchIdx = bestI
                                    Log.d("TRACK_POS", "Initial track pos: ${tPos[bestI]} at index $bestI (dist=${bestD}m)")
                                } else {

                                }
                            } else {
                                Log.d("TRACK_POS", "CONDITION FAILED - skipping search")
                            }
                        }
                        lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                        lastAcceptedMeasuredSpeedKmh = 0f
                        // GPS smoothing'i gerçek hıza hemen yakınsın diye
                        // ilk gerçek GPS hızıyla başlat (0 değil)
                        val firstRealSpeed = if (currentConvertedLocation.hasSpeed())
                            (currentConvertedLocation.speed * 3.6f).coerceAtLeast(0f)
                        else rawSpeedKmh
                        lastDisplayedGpsSpeedKmh = firstRealSpeed

                        _speed.emit(0f)
                        _position.emit(0f)
                        updateAbsCenterFromPosition()
                        lastAcceptedGpsLocation = cloneLocation(currentConvertedLocation)
                        lastLocation = cloneLocation(currentConvertedLocation)

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
                            // Sürekli forward-axis öğrenme (device-frame horizontal yön)
                            // (Eski "1 kerelik accSign kalibrasyonu" bunun içine gömüldü)
                            run {
                                val prevProviderMps = (lastVelocity ?: smoothedGpsSpeed) / 3.6
                                val currProviderMps = if (currentConvertedLocation.hasSpeed())
                                    currentConvertedLocation.speed.toDouble().coerceAtLeast(0.0)
                                else gpsSpeedMps
                                val dtS = rawDtSec

                                if (dtS > 0.15 && dtS < 3.0) {
                                    val gpsAccMps2 = (currProviderMps - prevProviderMps) / dtS
                                    if (abs(gpsAccMps2) < 4.0) {
                                        val ax = lastAccX.toDouble()
                                        val ay = lastAccY.toDouble()
                                        faSumXX += ax * ax
                                        faSumYY += ay * ay
                                        faSumXY += ax * ay
                                        faSumXa += ax * gpsAccMps2
                                        faSumYa += ay * gpsAccMps2
                                        faSampleCount++

                                        if (faSampleCount >= 15) {
                                            val det = faSumXX * faSumYY - faSumXY * faSumXY
                                            var fx = 0.0
                                            var fy = 0.0
                                            if (abs(det) > 1e-6) {
                                                fx = (faSumYY * faSumXa - faSumXY * faSumYa) / det
                                                fy = (faSumXX * faSumYa - faSumXY * faSumXa) / det
                                            } else {
                                                // Rank-deficient — tek eksen dominant ise 1D regresyon
                                                if (faSumXX >= faSumYY && faSumXX > 1e-6) {
                                                    fx = faSumXa / faSumXX
                                                } else if (faSumYY > 1e-6) {
                                                    fy = faSumYa / faSumYY
                                                }
                                            }
                                            val norm = sqrt(fx * fx + fy * fy)
                                            if (norm > 1e-3) {
                                                forwardAxisX = (fx / norm).toFloat()
                                                forwardAxisY = (fy / norm).toFloat()
                                                forwardAxisReady = true
                                                Log.d("FWD_AXIS", "Learned fx=$forwardAxisX fy=$forwardAxisY (n=$faSampleCount)")
                                            }
                                        }
                                    }
                                }
                            }

                            // GPS varken UI tamamen GPS-only
                            _position.emit(getDisplayedPosition())
                            _speed.emit(smoothedGpsSpeed)
                            closedPositionCounter = 0
                            updateAbsCenterFromPosition()

                            // Fallback filtresini sıcak tut
                            fallbackFilter?.predict(getAccelerationAlongTrack().toDouble(), rawDtSec)
                            fallbackFilter?.update(
                                positionM = getDisplayedPosition().toDouble(),
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
                            logGPSPos.add(getGpsLogAbsolutePosition())
                            logGPSAlt.add(currentConvertedLocation.altitude)
                            logGPSLat.add(currentConvertedLocation.latitude)
                            logGPSLon.add(currentConvertedLocation.longitude)
                            logGPSTime.add(_trackMovementTime.value)
                            logGPSIsAvailable.add(true)
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

    /**
     * GPS koordinatını track üzerindeki en yakın noktaya eşleştirip
     * o noktanın hat metresini (absolute position) döndürür.
     * Yön bilgisine göre sadece ileri yönde ve sınırlı pencerede arar.
     * trackPositionData yoksa null döner.
     */
    private fun lookupTrackPosition(gpsLocation: Location): Float? {
        val trackLocs = trackLocationData ?: return null
        val trackPos = trackPositionData ?: return null
        if (trackLocs.isEmpty() || trackPos.isEmpty() || trackLocs.size != trackPos.size) return null

        val dir = homeViewModel.selectedDirection.value ?: "West to East"
        val isW2E = dir == "West to East"

        // Arama penceresi: son bulunan indexten itibaren 1000 ileri
        val startIdx: Int
        val endIdx: Int

        if (isW2E) {
            startIdx = (lastTrackSearchIdx - 10).coerceAtLeast(0)
            endIdx = (lastTrackSearchIdx + 1000).coerceAtMost(trackLocs.size - 1)
        } else {
            startIdx = (lastTrackSearchIdx - 1000).coerceAtLeast(0)
            endIdx = (lastTrackSearchIdx + 10).coerceAtMost(trackLocs.size - 1)
        }

        var bestIdx = lastTrackSearchIdx
        var bestDist = Float.MAX_VALUE

        for (i in startIdx..endIdx) {
            val d = gpsLocation.distanceTo(trackLocs[i])
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        // Çok uzaksa (>200m) güvenme
        if (bestDist > 200f) return null

        lastTrackSearchIdx = bestIdx
        return trackPos[bestIdx].toFloat()
    }

    private fun getDisplayedPosition(): Float {
        return if (!trackLocationData.isNullOrEmpty()) totalProjectedDistance else totalDistance
    }

    private fun refreshMovementDurationNow() {
        trackStartMs?.let { start ->
            _trackMovementTime.value = (System.currentTimeMillis() - start) / 1000.0
        }
    }

    private fun getGpsLogAbsolutePosition(): Double {
        val initialBerth = homeViewModel.selectedInitialStation.value?.berthingPosition ?: 0f
        val trainLength = homeViewModel.selectedTrain.value?.totalLength ?: 0.0
        val traveled = _position.value.toDouble()
        val dir = homeViewModel.selectedDirection.value ?: "West to East"

        return if (dir == "West to East") {
            initialBerth.toDouble() + (trainLength / 2.0) + traveled
        } else {
            initialBerth.toDouble() - (trainLength / 2.0) + traveled
        }
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
        lastProjectedLocation = null
        lastVelocity = null
        currentVelocity = null
        lastAcceptedMeasuredSpeedKmh = null

        totalDistance = 0f
        totalProjectedDistance = 0f
        totalData = 0
        _dataNumber.value = 0

        _speed.value = 0f
        _position.value = 0f
        _absCenterPos.value = 0f
        _trackMovementTime.value = 0.0


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

        forwardAxisX = 0f
        forwardAxisY = 0f
        forwardAxisReady = false
        faSumXX = 0.0
        faSumYY = 0.0
        faSumXY = 0.0
        faSumXa = 0.0
        faSumYa = 0.0
        faSampleCount = 0

        rotationVectorValues = null
        lastTrackBearingDeg = null

        accOffsetX = 0f
        accOffsetY = 0f
        accOffsetZ = 0f
        accOffsetSampleCount = 0
        accOffsetSumX = 0f
        accOffsetSumY = 0f
        accOffsetSumZ = 0f
        accOffsetsReady = false

        fallbackStartedAtNs = null
        lastGpsElapsedRealtimeNs = 0L

        lastDisplayedGpsSpeedKmh = 0f
        gpsReacquireBlendCounter = 0
        lastFallbackSpeedBeforeGpsReturn = 0f


        rejectedByDt = 0
        rejectedBySpeed = 0
        rejectedBySpeedDiff = 0
        rejectedByAcceleration = 0


        isFirstAccData = true
        currentAccTime = 0L
        lastAccTime = 0L
        currentLinearAcc = FloatArray(3)
        lastLinearAcc = FloatArray(3)

        lastTrackSearchIdx = 0
        initialTrackAbsPos = null
        logGPSIsAvailable.clear()

        this.sensorManager = sensorManager
        appContext = context.applicationContext
        trackStartMs = System.currentTimeMillis()
        _trackMovementTime.value = 0.0
        tripOriginAbsCenter = null

        val linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearAccSensor != null) {
            sensorManager.registerListener(this, linearAccSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
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

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 250L)
            .setMinUpdateIntervalMillis(100L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateAgeMillis(500L)
            .setMinUpdateDistanceMeters(0f)
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
            speed = logGPSSpeed.toList(),
            isGpsDataAvailable = logGPSIsAvailable.toList()
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
        logGPSIsAvailable.clear()

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
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                rotationVectorValues = event.values.clone()
                return
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
            }
            else -> return
        }

        refreshMovementDurationNow()
        currentAccTime = event.timestamp

        // Hafif EMA low-pass
        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        if (!accOffsetsReady && totalData < calibrationDataCount) {
            val rawNorm = sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
            if (rawNorm < 0.15f) {
                accOffsetSumX += rawX
                accOffsetSumY += rawY
                accOffsetSumZ += rawZ
                accOffsetSampleCount++
            }

            if (accOffsetSampleCount >= 40) {
                accOffsetX = accOffsetSumX / accOffsetSampleCount
                accOffsetY = accOffsetSumY / accOffsetSampleCount
                accOffsetZ = accOffsetSumZ / accOffsetSampleCount
                accOffsetsReady = true

                Log.d("ACC_CAL", "Offsets ready: x=$accOffsetX y=$accOffsetY z=$accOffsetZ")
            }
        }

        val calibratedX = rawX - accOffsetX
        val calibratedY = rawY - accOffsetY
        val calibratedZ = rawZ - accOffsetZ

        val alpha = 0.78f
        filteredAccX = alpha * calibratedX + (1f - alpha) * filteredAccX
        filteredAccY = alpha * calibratedY + (1f - alpha) * filteredAccY
        filteredAccZ = alpha * calibratedZ + (1f - alpha) * filteredAccZ

        currentLinearAcc[0] = filteredAccX
        currentLinearAcc[1] = filteredAccY
        currentLinearAcc[2] = filteredAccZ

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

        val timeDiffLastLocat = if (lastGpsElapsedRealtimeNs > 0L) {
            (currentAccTime - lastGpsElapsedRealtimeNs) / 1_000_000_000.0
        } else {
            0.0
        }
        dt = (currentAccTime - lastAccTime) / 1_000_000_000.0

        if (dt <= accSamplingTime) {
            return
        }

        val alongTrackAccSigned = getAccelerationAlongTrack().toDouble()
        val axSigned = (accSign * lastAccX).toDouble()
        val aySigned = (accSign * lastAccY).toDouble()
        val azSigned = (accSign * currentLinearAcc[2]).toDouble()

        if (timeDiffLastLocat > gpsNoDataTime) {
            if (fallbackStartedAtNs == null) {
                fallbackStartedAtNs = currentAccTime
            }

            val fallbackElapsedNs = currentAccTime - (fallbackStartedAtNs ?: currentAccTime)

            // GPS'ten ivmeye geçince çok kısa bir stabilizasyon
            if (fallbackElapsedNs < 150_000_000L) {
                lastLinearAcc = currentLinearAcc.copyOf()
                lastAccTime = currentAccTime
                return
            }

            val clampedAcc = alongTrackAccSigned.coerceIn(-2.5, 2.5)
            fallbackFilter?.predict(clampedAcc, dt)
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
            totalProjectedDistance = max(totalProjectedDistance, predictedPositionM)
            lastVelocity = null
            isGPSReady = false
            gpsCalibrationCounter = 0


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
            logGPSPos.add(getGpsLogAbsolutePosition())
            logGPSAlt.add(-1.0)
            logGPSLat.add(-1.0)
            logGPSLon.add(-1.0)
            logGPSTime.add(_trackMovementTime.value)
            logGPSIsAvailable.add(false)
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

    private fun getDominantAccelerationAxisValue(): Float {
        val ax = currentLinearAcc[0]
        val ay = currentLinearAcc[1]
        val az = currentLinearAcc[2]

        return when {
            abs(ax) >= abs(ay) && abs(ax) >= abs(az) -> ax
            abs(ay) >= abs(ax) && abs(ay) >= abs(az) -> ay
            else -> az
        }
    }

    fun calculateNorm(acceleration: FloatArray): Float {
        return sqrt(
            acceleration[0] * acceleration[0] +
                    acceleration[1] * acceleration[1] +
                    acceleration[2] * acceleration[2]
        )
    }

    private fun adaptiveGpsSpeedFilter(
        rawSpeedKmh: Float,
        dtSec: Double
    ): Float {
        val safeRaw = rawSpeedKmh.coerceIn(0f, maxReasonableSpeedKmh)

        if (lastDisplayedGpsSpeedKmh <= 0f) {
            lastDisplayedGpsSpeedKmh = safeRaw
            return lastDisplayedGpsSpeedKmh
        }

        val prev = lastDisplayedGpsSpeedKmh

        val maxUp = prev + (maxPositiveGpsAccMps2 * dtSec.toFloat() * 3.6f) + gpsSpeedMarginKmh
        val maxDown = prev - (maxNegativeGpsAccMps2 * dtSec.toFloat() * 3.6f) - gpsSpeedMarginKmh

        val clamped = safeRaw.coerceIn(
            maxDown.coerceAtLeast(0f),
            maxUp.coerceAtLeast(0f)
        )

        val diff = clamped - prev

        val alpha = when {
            diff < -8f -> 0.92f
            diff < -3f -> 0.82f
            diff > 8f -> 0.78f
            diff > 3f -> 0.65f
            else -> 0.35f
        }

        val out = prev + alpha * (clamped - prev)
        val stopped = if (out < 3.5f && safeRaw < 5f) 0f else out
        lastDisplayedGpsSpeedKmh = stopped.coerceAtLeast(0f)
        return lastDisplayedGpsSpeedKmh
    }

    private fun smoothFallbackSpeed(rawSpeed: Float): Float {
        val prev = _speed.value
        val diff = rawSpeed - prev

        val alpha = when {
            diff < -6f -> 0.95f
            diff > 6f -> 0.92f
            else -> 0.82f
        }

        return (prev + alpha * diff).coerceAtLeast(0f)
    }

    private fun cloneLocation(location: Location): Location {
        return Location(location)
    }

    private fun getAccelerationAlongTrack(): Float {
        if (!forwardAxisReady) return 0f
        val ax = currentLinearAcc[0]
        val ay = currentLinearAcc[1]
        // Öğrenilmiş forward ekseninin üzerine skaler projeksiyon
        return ax * forwardAxisX + ay * forwardAxisY
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