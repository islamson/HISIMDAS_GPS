package fitech.tutorials.rsmgraphlast.data.models

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel() : ViewModel() {
    private val _minSpeedDiff = MutableStateFlow<Float>(1.5f)
    val minSpeedDiff : StateFlow<Float> = _minSpeedDiff

    private val _maxSpeedDiff = MutableStateFlow<Float>(35f)
    val maxSpeedDiff : StateFlow<Float> = _maxSpeedDiff

    private val _minPositionDiff = MutableStateFlow<Float>(1f)
    val minPositionDiff : StateFlow<Float> = _minPositionDiff

    private val _accDt = MutableStateFlow<Double>(0.2)
    val accDt : StateFlow<Double> = _accDt

    private val _gpsNoDataTime = MutableStateFlow<Double>(2.0)
    val gpsNoDataTime : StateFlow<Double> = _gpsNoDataTime

    private val _calibrationDataNumber = MutableStateFlow<Int>(30)
    val calibrationDataNumber : StateFlow<Int> = _calibrationDataNumber

    private val _autoStateTransition = MutableStateFlow<Boolean>(true)
    val autoStateTransition : StateFlow<Boolean> = _autoStateTransition

    fun selectMinSpeedDiff(value: Float){
        _minSpeedDiff.value = value
    }

    fun selectMaxSpeedDiff(value: Float){
        _maxSpeedDiff.value = value
    }

    fun selectMinPositionDiff(value: Float){
        _minPositionDiff.value = value
    }

    fun selectAccDt(value: Double){
        _accDt.value = value
    }

    fun selectGPSNoDataTime(value: Double){
        _gpsNoDataTime.value = value
    }

    fun selectCalibrationDataNumber(value: Int){
        _calibrationDataNumber.value = value
    }

    fun selectAutoStateTransition(value: Boolean){
        _autoStateTransition.value = value
    }

}