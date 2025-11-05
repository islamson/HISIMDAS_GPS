package fitech.tutorials.rsmgraphlast.data.api

import AccLogsRequest
import GpsLogsRequest
import fitech.tutorials.rsmgraphlast.data.models.AdminConfigParams
import fitech.tutorials.rsmgraphlast.data.models.Track
import fitech.tutorials.rsmgraphlast.data.models.Train
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("login")
    suspend fun login(@Body body: Map<String, String>) : retrofit2.Response<String>

    @POST("get-train-list")
    suspend fun getTrainList(): List<Train>

    @POST("get-track-list")
    suspend fun getTrackList(): List<Track>

    @POST("get-mobile-settings")
    suspend fun getSystemConfig(): AdminConfigParams

    @POST("upload-gps-logs")
    suspend fun uploadGPSLogs(@Body body: GpsLogsRequest): Response<Boolean>

    @POST("upload-acceleration-logs")
    suspend fun uploadAccLogs(@Body body: AccLogsRequest): Response<Boolean>
} 