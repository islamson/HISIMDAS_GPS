package fitech.tutorials.rsmgraphlast.work

import DASDriverStatistics
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import fitech.tutorials.rsmgraphlast.data.api.ApiFactory
import fitech.tutorials.rsmgraphlast.data.api.DasApiService
import fitech.tutorials.rsmgraphlast.data.models.DasLogsAcc
import fitech.tutorials.rsmgraphlast.data.models.DasLogsGps
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

class UploadTripWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        // === input keys ===
        const val KEY_GPS_PATH = "gps_path"
        const val KEY_ACC_PATH = "acc_path"
        const val KEY_TOKEN = "token"

        const val KEY_DRIVER_ID = "driver_id"
        const val KEY_TRAIN_ID = "train_id"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_GENERAL_ID = "general_id"
        const val KEY_DIRECTION = "direction" // "West to East" | "East to West" (senin string)

        private const val BASE_URL = "http://160.75.159.6:3012/api/das/"
        private const val TAG_UP = "UPLOAD_TRIP"
        private const val MAX_RETRIES = 5
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val gpsPath = inputData.getString(KEY_GPS_PATH) ?: return Result.success()
        val accPath = inputData.getString(KEY_ACC_PATH) ?: return Result.success()
        val token = inputData.getString(KEY_TOKEN) // nullable olabilir, ama sende genelde dolu

        val driverId = inputData.getInt(KEY_DRIVER_ID, -1)
        val trainId = inputData.getInt(KEY_TRAIN_ID, -1)
        val trackId = inputData.getInt(KEY_TRACK_ID, -1)
        val generalId = inputData.getInt(KEY_GENERAL_ID, 1)
        val direction = inputData.getString(KEY_DIRECTION) ?: "West to East"

        // Kuyruğu kilitlememek için: kritik alanlar yoksa success
        if (driverId <= 0 || trainId <= 0 || trackId <= 0) {
            Log.w(TAG_UP, "missing ids driverId=$driverId trainId=$trainId trackId=$trackId -> success skip")
            return Result.success()
        }

        val gpsFile = File(gpsPath)
        val accFile = File(accPath)

        // Dosya yoksa kuyruğu asla kilitleme
        if (!gpsFile.exists() || !accFile.exists()) {
            Log.w(TAG_UP, "file missing gpsExists=${gpsFile.exists()} accExists=${accFile.exists()} -> success skip")
            return Result.success()
        }

        val api: DasApiService = ApiFactory.createDasServiceLong(BASE_URL, token)

        return try {
            // === 1) GPS upload ===
            val gpsLogId = uploadGps(api, gpsFile) ?: return decideRetryOrSuccess("gps upload failed (null id)")

            // === 2) ACC upload ===
            val accLogId = uploadAcc(api, accFile) ?: return decideRetryOrSuccess("acc upload failed (null id)")

            // === 3) Statistics post ===
            val statsBody = buildStatsBody(
                driverId = driverId,
                gpsLogId = gpsLogId,
                accLogId = accLogId,
                generalId = generalId,
                trainId = trainId,
                trackId = trackId,
                direction = direction
            )
            Log.d(TAG_UP, "stats json = ${gson.toJson(statsBody)}")

            val statsResp = api.postDriverStatistics(statsBody)
            if (statsResp.isSuccessful) {
                // Her şey OK → dosyaları sil
                safeDelete(gpsFile)
                safeDelete(accFile)
                Log.d(TAG_UP, "ALL OK gpsLogId=$gpsLogId accLogId=$accLogId")
                Result.success()
            } else {
                val code = statsResp.code()

                //val err = safeErr(statsResp.errorBody())
                //Log.w(TAG_UP, "stats fail code=$code err=$err")
                val raw = statsResp.errorBody()?.string()
                Log.e(TAG_UP, "stats fail code=${statsResp.code()} rawError=$raw")

                if (code == 429 || code in 500..599) {
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
                } else {
                    // 4xx -> kalıcı, kuyruğu tıkama
                    Result.success()
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG_UP, "timeout", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (e: IOException) {
            Log.w(TAG_UP, "io error", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG_UP, "unexpected", e)
            Result.success()
        }
    }

    private suspend fun uploadGps(api: DasApiService, file: File): Int? {
        val json = file.readText()
        val body = gson.fromJson(json, DasLogsGps::class.java)

        val resp: Response<Int> = api.uploadGPSLogs(body) // senin servis adın buysa
        return if (resp.isSuccessful) {
            val id = resp.body()
            Log.d(TAG_UP, "gps OK id=$id")
            id
        } else {
            val code = resp.code()
            val err = safeErr(resp.errorBody())
            Log.w(TAG_UP, "gps fail code=$code err=$err")
            // geçici hata -> retry/success kararı doWork'te
            null
        }
    }

    private suspend fun uploadAcc(api: DasApiService, file: File): Int? {
        val json = file.readText()
        val body = gson.fromJson(json, DasLogsAcc::class.java)

        val resp: Response<Int> = api.uploadAccLogs(body)
        return if (resp.isSuccessful) {
            val id = resp.body()
            Log.d(TAG_UP, "acc OK id=$id")
            id
        } else {
            val code = resp.code()
            val err = safeErr(resp.errorBody())
            Log.w(TAG_UP, "acc fail code=$code err=$err")
            null
        }
    }

    private fun buildStatsBody(
        driverId: Int,
        gpsLogId: Int,
        accLogId: Int,
        generalId: Int,
        trainId: Int,
        trackId: Int,
        direction: String
    ): DASDriverStatistics {
        val dirEnum = if (direction == "West to East") 0 else 1

        return DASDriverStatistics(
            driverId = driverId,
            gpsLogId = gpsLogId,
            accelerationLogId = accLogId,
            generalId = generalId,
            trainId = trainId,
            trackId = trackId,
            tracklineDirection = dirEnum,
            maxSpeed = 0.0,
            averageSpeed = 0.0,
            journeyTime = 0.0
        )
    }

    private fun decideRetryOrSuccess(reason: String): Result {
        Log.w(TAG_UP, reason)
        return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private fun safeErr(b: ResponseBody?): String? = try { b?.string() } catch (_: Exception) { null }

    private fun safeDelete(f: File) {
        try { f.delete() } catch (_: Exception) {}
    }
}
