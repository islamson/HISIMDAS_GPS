package fitech.tutorials.rsmgraphlast.data.models

data class Track(
    val id: Int,
    val name: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
    val tracklineStart: Int,
    val tracklineEnd: Int,
    val speedLimits: SpeedLimits,
    val speedLimitsInverted: SpeedLimits,
    val stations: List<Station>,
    val stationsInverted: List<Station>
)

data class SpeedLimits(
    val x: List<Double>,
    val y: List<Int>,
    val count: Int,
    val xDataName: String,
    val yDataName: String
)

data class Station(
    val id: Int,
    val direction: Int,
    val name: String,
    val shortName: String,
    val startPosition: Int,
    val endPosition: Int,
    val berthingPosition: Int,
    val minimumJourneyTime: Int,
    val naturalJourneyTime: Int,
    val dwellTime: Int
) 