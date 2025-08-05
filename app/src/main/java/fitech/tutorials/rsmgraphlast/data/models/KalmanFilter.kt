package fitech.tutorials.rsmgraphlast.data.models

import androidx.compose.runtime.remember
import okhttp3.internal.notify
import org.ejml.simple.SimpleMatrix
import java.nio.DoubleBuffer
import kotlin.math.sqrt


class KalmanFilter(
    private val processNoise : Double = 1.0,
    private val measurementNoise : Double = 10.0
){
    // 4x1 state vector [x, vx, y, vy]
    private var x = SimpleMatrix(4,1)

    //4x4 covariance matrix
    private var P = SimpleMatrix.identity(4).scale(1.0)

    //Observation matrix (we only observe position)
    private val H = SimpleMatrix(
        arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0)
        )
    )

    //4x4 Identity Matrix
    private val I = SimpleMatrix.identity(4)

    //Process Noise Covariance
    private val Q = SimpleMatrix.identity(4).scale(processNoise)

    //Measurement Noise Covariance
    private val R = SimpleMatrix.identity(2).scale(measurementNoise)

    //Initialize the initial state vector with first two accurate gps data
    fun init(initialX : Double, initialY : Double, initialVx : Double = 0.0, initialVy : Double = 0.0){
        x = SimpleMatrix(4, 1).apply {
            set(0, 0, initialX)
            set(1, 0, initialVx)
            set(2, 0, initialY)
            set(3, 0, initialVy)
        }
    }

    //Predict functions in Kalman Filter
    //x^k = A * x^k-1 + B * uk + wk     (x^k predicted state in time k)
    //P^k = A * P^k-1 * A^T + Q         (P^k predicted covariance matrix)
    fun predict(ax : Double = 0.0, ay : Double = 0.0, dt : Double = 0.05){
        val u = SimpleMatrix(2, 1).apply {
            set(0, 0, ax)
            set(1, 0, ay)
        }
        //State transition matrix
        val A = SimpleMatrix(
            arrayOf(
                doubleArrayOf(1.0, dt, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0, dt),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            )
        )

        //Control Matrix (for acceleration)
        val B = SimpleMatrix(
            arrayOf(
                doubleArrayOf((dt * dt * 0.5), 0.0),
                doubleArrayOf(dt, 0.0),
                doubleArrayOf(0.0, (dt * dt * 0.5)),
                doubleArrayOf(0.0, dt)
            )
        )

        x = A.mult(x).plus(B.mult(u))
        P = A.mult(P).mult(A.transpose()).plus(Q)
    }

    //Update step
    //K = P^k * H^T * (H * P^k * H^T + R)^-1
    //x = x^k + K(z - H * x^k)
    //P^k = (1 - K * H) * P^k
    fun update(measuredX : Double, measuredY : Double){
        val z = SimpleMatrix(2, 1).apply {
            set(0, 0, measuredX)
            set(1, 0, measuredY)
        }

        val y = z.minus(H.mult(x)) //Innovation
        val S = H.mult(P).mult(H.transpose()).plus(R) //Innovation Covariance
        val K = P.mult(H.transpose()).mult(S.invert()) //Kalman Gain

        x = x.plus(K.mult(y))
        P = (I.minus(K.mult(H))).mult(P)
    }

    fun getFilteredX() : Double = x[0,0]
    fun getFilteredY() : Double = x[2,0]
    fun getFilteredVx() : Double = x[1,0]
    fun getFilteredVy() : Double = x[3,0]

    fun getSpeed() : Float{
        return sqrt((x[1,0] * x[1,0] + x[3,0] * x[3,0])).toFloat()
    }

    fun getPosition() : Float{
        return sqrt((x[0,0] * x[0,0] + x[2,0] * x[2,0])).toFloat()
    }
}