package fitech.tutorials.rsmgraphlast.data.models

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import fitech.tutorials.rsmgraphlast.data.api.ApiFactory
import fitech.tutorials.rsmgraphlast.data.api.LoginApiService
import fitech.tutorials.rsmgraphlast.data.api.DasApiService
import fitech.tutorials.rsmgraphlast.data.coasting.CoastingEngine
import fitech.tutorials.rsmgraphlast.data.local.readAllConfigParams
import fitech.tutorials.rsmgraphlast.data.local.saveAdminConfig
import fitech.tutorials.rsmgraphlast.data.local.saveAutoStationTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException


class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    private var token: String? = null
    private val DB_BASE = "http://160.75.159.6:3012/api/database/"
    private val DAS_BASE = "http://160.75.159.6:3012/api/das/"

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn



    private val loginApiService: LoginApiService = ApiFactory.createLoginService(DB_BASE)

    private var dasApiService: DasApiService? = null

    private var coastingEngine: CoastingEngine? = null

    private val _trains = MutableStateFlow<List<Train>>(emptyList())
    val trains: StateFlow<List<Train>> = _trains

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    private val _allConfigParams = MutableStateFlow<AllConfigParams>(AllConfigParams())
    val allConfigParams : StateFlow<AllConfigParams> = _allConfigParams

    private val _adminConfigParams = MutableStateFlow<AdminConfigParams>(AdminConfigParams())
    val adminConfigParams : StateFlow<AdminConfigParams> = _adminConfigParams

    private val _autoStateTransition = MutableStateFlow<Boolean>(true)
    val autoStateTransition : StateFlow<Boolean> = _autoStateTransition

    private val _selectedTrain = MutableStateFlow<Train?>(null)
    val selectedTrain: StateFlow<Train?> = _selectedTrain

    private val _selectedTrack = MutableStateFlow<Track?>(null)
    val selectedTrack: StateFlow<Track?> = _selectedTrack

    private val _selectedDirection = MutableStateFlow<String?>(null)
    val selectedDirection : StateFlow<String?> = _selectedDirection

    private val _selectedInitialStation = MutableStateFlow<Station?>(null)
    val selectedInitialStation : StateFlow<Station?> = _selectedInitialStation

    private val _selectedFinalStation = MutableStateFlow<Station?>(null)
    val selectedFinalStation : StateFlow<Station?> = _selectedFinalStation

    private val _speedLimitPoints = MutableStateFlow<List<Entry>>(emptyList())
    val speedLimitPoints : StateFlow<List<Entry>> = _speedLimitPoints

    private val _selectedSkippedStations = MutableStateFlow<Set<Station>?>(null)
    val selectedSkippedStations : StateFlow<Set<Station>?> = _selectedSkippedStations

    private val _allOutJourneyTime = MutableStateFlow(0.0)
    val allOutJourneyTime: StateFlow<Double> = _allOutJourneyTime

    private val _firstAllOutProfile = MutableStateFlow<DASOutput?>(null)
    val firstAllOutProfile: StateFlow<DASOutput?> = _firstAllOutProfile

    // Coasting çıktıları UI’a yine HomeVM üzerinden verelim:
    val dasProfilePoints: StateFlow<List<Entry>>
        get() = coastingEngine?.dasProfilePoints ?: MutableStateFlow(emptyList())

    val coastingBand: StateFlow<Pair<Float, Float>?>
        get() = coastingEngine?.coastingBand ?: MutableStateFlow(null)

    val lastDasOutput: StateFlow<DASOutput?>
        get() = coastingEngine?.lastDasOutput ?: MutableStateFlow(null)

    private val _segmentInitialStation = MutableStateFlow<Station?>(null)
    val segmentInitialStation: StateFlow<Station?> = _segmentInitialStation

    private val _segmentFinalStation = MutableStateFlow<Station?>(null)
    val segmentFinalStation: StateFlow<Station?> = _segmentFinalStation

    private val _driverId = MutableStateFlow<Int?>(null)
    val driverId: StateFlow<Int?> = _driverId


    fun markJourneyStarted() {
        coastingEngine?.markJourneyStarted()
    }

    init {
        viewModelScope.launch {
            app.readAllConfigParams().collect { local ->
                _allConfigParams.value = local
                _autoStateTransition.value = local.autoStationTransition
            }
        }
    }

    fun sha256(text: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun loginAndLoad(username: String, plainPassword: String) = viewModelScope.launch {
        _isLoggingIn.value = true
        _loginError.value = null

        try {
            val hashed = sha256(plainPassword)

            val resp = loginApiService.login(
                mapOf("Username" to username.trim(), "Password" to hashed)
            )

            if (!resp.isSuccessful) {
                val code = resp.code()

                _loginError.value = when (code) {
                    400, 401, 403 -> "Kullanıcı adı veya şifre yanlış."
                    404 -> "Giriş servisine ulaşılamadı (404)."
                    in 500..599 -> "Sunucu hatası. Lütfen daha sonra tekrar deneyin."
                    else -> "Beklenmeyen hata (HTTP $code). Lütfen tekrar deneyin."
                }

                _isLoggedIn.value = false
                return@launch
            }

            token = resp.body()?.trim('"', ' ', '\n', '\r')
            val t = token

            if (t.isNullOrBlank()) {
                _loginError.value = "Sunucudan geçersiz yanıt alındı (token boş)."
                _isLoggedIn.value = false
                return@launch
            }

            // Token -> driverId
            _driverId.value = extractDriverIdFromJwt(t)

            // DAS servisleri
            dasApiService = ApiFactory.createDasServiceShort(DAS_BASE, t)
            val das = dasApiService!!
            coastingEngine = CoastingEngine(das, viewModelScope)

            // İlk yüklemeler
            _trains.value = das.getTrainList()
            _tracks.value = das.getTrackList()
            _adminConfigParams.value = das.getSystemConfig()

            val a = _adminConfigParams.value
            val merged = AllConfigParams(
                minSpeedDiff = a.minimumSpeedDifference,
                maxSpeedDiff = a.maximumSpeedDifference,
                minPositionDiff = a.minimumPositionDifference,
                accSamplingTime = a.accelerationSamplingTime,
                gpsNoDataTime = a.gpsNoDataTime,
                calibrationDataNumber = a.calibrationDataNumber,
                autoStationTransition = _autoStateTransition.value
            )
            _allConfigParams.value = merged

            viewModelScope.launch { app.saveAdminConfig(a) }

            _isLoggedIn.value = true

        } catch (e: UnknownHostException) {
            // DNS / internet yok / host çözümlenemedi
            _loginError.value = "İnternet bağlantınızı kontrol edin."
            _isLoggedIn.value = false

        } catch (e: SocketTimeoutException) {
            _loginError.value = "Bağlantı zaman aşımına uğradı. Lütfen tekrar deneyin."
            _isLoggedIn.value = false

        } catch (e: IOException) {
            // Connection reset, network kopması vb.
            _loginError.value = "Ağ hatası oluştu. İnternet bağlantınızı kontrol edin."
            _isLoggedIn.value = false

        } catch (e: Exception) {
            _loginError.value = "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin."
            _isLoggedIn.value = false

        } finally {
            _isLoggingIn.value = false
        }
    }

    fun logout() {
        token = null
        dasApiService = null
        coastingEngine = null

        _driverId.value = null

        _selectedTrain.value = null
        _selectedTrack.value = null
        _selectedDirection.value = null
        _selectedInitialStation.value = null
        _selectedFinalStation.value = null
        _segmentInitialStation.value = null
        _segmentFinalStation.value = null

        _trains.value = emptyList()
        _tracks.value = emptyList()
        _speedLimitPoints.value = emptyList()

        _isLoggedIn.value = false
        _loginError.value = null
    }


    // Worker'a token geçebilmek için
    fun currentToken(): String? = token

    private fun extractDriverIdFromJwt(token: String): Int? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = parts[1]

            val decodedBytes = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val json = JSONObject(String(decodedBytes, Charsets.UTF_8))
            // senin token claim'in: new Claim("id", user.Id)
            json.optString("id", null)?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // Seçimler güncellenince coasting’e de ilet
    fun selectTrain(train: Train) {
        _selectedTrain.value = train
        coastingEngine?.updateSelections(
            train, _selectedTrack.value, _selectedInitialStation.value, _selectedFinalStation.value, _selectedDirection.value
        )
    }
    fun selectTrack(track: Track) {
        _selectedTrack.value = track
        coastingEngine?.updateSelections(
            _selectedTrain.value, track, _selectedInitialStation.value, _selectedFinalStation.value, _selectedDirection.value
        )
    }
    fun selectDirection(direction : String){
        _selectedDirection.value = direction
        coastingEngine?.updateSelections(
            _selectedTrain.value, _selectedTrack.value, _selectedInitialStation.value, _selectedFinalStation.value, direction
        )
    }
    fun selectInitialStation(initialStation: Station){
        _selectedInitialStation.value = initialStation

        // Segment initial ilk başta burası olsun:
        _segmentInitialStation.value = initialStation

        coastingEngine?.updateSelections(
            _selectedTrain.value, _selectedTrack.value, initialStation, _selectedFinalStation.value, _selectedDirection.value
        )
    }
    fun selectFinalStation(finalStation: Station){
        _selectedFinalStation.value = finalStation

        // Segment final ilk başta burası olsun:
        _segmentFinalStation.value = finalStation

        coastingEngine?.updateSelections(
            _selectedTrain.value, _selectedTrack.value, _selectedInitialStation.value, finalStation, _selectedDirection.value
        )
    }
    fun selectAutoStateTransition(value: Boolean){
        _autoStateTransition.value = value
        _allConfigParams.value.autoStationTransition = value

        viewModelScope.launch {
            app.saveAutoStationTransition(value)
        }
    }
    fun stationsInCurrentDirection(): List<Station> {
        val track = _selectedTrack.value ?: return emptyList()
        val dir = _selectedDirection.value ?: return emptyList()
        return if (dir == "West to East") track.stations else track.stationsInverted
    }


    fun calculateSpeedLimits() {
        val track = _selectedTrack.value ?: return
        val direction = _selectedDirection.value ?: return
        val initial = _selectedInitialStation.value ?: return
        val final = _selectedFinalStation.value ?: return

        val isW2E = direction == "West to East"
        val speedLimits = if (isW2E) track.speedLimits else track.speedLimitsInverted

        // Yolculuk aralığı (direction’a göre!)
        val startPos = initial.berthingPosition
        val endPos   = final.berthingPosition

        val low = minOf(startPos, endPos)
        val high = maxOf(startPos, endPos)

        val xs = speedLimits.x.map { it.toFloat() }
        val ys = speedLimits.y

        if (xs.isEmpty() || ys.isEmpty()) {
            _speedLimitPoints.value = emptyList()
            return
        }

        var beforeStart: Entry? = null    // low tarafının "dışında" kalan son nokta
        var afterEnd: Entry? = null       // high tarafının "dışında" kalan ilk nokta

        if (isW2E) {
            for (i in xs.indices) {
                val x = xs[i]
                if (x < low) beforeStart = Entry(x, ys[i]) else break
            }
        } else {
            for (i in xs.indices) {
                val x = xs[i]
                if (x < low) beforeStart = Entry(x, ys[i])
            }
        }

        if (isW2E) {
            for (i in xs.indices) {
                val x = xs[i]
                if (x > high) { afterEnd = Entry(x, ys[i]); break }
            }
        } else {
            for (i in xs.indices) {
                val x = xs[i]
                if (x > high) afterEnd = Entry(x, ys[i])
            }
        }

        val mid = mutableListOf<Entry>()
        for (i in xs.indices) {
            val x = xs[i]
            if (x in low..high) {
                // Step için: x'e gelmeden önce bir "y önceki" noktası ekle (x - eps, y_prev)
                if (i > 0) {
                    val stepX = if (isW2E) (x - 0.0000001f) else (x + 0.0000001f)
                    mid.add(Entry(stepX, ys[i - 1]))
                }
                mid.add(Entry(x, ys[i]))
            }
        }

        // Tüm listeyi direction’a göre sırala ve boundary’leri direction’a uygun bağla
        val result = mutableListOf<Entry>()

        if (isW2E) {
            // Artan grafik: low->high
            beforeStart?.let { result.add(it) }
            result.addAll(mid)
            afterEnd?.let { lp ->
                if (result.isNotEmpty()) result.add(Entry(lp.x - 1e-7f, result.last().y))
                result.add(lp)
            }
            // Ek güvenlik: gerçekten artan sıraya zorla
            result.sortBy { it.x }
        } else {
            // Azalan grafik: high->low
            // W2E'de "beforeStart" low'dan önceydi; E2W'de yolculuk high->low olduğu için
            // "afterEnd" (high'dan sonraki ilk nokta) başlangıç tarafında, "beforeStart" bitiş tarafında kalır.

            afterEnd?.let { result.add(it) }

            val midDesc = mid.sortedByDescending { it.x }
            result.addAll(midDesc)

            beforeStart?.let { bp ->
                if (result.isNotEmpty()) result.add(Entry(bp.x + 1e-7f, result.last().y))
                result.add(bp)
            }

            // Ek güvenlik: gerçekten azalan sıraya zorla
            result.sortByDescending { it.x }
        }

        _speedLimitPoints.value = result

        println("Initial Berthing Position:${initial.berthingPosition} Final Berth Pos:${final.berthingPosition}")
        result.forEach{point->
            println("SpeedPoint:${point.x}")
        }
        result.forEach{point->
            println("Speed Limit:${point.y}")
        }
    }

    fun speedLimitAt(position: Float): Float {
        val limits = _speedLimitPoints.value // List<Entry> (x: position, y: speed)
        if (limits.isEmpty()) return Float.POSITIVE_INFINITY

        // X'e göre sıralı değilse önce sırala
        val sorted = limits.sortedBy { it.x }

        var last = sorted.first().y
        for (e in sorted) {
            if (e.x <= position) last = e.y else break
        }
        return last
    }


    // All-Out profili tek seferlik çek (coasting üzerinden)
    fun fetchAllOutProfileOnce() = viewModelScope.launch {
        val isAllOutGetted = coastingEngine?.fetchAllOutProfileOnce() ?: false
        _allOutJourneyTime.value = if (isAllOutGetted) coastingEngine?.getAllOutJourneyTime() ?: 0.0 else 0.0
    }

    // Döngüyü başlat/bitir
    fun startCoastingSimLoop(positionProvider: () -> Float, speedProvider: () -> Float) {
        coastingEngine?.bindPosition(positionProvider)
        coastingEngine?.bindSpeed(speedProvider)
        coastingEngine?.bindSpeedLimit { pos ->  speedLimitAt(pos)}
        coastingEngine?.startLoop()
    }

    fun stopCoastingSimLoop(){
        coastingEngine?.stopLoop()
    }

    fun selectSkippedStations(skippedStations: Set<Station>){
        _selectedSkippedStations.value = skippedStations
    }

    private fun orderedStations(): List<Station> {
        val track = _selectedTrack.value ?: return emptyList()
        val dir = _selectedDirection.value ?: return emptyList()
        return if (dir == "West to East") track.stations else track.stationsInverted
    }

    fun goNextSegment() {
        val list = stationsInCurrentDirection()
        val fin = _segmentFinalStation.value ?: return
        val idx = list.indexOf(fin)
        if (idx == -1 || idx == list.lastIndex) return

        val newInit = fin
        val newFinal = list[idx + 1]

        setSegmentInitial(newInit)
        setSegmentFinal(newFinal)
    }

    fun goPrevSegment() {
        val list = stationsInCurrentDirection()
        val init = _segmentInitialStation.value ?: return
        val idx = list.indexOf(init)
        if (idx <= 0) return

        val newFinal = init
        val newInit = list[idx - 1]

        setSegmentInitial(newInit)
        setSegmentFinal(newFinal)
    }


    // Main screen dropdown’ları bununla set etsin:
    fun setSegmentInitial(st: Station) {
        _segmentInitialStation.value = st

        // Segment değişince coasting & speedlimit hesapları bu segmente göre olmalı:
        _selectedInitialStation.value = st
        coastingEngine?.updateSelections(
            _selectedTrain.value, _selectedTrack.value, st, _segmentFinalStation.value, _selectedDirection.value
        )
    }

    fun setSegmentFinal(st: Station) {
        _segmentFinalStation.value = st

        _selectedFinalStation.value = st
        coastingEngine?.updateSelections(
            _selectedTrain.value, _selectedTrack.value, _segmentInitialStation.value, st, _selectedDirection.value
        )
    }


    // start/continue anında movementDuration resetlemek için:
    fun resetSegmentTimer() {
        coastingEngine?.resetSegmentTimer()
    }

} 