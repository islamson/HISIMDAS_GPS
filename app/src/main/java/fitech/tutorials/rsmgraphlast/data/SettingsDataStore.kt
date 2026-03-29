package fitech.tutorials.rsmgraphlast.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.AllConfigParams
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.AdminConfigParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// BUNU bir top-level (dosyanın en üst seviyesinde) tanımla:
val Context.settingsDataStore by preferencesDataStore(name = "settings")

private object Keys {
    val MIN_SPEED_DIFF = floatPreferencesKey("min_speed_diff")
    val MAX_SPEED_DIFF = floatPreferencesKey("max_speed_diff")
    val MIN_POSITION_DIFF = floatPreferencesKey("min_position_diff")
    val ACC_DT = doublePreferencesKey("acc_dt")                 // 1.1.x ile gelir
    val GPS_NO_DATA_TIME = doublePreferencesKey("gps_no_data")  // 1.1.x ile gelir
    val CALIBRATION_DATA_NUMBER = intPreferencesKey("calibration_n")
    val AUTO_STATION_TRANSITION = booleanPreferencesKey("auto_station_transition")
}

// Tüm ayarları localden oku (yoksa default değerler döner)
fun Context.readAllConfigParams(): Flow<AllConfigParams> =
    settingsDataStore.data.map { p ->
        AllConfigParams(
            minSpeedDiff = p[Keys.MIN_SPEED_DIFF] ?: 1f,
            maxSpeedDiff = p[Keys.MAX_SPEED_DIFF] ?: 35f,
            minPositionDiff = p[Keys.MIN_POSITION_DIFF] ?: 1f,
            accSamplingTime = p[Keys.ACC_DT] ?: 0.2,
            gpsNoDataTime = p[Keys.GPS_NO_DATA_TIME] ?: 2.0,
            calibrationDataNumber = p[Keys.CALIBRATION_DATA_NUMBER] ?: 30,
            autoStationTransition = p[Keys.AUTO_STATION_TRANSITION] ?: true
        )
    }

// Sadece admin’den gelenleri yaz (kullanıcı ayarını bozma)
suspend fun Context.saveAdminConfig(admin: AdminConfigParams) {
    settingsDataStore.edit { p ->
        p[Keys.MIN_SPEED_DIFF] = admin.minimumSpeedDifference
        p[Keys.MAX_SPEED_DIFF] = admin.maximumSpeedDifference
        p[Keys.MIN_POSITION_DIFF] = admin.minimumPositionDifference
        p[Keys.ACC_DT] = admin.accelerationSamplingTime
        p[Keys.GPS_NO_DATA_TIME] = admin.gpsNoDataTime
        p[Keys.CALIBRATION_DATA_NUMBER] = admin.calibrationDataNumber
    }
}

// Sadece kullanıcı ayarı
suspend fun Context.saveAutoStationTransition(value: Boolean) {
    settingsDataStore.edit { it[Keys.AUTO_STATION_TRANSITION] = value }
}

// Tam paket kaydetmek istersen
suspend fun Context.saveAllConfig(all: AllConfigParams) {
    settingsDataStore.edit { p ->
        p[Keys.MIN_SPEED_DIFF] = all.minSpeedDiff
        p[Keys.MAX_SPEED_DIFF] = all.maxSpeedDiff
        p[Keys.MIN_POSITION_DIFF] = all.minPositionDiff
        p[Keys.ACC_DT] = all.accSamplingTime
        p[Keys.GPS_NO_DATA_TIME] = all.gpsNoDataTime
        p[Keys.CALIBRATION_DATA_NUMBER] = all.calibrationDataNumber
        p[Keys.AUTO_STATION_TRANSITION] = all.autoStationTransition
    }
}
