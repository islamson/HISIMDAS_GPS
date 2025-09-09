package fitech.tutorials.rsmgraphlast.data.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import fitech.tutorials.rsmgraphlast.data.api.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory


class HomeViewModel : ViewModel() {

    private var token: String? = null

    private val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val req = chain.request()
            val needsAuth = !req.url.encodedPath.endsWith("/login")
            val t = token
            val newReq = if (needsAuth && !t.isNullOrBlank())
                req.newBuilder().addHeader("Authorization", "Bearer $t").build()
            else req
            chain.proceed(newReq)
        })
        .build()

    private val apiService: ApiService = Retrofit.Builder()
        .baseUrl("http://160.75.159.6:3012/api/database/")
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    private val _trains = MutableStateFlow<List<Train>>(emptyList())
    val trains: StateFlow<List<Train>> = _trains

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

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

    init {
        loginAndLoad("test", "123456")
    }

    fun sha256(text: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun loginAndLoad(username: String, plainPassword: String) = viewModelScope.launch {
        try {
            val hashed = sha256(plainPassword)
            val resp = apiService.login(mapOf("Username" to username, "Password" to hashed))
            if (resp.isSuccessful) {
                token = resp.body()?.trim('"', ' ', '\n', '\r')
                // token set -> interceptor tüm GET'lere Authorization ekleyecek
                _trains.value = apiService.getTrainList()
                _tracks.value  = apiService.getTrackList()
                print("Trains: ")
                trains.value.forEach { train ->
                    print("\nTrain Name: ${train.name}")
                    print("\nTrain Id: ${train.id}")
                    print("\nTrain Description: ${train.description}")
                    print("\nTrain Created Time: ${train.createdAt}")
                    print("\n")
                }
                print("\n")
                print("Tracks: ")
                tracks.value.forEach { track ->
                    print("\nTrack Name: ${track.name}")
                    print("\nTrack Id: ${track.id}")
                    print("\nTrack Description: ${track.description}")
                    print("\nTrack Created Time: ${track.createdAt}")
                    print("Stations: ${track.stationsInverted.forEach({ station -> print("\n" + station.name) })}")
                    print("\n")
                }
            }
            else {
                println("Login failed: ${resp.code()} ${resp.message()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error detected : ${e.localizedMessage}")
        }
    }


    fun selectTrain(train: Train) {
        _selectedTrain.value = train
    }

    fun selectTrack(track: Track) {
        _selectedTrack.value = track
    }

    fun selectDirection(direction : String){
        _selectedDirection.value = direction
    }

    fun selectInitialStation(initialStation : Station){
        _selectedInitialStation.value = initialStation
    }

    fun selectFinalStation(finalStation : Station){
        _selectedFinalStation.value = finalStation
    }

    fun calculateSpeedLimits(){
        val track = _selectedTrack.value ?: return
        val direction = _selectedDirection.value ?: return
        val initial = _selectedInitialStation.value ?: return
        val final = _selectedFinalStation.value ?: return

        val speedLimits = if (direction == "West to East") track.speedLimits else track.speedLimitsInverted

        val result = mutableListOf<Entry>()
        for (i in speedLimits.x.indices) {
            val pos = speedLimits.x[i]
            if (pos in initial.berthingPosition..final.berthingPosition) {
                if(result.size >= 1)
                    result.add(Entry((pos - 0.0000001).toFloat(), speedLimits.y[i - 1]))
                result.add(Entry(pos.toFloat(), speedLimits.y[i]))
            }
        }
        _speedLimitPoints.value = result
        println("Initial Berthing Position:${initial.berthingPosition} Final Berth Pos:${final.berthingPosition}")
        track.speedLimits.x.forEach{point->
            println("SpeedPoint:${point}")
        }
    }
} 