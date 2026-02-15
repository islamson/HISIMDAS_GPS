import com.google.gson.annotations.SerializedName

data class DASDriverStatistics(
    @SerializedName("driverId") val driverId: Int,
    @SerializedName("gpsLogId") val gpsLogId: Int,
    @SerializedName("accelerationLogId") val accelerationLogId: Int, // <-- backend ne istiyorsa O
    @SerializedName("generalId") val generalId: Int = 0,
    @SerializedName("trainId") val trainId: Int,
    @SerializedName("trackId") val trackId: Int,
    @SerializedName("tracklineDirection") val tracklineDirection: Int,
    @SerializedName("maxSpeed") val maxSpeed: Double = 0.0,
    @SerializedName("averageSpeed") val averageSpeed: Double = 0.0,
    @SerializedName("journeyTime") val journeyTime: Double = 0.0,
)
