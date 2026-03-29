package fitech.tutorials.rsmgraphlast.data.models.dataClasses

import androidx.compose.material3.FabPosition
import com.google.gson.annotations.SerializedName

data class DASDriverStatistics(
    @SerializedName("driverId") val driverId: Int,
    @SerializedName("gpsLogId") val gpsLogId: Int,
    @SerializedName("accelerationLogId") val accelerationLogId: Int, // <-- backend ne istiyorsa O
    @SerializedName("generalId") val generalId: Int = 0,
    @SerializedName("trainId") val trainId: Int,
    @SerializedName("trackId") val trackId: Int,
    @SerializedName("tracklineDirection") val tracklineDirection: Int,
    @SerializedName("referenceSpeed") val referenceSpeed: List<Double>,
    @SerializedName("referencePosition") val referencePosition: List<Double>,
    @SerializedName("referenceTime") val referenceTime: List<Double>,
)
