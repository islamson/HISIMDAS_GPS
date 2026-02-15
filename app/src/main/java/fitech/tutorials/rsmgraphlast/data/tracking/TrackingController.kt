package fitech.tutorials.rsmgraphlast.data.tracking

import android.content.Context
import android.hardware.SensorManager
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.flow.StateFlow

interface TrackingController {
    val state: StateFlow<PositionSpeedState>
    suspend fun loadTrackFromAssets(context: Context, trackId: Int)
    fun start(
        fused: FusedLocationProviderClient,
        sensorManager: SensorManager,
        context: Context,
        initialOffset: Float        // Initial berthing position in meter
    )
    fun stop()
}