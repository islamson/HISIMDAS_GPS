package fitech.tutorials.rsmgraphlast.data.location

import AccLogsRequest
import GpsLogsRequest
import android.content.Context
import android.hardware.SensorManager
import com.google.android.gms.location.FusedLocationProviderClient
import fitech.tutorials.rsmgraphlast.data.LocationProcessor
import fitech.tutorials.rsmgraphlast.data.location.fusion.FusionEngine
import fitech.tutorials.rsmgraphlast.data.tracking.PositionSpeedState
import fitech.tutorials.rsmgraphlast.data.tracking.TrackingController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.google.gson.Gson
import fitech.tutorials.rsmgraphlast.data.logging.LogUploader
import fitech.tutorials.rsmgraphlast.data.models.DasLogsAcc
import fitech.tutorials.rsmgraphlast.data.models.DasLogsGps
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Eski LocationViewModel.stopTracking()’deki logging + worker kuyruğunu birebir üstlenen Controller */
class DefaultTrackingController(
    private val appContext: Context,
    private val gpsEngine: GpsEngine,
    private val accEngine: AccEngine,
    private val fusion: FusionEngine,
    private val tokenProvider: () -> String,            // homeViewModel.currentToken()
    private val logUploader: LogUploader
) : TrackingController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ===== LOG BUFFERS (Eski LocationViewModel ile aynı alanlar) =====
    // GPS
    private val logGPSTime  = mutableListOf<Double>()
    private val logGPSLat   = mutableListOf<Double>()
    private val logGPSLon   = mutableListOf<Double>()
    private val logGPSAlt   = mutableListOf<Double>()
    private val logGPSPos   = mutableListOf<Double>()
    private val logGPSSpeed = mutableListOf<Double>()
    private var logGPSDataNumber = 0

    // ACC
    private val logAccTime = mutableListOf<Double>()
    private val logAccX    = mutableListOf<Double>()
    private val logAccY    = mutableListOf<Double>()
    private val logAccZ    = mutableListOf<Double>()
    private var logAccDataNumber = 0

    // ===== DIŞA AÇILAN STATE =====
    override val state: StateFlow<PositionSpeedState> = fusion.state
    val dataNumber: StateFlow<Int> get() = fusion.dataNumber

    // ===== KAYIT =====
    private var gpsJob: Job? = null
    private var accJob: Job? = null
    private var fusionJob: Job? = null

    override suspend fun loadTrackFromAssets(context: Context, trackId: Int) {
        // track’ı FusionEngine tarafında zaten projection için kullanıyorsun; gerekirse burada da yüklenebilir.
        // Şu an fusion.run() içinde projection path'i FusionParams'tan geliyor.
        // Burası boş bırakılabilir ya da senin util'inle kullanılır.
        withContext(Dispatchers.IO) {
            LocationProcessor.loadTrackLocations(context, trackId)
        }
    }

    override fun start(
        fused: FusedLocationProviderClient,
        sensorManager: SensorManager,
        context: Context,
        initialOffset: Float
    ) {
        // 1) Akışları al
        val req     = gpsEngine.defaultRequest()
        val gpsFlow = gpsEngine.updates(fused, req)              // Flow<Location>
        val accFlow = accEngine.worldAcceleration(sensorManager) // Flow<Triple<Float,Float,Float>>

        // 2) FusionEngine’i çalıştır (GPS akışını FusionEngine içinde collect ediyoruz)
        if (fusionJob?.isActive != true) {
            fusionJob = scope.launch { fusion.run() }
        }

        // 3) LOG: GPS her örnekte state ile beraber listeye yaz
        gpsJob?.cancel()
        gpsJob = gpsFlow
            .onEach { loc ->
                // zaman s (epoch)
                logGPSTime.add(System.currentTimeMillis().toDouble() / 1000.0)
                logGPSLat.add(loc.latitude)
                logGPSLon.add(loc.longitude)
                logGPSAlt.add(loc.altitude)
                logGPSPos.add(state.value.position.toDouble())
                logGPSSpeed.add(state.value.speed.toDouble())
                logGPSDataNumber++
            }
            .launchIn(scope)

        // 4) LOG: ACC her örnekte listeye yaz + FusionEngine'e aktar
        accJob?.cancel()
        accJob = accFlow
            .onEach { (ax, ay, az) ->
                // FusionEngine ivme örneği
                // dt hesabı AccEngine içinde yapılıyorsa oradan da gelebilir; burada yalnızca logluyoruz.
                // Log — dünya ekseninde ivmeler
                logAccX.add(ax.toDouble())
                logAccY.add(ay.toDouble())
                logAccZ.add(az.toDouble())
                logAccTime.add(System.currentTimeMillis().toDouble() / 1000.0)
                logAccDataNumber++
            }
            .launchIn(scope)
    }

    override fun stop() {
        // Akışları kapat
        gpsJob?.cancel(); accJob?.cancel(); fusionJob?.cancel()
        scope.cancel()

        // === Eski LocationViewModel.stopTracking() ile aynı JSON + 2 ayrı Worker akışı ===

        // 1) createdAt (UTC)
        val createdAtUtc = OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        // 2) Body'leri oluştur (Eskiyle birebir aynı alan adları)
        val gpsBody = DasLogsGps(
            createdAt  = createdAtUtc,
            dataNumber = logGPSDataNumber,
            time       = logGPSTime.toList(),
            latitude   = logGPSLat.toList(),
            longitude  = logGPSLon.toList(),
            altitude   = logGPSAlt.toList(),
            position   = logGPSPos.toList(),
            speed      = logGPSSpeed.toList()
        )
        val accBody = DasLogsAcc(
            createdAt  = createdAtUtc,
            dataNumber = logAccDataNumber,
            time       = logAccTime.toList(),
            axisX      = logAccX.toList(),
            axisY      = logAccY.toList(),
            axisZ      = logAccZ.toList()
        )

        // 3) JSON’a yaz (internal storage)
        val logsDir = File(appContext.filesDir, "logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        val gson = Gson()
        val gpsFile = File(logsDir, "gps_${System.currentTimeMillis()}.json")
        val accFile = File(logsDir, "acc_${System.currentTimeMillis()}.json")
        gpsFile.writeText(gson.toJson(GpsLogsRequest(gpsBody)))
        accFile.writeText(gson.toJson(AccLogsRequest(accBody)))

        // 4) LogUploader ile kuyruğa at (ayrı endpoint’ler)
        val token = tokenProvider()
        logUploader.enqueue(kind = "gps", path = gpsFile.absolutePath, token = token)
        logUploader.enqueue(kind = "acc", path = accFile.absolutePath, token = token)

        // 5) Buffer temizliği
        logGPSTime.clear(); logGPSLat.clear(); logGPSLon.clear()
        logGPSAlt.clear();  logGPSPos.clear(); logGPSSpeed.clear()
        logAccTime.clear(); logAccX.clear();   logAccY.clear(); logAccZ.clear()
        logGPSDataNumber = 0
        logAccDataNumber = 0
    }
}
