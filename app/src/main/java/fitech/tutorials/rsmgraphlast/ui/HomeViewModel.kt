package fitech.tutorials.rsmgraphlast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import fitech.tutorials.rsmgraphlast.data.api.ApiService
import fitech.tutorials.rsmgraphlast.data.models.Station
import fitech.tutorials.rsmgraphlast.data.models.Train
import fitech.tutorials.rsmgraphlast.data.models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeViewModel : ViewModel() {
    private val apiService: ApiService = Retrofit.Builder()
        .baseUrl("http://160.75.159.6:3012/api/database/")
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
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                _trains.value = apiService.getTrainList()
                _tracks.value = apiService.getTrackList()
                print("Trains: ")
                trains.value.forEach {train->
                    print("\nTrain Name: ${train.name}")
                    print("\nTrain Id: ${train.id}")
                    print("\nTrain Description: ${train.description}")
                    print("\nTrain Created Time: ${train.createdAt}")
                    print("\n")
                }

                print("\n")
                print("Tracks: ")
                tracks.value.forEach {track->
                    print("\nTrack Name: ${track.name}")
                    print("\nTrack Id: ${track.id}")
                    print("\nTrack Description: ${track.description}")
                    print("\nTrack Created Time: ${track.createdAt}")
                    print("Stations: ${track.stationsInverted.forEach({station-> print("\n" + station.name)})}")
                    print("\n")
                }
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
                println(e)
            }
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
            if (pos in initial.berthingPosition.toFloat()..final.berthingPosition.toFloat()) {
                if(result.size >= 1)
                    result.add(Entry((pos - 0.0000001).toFloat(), speedLimits.y[i - 1].toFloat()))
                result.add(Entry(pos.toFloat(), speedLimits.y[i].toFloat()))
            }
        }
        _speedLimitPoints.value = result
    }
} 