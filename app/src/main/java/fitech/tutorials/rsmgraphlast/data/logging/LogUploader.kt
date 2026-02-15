package fitech.tutorials.rsmgraphlast.data.logging

import android.content.Context
import androidx.work.*
import fitech.tutorials.rsmgraphlast.work.UploadLogsWorker

class LogUploader(private val appContext: Context) {
    fun enqueue(kind: String, path: String, token: String?) {
        val net = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()

        val input = workDataOf(
            UploadLogsWorker.KEY_KIND to kind,
            UploadLogsWorker.KEY_FILE_PATH to path,
            UploadLogsWorker.KEY_TOKEN to token
        )

        val req = OneTimeWorkRequestBuilder<UploadLogsWorker>()
            .setInputData(input)
            .setConstraints(net)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(appContext).enqueue(req)
    }
}
