package fitech.tutorials.rsmgraphlast

import android.Manifest
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.mikephil.charting.data.Entry
import com.google.android.gms.location.LocationServices
import fitech.tutorials.rsmgraphlast.ui.HomeScreen
import fitech.tutorials.rsmgraphlast.data.models.HomeViewModel
import fitech.tutorials.rsmgraphlast.data.models.LocationVMFactory
import fitech.tutorials.rsmgraphlast.data.models.LocationViewModel
import fitech.tutorials.rsmgraphlast.ui.CalibrationDialog
import fitech.tutorials.rsmgraphlast.ui.SpeedChart
import fitech.tutorials.rsmgraphlast.ui.theme.RSMGRAPHLASTTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val locationViewModel: LocationViewModel by viewModels { LocationVMFactory(homeViewModel) }
    private var sensorManager : SensorManager? = null
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
            }
            permissions.getOrDefault(Manifest.permission.WRITE_EXTERNAL_STORAGE, false) -> {
                //Writing to external storage access granted
            }
            else -> {
                // No location access granted.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure full screen mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Hide system bars
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Request location permissions
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        setContent {
            RSMGRAPHLASTTheme {
                var showHomeScreen by remember { mutableStateOf(true) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showHomeScreen) {
                        HomeScreen(
                            homeViewModel = homeViewModel,
                            onContinue = { showHomeScreen = false }
                        )
                    } else {
                        MainScreen(locationViewModel, homeViewModel, sensorManager!!)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(locationViewModel: LocationViewModel, homeViewModel: HomeViewModel, sensorManager : SensorManager) {
    val context = LocalContext.current
    val speed by locationViewModel.speed.collectAsState()
    val position by locationViewModel.position.collectAsState()
    val isTracking by locationViewModel.isTracking
    val dataNumber by locationViewModel.dataNumber.collectAsState()
    val initialStation by homeViewModel.selectedInitialStation.collectAsState()
    val finalStation by homeViewModel.selectedFinalStation.collectAsState()

    val speedLimitPoints by homeViewModel.speedLimitPoints.collectAsState()
    val velocityPoints = remember { mutableStateListOf<Entry>() }
    val speedCircleColor = remember { mutableStateOf(Color.Black) }
    val isOverLimit = remember { mutableStateOf(false) }

    val showCalibrationDialog = isTracking && (dataNumber <= 30)

    LaunchedEffect(speed, position) {
        // Only add points within our x-axis range
        velocityPoints.add(Entry(position + initialStation!!.berthingPosition, speed))  // Convert position to km for x-axis
        println("En son grafiğe giden hız:${speed}, Position:${position}")
        var speedLimit : Float? = null
        for (i in 0..speedLimitPoints.size-1){
            if(speedLimitPoints[i].x > position + initialStation!!.berthingPosition){
                if(i >= 1)
                    speedLimit = speedLimitPoints[i - 1].y
                break
            }
            else if(speedLimitPoints[i].x == position + initialStation!!.berthingPosition){
                speedLimit = speedLimitPoints[i].y
                break
            }
        }
        if(speedLimit != null){
            if(speed > speedLimit){
                speedCircleColor.value = Color.Red
                isOverLimit.value = true
            }
            else{
                speedCircleColor.value = Color.Black
                isOverLimit.value = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()){
        CalibrationDialog(showCalibrationDialog, dataNumber/30f)

        VelocityBadge(
            velocity = speed,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            color = speedCircleColor.value,
            isOverLimit = isOverLimit.value
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            SpeedChart(
                speedPoints = velocityPoints,
                speedLimits = speedLimitPoints,
                initialStationBerthing = initialStation!!.berthingPosition,
                finalStationBerthing = finalStation!!.berthingPosition,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        if (!isTracking) {
                            velocityPoints.clear()
                            locationViewModel.startTracking(LocationServices.getFusedLocationProviderClient(context), sensorManager)
                            velocityPoints.clear()
                        }
                    },
                    enabled = !isTracking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Start")
                    }
                }

                Button(
                    onClick = {
                        locationViewModel.stopTracking() },
                    enabled = isTracking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.White)
                        )
                        Text("Stop")
                    }
                }
            }
        }
    }

}

@Composable
private fun VelocityBadge(
    velocity: Float,
    modifier: Modifier = Modifier,
    color: Color,
    isOverLimit: Boolean
) {
    // Border rengini yumuşak geçişle animasyonla
    val borderColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(250),
        label = "borderColor"
    )

    // Limit aşıldığında sonsuz nabız animasyonları
    val infinite = rememberInfiniteTransition(label = "limitPulse")

    // Ölçek (pulse)
    val scale by if (isOverLimit) {
        infinite.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
    } else remember { mutableStateOf(1.0f) }

    // Spotlight parlaklık (alpha)
    val glowAlpha by if (isOverLimit) {
        infinite.animateFloat(
            initialValue = 0.0f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
    } else remember { mutableStateOf(0.0f) }

    // Spotlight yarıçapı (dp cinsinden)
    val glowRadiusDp by if (isOverLimit) {
        infinite.animateFloat(
            initialValue = 50f,
            targetValue = 80f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowRadiusDp"
        )
    } else remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val glowRadiusPx = with(density) { glowRadiusDp.dp.toPx() }

    // Dış kutu: spotlight’ın taşması için biraz büyük tuval
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(90.dp) // glow için alan
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Red.copy(alpha = glowAlpha), Color.Transparent),
                            center = center,
                            radius = glowRadiusPx
                        ),
                        radius = glowRadiusPx,
                        center = center
                    )
                }
            }
    ) {
        // Rozetin kendisi
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(60.dp)
                .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
                .border(2.dp, borderColor, CircleShape)
        ) {
            Text(
                text = "${velocity.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 22.sp,
                color = borderColor
            )
        }
    }
}

