package fitech.tutorials.rsmgraphlast.data.models.dataClasses

data class Track(
    val id: Int,
    val name: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
    val tracklineStart: Float,
    val tracklineEnd: Float,
    val speedLimits: SpeedLimits,
    val speedLimitsInverted: SpeedLimits,
    val stations: List<Station>,
    val stationsInverted: List<Station>,
    val latitude: List<Double>? = null,
    val longitude: List<Double>? = null,
    val altitude: List<Double>? = null,
    val position: List<Double>? = null
)

data class SpeedLimits(
    val x: List<Double>,
    val y: List<Float>,
    val count: Int,
    val xDataName: String,
    val yDataName: String
)

data class Station(
    val id: Int,
    val direction: Int,
    val name: String,
    val shortName: String,
    val startPosition: Float,
    val endPosition: Float,
    val berthingPosition: Float,
    val minimumJourneyTime: Float,
    val naturalJourneyTime: Float,
    val dwellTime: Float
) 