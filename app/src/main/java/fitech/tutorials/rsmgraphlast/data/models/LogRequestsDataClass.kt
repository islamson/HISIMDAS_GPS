import com.google.gson.annotations.SerializedName
import fitech.tutorials.rsmgraphlast.data.models.DasLogsAcc
import fitech.tutorials.rsmgraphlast.data.models.DasLogsGps

data class GpsLogsRequest(
    @SerializedName("I_Input") val input: DasLogsGps
)

data class AccLogsRequest(
    @SerializedName("I_Input") val input: DasLogsAcc
)
