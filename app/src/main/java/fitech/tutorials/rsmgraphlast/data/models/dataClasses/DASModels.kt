package fitech.tutorials.rsmgraphlast.data.models.dataClasses

import com.google.gson.annotations.SerializedName

data class DASInput(
    @SerializedName("generalId") val generalId: Int = 1,
    @SerializedName("trainId") val trainId: Int = 0,
    @SerializedName("trackId") val trackId: Int = 0,
    @SerializedName("tracklineDirection") val tracklineDirection: Int = 0, // "West_to_East" / "East_to_West"
    @SerializedName("initialPosition") val initialPosition: Double,
    @SerializedName("finalPosition") val finalPosition: Double,
    @SerializedName("initialSpeed") val initialSpeed: Double = 0.0,
    @SerializedName("finalSpeed") val finalSpeed: Double = 0.0,
    @SerializedName("coastingAllowedTime") val coastingAllowedTime: Double = 0.0
)

data class DASOutput(
    val trainPositionTime: Table = Table(),
    val trainSpeedTime: Table = Table(),
    val trainAccelerationTime: Table = Table(),
    val trainJerkTime: Table = Table(),
    val trainMovementStateTime: Table = Table(),
    val coastingRegion: CoastingData = CoastingData(),
    val journeyTime: Double = 0.0,
    val errorStatus: String = "NoError"
)

data class Table(
    val x: List<Double> = emptyList(),
    val y: List<Float>  = emptyList()
)

data class CoastingData(
    val id: Int = 0,
    val isActive: Boolean = false,
    val startPosition: Double = 0.0,
    val endPosition: Double = 0.0,
    val coastingSpeed: Double = 0.0,
    val remotoringSpeed: Double = 0.0,
    val direction: String = "West_to_East",
    val trainType: String = "",
    val notes: String = ""
)
