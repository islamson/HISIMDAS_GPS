package fitech.tutorials.rsmgraphlast.data.models

data class AllConfigParams(
    val minSpeedDiff: Float = 3f,
    val maxSpeedDiff: Float = 35f,
    val minPositionDiff: Float = 1f,
    val accSamplingTime: Double = 0.2,
    val gpsNoDataTime: Double = 2.0,
    val calibrationDataNumber: Int = 30,
    var autoStationTransition : Boolean = true
)

