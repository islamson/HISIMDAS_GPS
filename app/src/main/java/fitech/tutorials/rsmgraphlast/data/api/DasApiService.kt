package fitech.tutorials.rsmgraphlast.data.api

import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DASDriverStatistics
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.AdminConfigParams
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DASInput
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DASOutput
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DasLogsAcc
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.DasLogsGps
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.Track
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.Train
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// baseUrl -> http://160.75.159.6:3012/api/das/

interface DasApiService {
    @POST("get-train-list")
    suspend fun getTrainList(): List<Train>

    @POST("get-track-list")
    suspend fun getTrackList(): List<Track>

    @POST("get-mobile-settings")
    suspend fun getSystemConfig(): AdminConfigParams

    @POST("upload-gps-logs")
    suspend fun uploadGPSLogs(@Body body: DasLogsGps): Response<Int>

    @POST("upload-acceleration-logs")
    suspend fun uploadAccLogs(@Body body: DasLogsAcc): Response<Int>

    @POST("simulate")
    suspend fun simulate(@Body body: DASInput): Response<DASOutput>

    @POST("store-driver-statistics")
    suspend fun postDriverStatistics(@Body body: DASDriverStatistics): Response<Boolean>
}