package fitech.tutorials.rsmgraphlast.data.models.dataClasses

data class DasLogsGps(
    val createdAt: String,   // ISO-8601, örn: Instant.now().toString()
    val dataNumber: Int,
    val time: List<Double>,
    val latitude: List<Double>,
    val longitude: List<Double>,
    val altitude: List<Double>,
    val position: List<Double>,
    val speed: List<Double>
)

data class DasLogsAcc(
    val createdAt: String,
    val dataNumber: Int,
    val time: List<Double>,
    val axisX: List<Double>,
    val axisY: List<Double>,
    val axisZ: List<Double>
)