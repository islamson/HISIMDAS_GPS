package fitech.tutorials.rsmgraphlast.data.api

import fitech.tutorials.rsmgraphlast.data.models.Train
import fitech.tutorials.rsmgraphlast.data.models.Track
import retrofit2.http.POST

interface ApiService {
    @POST("get-train-list")
    suspend fun getTrainList(): List<Train>

    @POST("get-track-list")
    suspend fun getTrackList(): List<Track>
} 