package fitech.tutorials.rsmgraphlast.data.coasting

import android.util.Log
import com.github.mikephil.charting.data.Entry
import fitech.tutorials.rsmgraphlast.data.api.DasApiService
import fitech.tutorials.rsmgraphlast.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.min

class CoastingEngine(
    private val dasApi: DasApiService,
    private val scope: CoroutineScope
) {
    // Seçimler (HomeVM’den beslenecek)
    var selectedTrain: Train? = null
    var selectedTrack: Track? = null
    var selectedInitial: Station? = null
    var selectedFinal: Station? = null
    var direction: String? = null  // "West to East" / "East to West"

    // Durum
    private var journeyStartMs: Long? = null
    private var positionProvider: (() -> Float)? = null
    private var speedLimitProvider: ((Float) -> Float)? = null
    private var speedProvider: (() -> Float)? = null

    private var firstAllOutProfile: DASOutput? = null
    private var allOutJourneyTime: Double = 0.0

    private var simJob: Job? = null
    private var expectedTime = 0.0
    private var timeDifference = 0.0
    private var bestPosition = 0f

    // Dışarı açık akışlar (UI / HomeVM kullanır)
    private val _dasProfilePoints = MutableStateFlow<List<Entry>>(emptyList())
    val dasProfilePoints: StateFlow<List<Entry>> = _dasProfilePoints

    private val _coastingBand = MutableStateFlow<Pair<Float, Float>?>(null)
    val coastingBand: StateFlow<Pair<Float, Float>?> = _coastingBand

    private val _lastDasOutput = MutableStateFlow<DASOutput?>(null)
    val lastDasOutput: StateFlow<DASOutput?> = _lastDasOutput

    // Bağlayıcılar
    fun bindPosition(provider: () -> Float) { positionProvider = provider }
    fun bindSpeed(provider: () -> Float) {speedProvider = provider}
    fun markJourneyStarted() { journeyStartMs = System.currentTimeMillis() }
    fun resetSegmentTimer() { journeyStartMs = System.currentTimeMillis() }

    fun bindSpeedLimit(provider: (position: Float) -> Float) {
        speedLimitProvider = provider
    }

    fun updateSelections(
        train: Train?, track: Track?, initial: Station?, final: Station?, dir: String?
    ) {
        selectedTrain = train
        selectedTrack = track
        selectedInitial = initial
        selectedFinal = final
        direction = dir
    }

    fun getAllOutJourneyTime(): Double = allOutJourneyTime

    // Tek seferlik All-Out profili çek
    suspend fun fetchAllOutProfileOnce(): Boolean = withContext(Dispatchers.IO) {
        val tr = selectedTrain ?: return@withContext false
        val tk = selectedTrack ?: return@withContext false
        val si = selectedInitial ?: return@withContext false
        val sf = selectedFinal ?: return@withContext false
        val dir = direction ?: return@withContext false

        val sign = if (dir == "West to East") 1 else -1
        val offset = sign * tr.totalLength / 2.0

        val initialPositionInput = si.berthingPosition + offset
        val finalPositionInput   = sf.berthingPosition + offset

        val body = DASInput(
            generalId = 1,
            trainId = tr.id,
            trackId = tk.id,
            initialPosition = initialPositionInput,
            finalPosition = finalPositionInput,
            tracklineDirection = if (dir == "West to East") 0 else 1,
            coastingAllowedTime = 0.0
        )

        val resp = dasApi.simulate(body)
        if (!resp.isSuccessful) return@withContext false

        val out = resp.body() ?: return@withContext false

        firstAllOutProfile = out
        allOutJourneyTime = out.journeyTime
        _lastDasOutput.value = out

        val pos = out.trainPositionTime.y
        val spd = out.trainSpeedTime.y
        val n = min(pos.size, spd.size)
        _dasProfilePoints.value = List(n) { i -> Entry(pos[i], spd[i]) }
        for(i in 0..< pos.size)
            println("Allout points x:${pos[i]} y:${spd[i]}")

        _coastingBand.value = if (out.coastingRegion.isActive) {
            out.coastingRegion.startPosition.toFloat() to out.coastingRegion.endPosition.toFloat()
        } else null

        true
    }

    // Döngü
    fun startLoop() {
        if (simJob?.isActive == true) return
        simJob = scope.launch {
            while (isActive) {
                try {
                    if (selectedTrain == null || selectedTrack == null || selectedInitial == null || selectedFinal == null
                        || direction == null || firstAllOutProfile == null || positionProvider == null || speedProvider == null || speedLimitProvider == null) {
                        delay(5_000); continue
                    }

                    val start = journeyStartMs ?: System.currentTimeMillis()
                    val movementDuration = (System.currentTimeMillis() - start) / 1000.0

                    val currentPos = positionProvider!!.invoke()
                    val finalStation = selectedFinal!!

                    val remaining = finalStation.naturalJourneyTime - movementDuration
                    val coastingTimeInput = remaining.coerceAtLeast(0.0)


                    val sign = if (direction == "West to East") 1 else -1
                    val finalPositionInput = finalStation.berthingPosition + (sign * selectedTrain!!.totalLength / 2.0)

                    val limitAtPos = speedLimitProvider!!.invoke(currentPos)
                    val initialSpeedInput = min(speedProvider!!.invoke().toDouble(), limitAtPos.toDouble())

                    Log.d("Coasting Movement Duration", "Movement Duration: $movementDuration")

                    val body = DASInput(
                        generalId = 1,
                        trainId = selectedTrain!!.id,
                        trackId = selectedTrack!!.id,
                        initialPosition = currentPos.toDouble(),
                        finalPosition = finalPositionInput,
                        initialSpeed = initialSpeedInput,
                        tracklineDirection = if (direction == "West to East") 0 else 1,
                        coastingAllowedTime = coastingTimeInput
                    )

                    val resp = withContext(Dispatchers.IO) { dasApi.simulate(body) }
                    if (resp.isSuccessful) {
                        resp.body()?.let { out ->
                            _lastDasOutput.value = out
                            val positions = out.trainPositionTime.y
                            val speeds = out.trainSpeedTime.y
                            val n = min(positions.size, speeds.size)
                            _dasProfilePoints.value = List(n) { i -> Entry(positions[i], speeds[i]) }

                            _coastingBand.value = if (out.coastingRegion.isActive)
                                out.coastingRegion.startPosition.toFloat() to out.coastingRegion.endPosition.toFloat()
                            else null

                            _dasProfilePoints.value.forEach {
                                val x = if(direction == "West to East") it.x else (selectedTrack!!.tracklineEnd - (it.x - selectedTrack!!.tracklineStart))
                                println("Das points x:${x} speed:${it.y}")
                            }

                            expectedTimeAtPosition(out, currentPos)
                            Log.d("Coasting", "original coasting start:${out.coastingRegion.startPosition}, end:${out.coastingRegion.endPosition}")
                        }
                    } else {
                        Log.w("Coasting", "simulate failed: ${resp.code()} ${resp.message()}")
                    }
                    val coastingBandStartPos = if(direction == "West to East") _coastingBand.value?.first else (coastingBand.value?.first?.minus(selectedTrack!!.tracklineStart)
                        ?.let { selectedTrack!!.tracklineEnd.minus(it) })

                    val coastingBandEndPos = if(direction == "West to East") _coastingBand.value?.second else (coastingBand.value?.second?.minus(selectedTrack!!.tracklineStart)
                        ?.let { selectedTrack!!.tracklineEnd.minus(it) })

                    Log.d("Coasting", "CoastingTime:${coastingTimeInput}, bestPos:${bestPosition}, currentPos${currentPos}, expectedTime:${expectedTime}\n, timeDifference:${timeDifference}, startingPos:${coastingBandStartPos}, endPos:${coastingBandEndPos}, trainLength:${selectedTrain!!.totalLength}")
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
                delay(5_000)
            }
        }
    }

    fun stopLoop() {
        simJob?.cancel()
        simJob = null
    }

    // Hesap parçaları
    private fun stationsInDirection(track: Track, direction: String): List<Station> =
        if (direction == "West to East") track.stations else track.stationsInverted

    private fun findNextStation(track: Track, direction: String, currentPosition: Float): Station? {
        val list = stationsInDirection(track, direction)
        return if(direction == "West to East")
            list.firstOrNull { it.berthingPosition > currentPosition }
        else
            list.firstOrNull { it.berthingPosition < currentPosition }
    }

    private fun expectedTimeAtPosition(out: DASOutput, currentPosition: Float): Double? {
        val positions = out.trainPositionTime.y
        val times = out.trainPositionTime.x
        if (positions.isEmpty() || times.isEmpty()) return null

        var bestIdx = 0
        var bestDiff = abs(positions[0] - currentPosition.toDouble())
        for (i in 1 until min(positions.size, times.size)) {
            val position = if(direction == "East to West") (selectedTrack!!.tracklineEnd - (positions[i] - selectedTrack!!.tracklineStart)) else positions[i]
            val d = abs(position - currentPosition.toDouble())
            if (d < bestDiff) { bestDiff = d; bestIdx = i }
        }
        expectedTime = times[bestIdx]
        bestPosition = if(direction == "East to West") (selectedTrack!!.tracklineEnd - (positions[bestIdx] - selectedTrack!!.tracklineStart)) else positions[bestIdx]

        return expectedTime  //
    }

}
