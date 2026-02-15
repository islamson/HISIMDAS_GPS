package fitech.tutorials.rsmgraphlast.data.logging

import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CsvLogger {
    private var writer: BufferedWriter? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())

    fun open(file: File, header: String) {
        writer = file.bufferedWriter()
        writer?.apply { write(header); newLine(); flush() }
    }

    fun writeRow(row: String) {
        writer?.apply { write(row); newLine(); flush() }
    }

    fun appendGps(
        lat: Double,
        long: Double,
        alt: Double,
        position: Float,
        speed: Float
    ) {
        val timeString = timeFormat.format(Date())
        // Header: Timestamp,Latitude,Longitude,Altitude,Ax,Ay,Az,Position,Speed
        writeRow("$timeString,$lat,$long,$alt,-,-,-,${position.toInt()},${speed.toInt()}")
    }

    fun appendAcc(
        ax: Float,
        ay: Float,
        az: Float?,           // yoksa null geç
        position: Float,
        speed: Float
    ) {
        val timeString = timeFormat.format(Date())
        val azValue = az ?: 0.0
        // GPS alanlarını boş (-) bırak
        writeRow("$timeString,-,-,-,$ax,$ay,$azValue,${position.toInt()},${speed.toInt()}")
    }

    fun close() { writer?.close(); writer = null }
}
