package fitech.tutorials.rsmgraphlast.data.location

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AccEngine {
    data class WorldAcc(val ax: Float, val ay: Float, val az: Float, val time_nanoseconds: Long)

    fun worldAcceleration(sensorManager: SensorManager): Flow<WorldAcc> = callbackFlow {
        val rotation = FloatArray(9)

        val rotationListener = object: SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotation, e.values)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

        val accListener = object: SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    val aWorld = FloatArray(3)
                    aWorld[0] = rotation[0]*e.values[0] + rotation[1]*e.values[1] + rotation[2]*e.values[2]
                    aWorld[1] = rotation[3]*e.values[0] + rotation[4]*e.values[1] + rotation[5]*e.values[2]
                    aWorld[2] = rotation[6]*e.values[0] + rotation[7]*e.values[1] + rotation[8]*e.values[2]
                    trySend(WorldAcc(aWorld[0], aWorld[1], aWorld[2], e.timestamp)).isSuccess
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(rotationListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(accListener, accSensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(rotationListener)
            sensorManager.unregisterListener(accListener)
        }
    }
}