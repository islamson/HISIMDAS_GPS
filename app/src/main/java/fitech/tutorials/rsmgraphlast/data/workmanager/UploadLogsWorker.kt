package fitech.tutorials.rsmgraphlast.work

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

class UploadLogsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_KIND = "kind"           // "gps" | "acc"
        const val KEY_FILE_PATH = "file_path" // JSON dosya yolu
        const val KEY_TOKEN = "token"         // Authorization token

        private const val BASE_URL = "http://160.75.159.6:3012/api/das/"
        private const val TAG_UP = "UPLOAD"
        private const val MAX_RETRIES = 5
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.success()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.success()
        val token = inputData.getString(KEY_TOKEN)

        val file = File(filePath)
        if (!file.exists()) {
            // Asla kuyruğu kilitleme:
            Log.w(TAG_UP, "file not found: $filePath -> success to skip")
            return Result.success()
        }

        val api: DasApiService = ApiFactory.createDasServiceLong(BASE_URL, token)

        return try {
            val json = file.readText()

            val resp: Response<Int> = when (kind) {
                "gps" -> {
                    val body = gson.fromJson(json, DasLogsGps::class.java)
                    api.uploadGPSLogs(body)
                }
                "acc" -> {
                    val body = gson.fromJson(json, DasLogsAcc::class.java)
                    api.uploadAccLogs(body)
                }
                else -> {
                    Log.w(TAG_UP, "unknown kind=$kind")
                    Result.success()
                    return Result.success()
                }
            }

            if (resp.isSuccessful) {
                // Başarılı → dosyayı sil
                safeDelete(file)
                Log.d(TAG_UP, "$kind OK")
                Result.success()
            } else {
                val code = resp.code()
                val err = safeErr(resp.errorBody())
                Log.d(TAG_UP, "$kind fail code=$code err=$err")

                // Geçici hatalar → sınırlı retry, yoksa success
                if (code == 429 || code in 500..599) {
                    if (runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        Result.success()
                    }
                } else {
                    // 4xx gibi kalıcı hatalar → kuyruğu durdurmamak için success
                    Result.success()
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG_UP, "$kind timeout", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (e: IOException) {
            Log.w(TAG_UP, "$kind io error", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG_UP, "$kind unexpected", e)
            Result.success()
        }
    }

    private fun safeErr(b: ResponseBody?): String? = try { b?.string() } catch (_: Exception) { null }

    private fun safeDelete(f: File) {
        try { f.delete() } catch (_: Exception) { }
    }
}
