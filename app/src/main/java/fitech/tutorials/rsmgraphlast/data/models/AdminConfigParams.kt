package fitech.tutorials.rsmgraphlast.data.models

data class AdminConfigParams(
    val calibrationDataNumber: Int = 30,
    val minimumSpeedDifference: Float = 1f,
    val maximumSpeedDifference: Float = 35f,
    val minimumPositionDifference: Float = 1f,
    val accelerationSamplingTime: Double = 0.2,
    val gpsNoDataTime: Double = 2.0,
    val autoStationTransition: Boolean = true
)
