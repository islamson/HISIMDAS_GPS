package fitech.tutorials.rsmgraphlast.data.location.fusion

import android.location.Location
import fitech.tutorials.rsmgraphlast.data.LocationProcessor
import fitech.tutorials.rsmgraphlast.data.tracking.PositionSpeedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

data class FusionParams(
    val minSpeedDiff: Float,
    val maxSpeedDiff: Float,
    val minPositionDiff: Float,
    val calibrationDataNumber: Int,
    val accSamplingTimeSec: Double,
    val gpsNoDataTimeSec: Double,
    val initialBerthingOffset: Float,
    val trackPath: List<Location>? = null
)

interface KalmanLike {
    fun init(x: Double, y: Double, vx: Double, vy: Double)
    fun predict(ax: Double, ay: Double, dt: Double)
    fun update(xMeas: Double, yMeas: Double)
    fun getSpeed(): Double           // m/s
    fun getPosition(): Float         // m
}

class FusionEngine(
    private val scope: CoroutineScope,
    private val gps: Flow<Location>,
    private val kalman: KalmanLike,
    private val params: FusionParams,
    private val onCalibrationCompleted: (() -> Unit)? = null
) {
    // Ara değişkenler
    private var lastGpsLoc: Location? = null
    private var lastVelocity: Float? = null
    private var totalDistance: Float = 0f

    // 3’lü moving average
    private var location_first: Location? = null
    private var location_second: Location? = null
    private var location_third: Location? = null

    // Kalman (GPS update için XY birikimi)
    private var xSum = 0.0
    private var ySum = 0.0
    private var isKalmanReady = false
    private var calibrationDone = false

    // GPS-ACC geçiş kontrolü
    private var lastGpsTimeInMs: Long = 0L
    private var isGpsReady = true
    private var gpsRecalibrationCount = 0

    // dur-kalk filtresi
    private var closedPosCounter = 0

    // Sayaç (UI progress)
    private var totalData = 0
    private val _dataNumber = MutableStateFlow(0)
    val dataNumber: StateFlow<Int> = _dataNumber.asStateFlow()

    private val _state = MutableStateFlow(PositionSpeedState())
    val state: StateFlow<PositionSpeedState> = _state.asStateFlow()

    fun run() {
        scope.launch { gps.collect { onGps(it) } }
    }

    // GPS Verisi İşleme
    private fun onGps(raw: Location) {
        // 1) moving average
        location_first = location_second
        location_second = location_third
        location_third = raw

        val meanLoc = if (location_first != null && location_second != null) {
            LocationProcessor.locationMeanCalculater(location_first!!, location_second!!, location_third!!)
        } else raw

        // 2) Projection (varsa)
        val projected = params.trackPath?.let { LocationProcessor.findNearestPoint(meanLoc, it) }
        val currentLocation = projected ?: meanLoc

        // İlk GPS: sadece referans kur
        if (lastGpsLoc == null) {
            lastGpsLoc = currentLocation
            lastGpsTimeInMs = System.currentTimeMillis()
            dataCounter()
            // kalibrasyon öncesi state 0
            _state.value = PositionSpeedState(params.initialBerthingOffset, 0f)
            return
        }

        val dtSec = (currentLocation.time - lastGpsLoc!!.time) / 1000.0


        // Calibration penceresi (ilk n örnek)
        if (!calibrationDone && totalData < params.calibrationDataNumber) {
            // sadece state'i 0 tut; birikimleri toplayabilirsin (gerek yoksa atla)
            _state.value = PositionSpeedState(params.initialBerthingOffset, 0f)
            lastGpsLoc = currentLocation
            lastGpsTimeInMs = System.currentTimeMillis()
            dataCounter()       // Bu dataCounter ları sor ya kullanılmayan datalar listeye de ekleniyor mu?
            return              // Yoksa sadece dataNumber artıyor ama list size artmıyor uyumsuzluk mu oluşuyor?
        }

        // tam kalibrasyon anı: Kalman init (yalnızca 1 kez)
        if (!calibrationDone && totalData == params.calibrationDataNumber) {
            val (dx0, dy0) = LocationProcessor.latLongToXY(
                currentLocation.latitude, currentLocation.longitude,
                lastGpsLoc!!.latitude,  lastGpsLoc!!.longitude
            )
            val initVx = abs(dx0) / dtSec
            val initVy = abs(dy0) / dtSec
            kalman.init(0.0, 0.0, initVx, initVy)
            isKalmanReady = true
            calibrationDone = true
            xSum = 0.0; ySum = 0.0
            _state.value = PositionSpeedState(params.initialBerthingOffset, 0f)
            onCalibrationCompleted?.invoke()
            lastGpsLoc = currentLocation
            lastGpsTimeInMs = System.currentTimeMillis()
            dataCounter()
            return
        }

        // ACC'den GPS'e dönüş ufak kalibrasyon penceresi (3 örnek)
        if (!isGpsReady && gpsRecalibrationCount < 3) {
            gpsRecalibrationCount++
            lastGpsLoc = currentLocation
            lastGpsTimeInMs = System.currentTimeMillis()
            dataCounter()
            return
        }

        // Buraya ulaştıysa zaten gpsRecalibrationCount 3 ten büyük ya da eşittir, yani gps tekrar kullanıma hazır.
        isGpsReady = true

        // 3) ham ölçümler
        val posDiff = lastGpsLoc!!.distanceTo(currentLocation)           // m
        var currentVelocity = (posDiff / dtSec.toFloat() * 3.6f)         // km/h

        // 4) min/max hız + min mesafe filtreleri
        lastVelocity?.let { lastV ->
            if (abs(lastV - currentVelocity) > params.maxSpeedDiff) {
                lastGpsLoc = currentLocation
                lastGpsTimeInMs = System.currentTimeMillis()
                dataCounter()
                return
            }
            if (posDiff >= params.minPositionDiff) totalDistance += posDiff
            if (abs(lastV - currentVelocity) < params.minSpeedDiff) currentVelocity = lastV
        } ?: run {
            if (posDiff >= params.minPositionDiff) totalDistance += posDiff
        }

        // 5) Kalman’ı canlı tut (GPS varken predict+update)
        if (isKalmanReady) {
            val (dx, dy) = LocationProcessor.latLongToXY(
                currentLocation.latitude, currentLocation.longitude,
                lastGpsLoc!!.latitude,  lastGpsLoc!!.longitude
            )
            xSum += abs(dx)
            ySum += abs(dy)
            kalman.predict(0.0, 0.0, dtSec)
            kalman.update(xSum, ySum)
        }

        // 6) Son Hız 0 mı diye son kontrol yapıp işlenmiş verileri artık state e veriyoruz
        val kmhClamped = min(currentVelocity, 220f)
        if (posDiff < params.minPositionDiff) {
            closedPosCounter++
            if (abs(kmhClamped - (lastVelocity ?: kmhClamped)) <= params.minSpeedDiff && closedPosCounter >= 8) {
                _state.value = PositionSpeedState(totalDistance + params.initialBerthingOffset, 0f)
                lastVelocity = 0f
            } else {
                _state.value = PositionSpeedState(totalDistance + params.initialBerthingOffset, kmhClamped)
                lastVelocity = kmhClamped
            }
        } else {
            closedPosCounter = 0
            _state.value = PositionSpeedState(totalDistance + params.initialBerthingOffset, kmhClamped)
            lastVelocity = kmhClamped
        }

        // 7) referanslar & sayaçlar
        lastGpsLoc = currentLocation
        lastGpsTimeInMs = System.currentTimeMillis()
        dataCounter()
    }

    // ACC Verisini İşleme
    fun onAccSample(ax: Double, ay: Double, dtSec: Double) {
        if (!isKalmanReady) return

        // 1) predict
        kalman.predict(ax, ay, dtSec)

        // 2) GPS kesinti kontrolü
        val now = System.currentTimeMillis()
        val gpsGapSecond = (now - lastGpsTimeInMs) / 1000.0
        if (gpsGapSecond <= params.gpsNoDataTimeSec) return

        // 3) GPS kesintisi old durumda Kalman Filtresinde elde edilen position ve speed i kullanıyoruz
        var currentSpeed = (kalman.getSpeed() * 3.6).toFloat()
        val currentPosition = kalman.getPosition()

        val lastState = _state.value
        val lastSpeed = lastState.speed
        if (abs(currentSpeed - lastSpeed) > params.maxSpeedDiff) return
        if (abs(currentSpeed - lastSpeed) < params.minSpeedDiff) currentSpeed = lastSpeed

        // ACC sensörünün frekansı gps inkinden yaklaşık 5 kat daha fazla old için posDiff ile (params.minPositionDiff / 5) i kıyasladık
        if (abs(currentPosition - (lastState.position - params.initialBerthingOffset)) < params.minPositionDiff / 5f) {
            closedPosCounter++
            if (abs(currentSpeed - lastSpeed) <= params.minSpeedDiff && closedPosCounter >= 25) currentSpeed = 0f
        } else {
            closedPosCounter = 0
        }

        totalDistance = currentPosition
        _state.value = PositionSpeedState(
            position = currentPosition + params.initialBerthingOffset,
            speed = min(currentSpeed, 220f)
        )

        // ACC moduna girdiğimiz için GPS recalibration penceresinin flaglerini ayarladık
        isGpsReady = false
        gpsRecalibrationCount = 0
        lastVelocity = null
        // Moving average penceresini sıfırla
        location_first = null; location_second = null; location_third = null
    }

    private fun dataCounter() {
        totalData++
        _dataNumber.value = totalData
    }
}
