package fitech.tutorials.rsmgraphlast

import MovementAlertBanner
import android.Manifest
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.mikephil.charting.data.Entry
import com.google.android.gms.location.LocationServices
import fitech.tutorials.rsmgraphlast.ui.HomeScreen
import fitech.tutorials.rsmgraphlast.data.models.HomeViewModel
import fitech.tutorials.rsmgraphlast.data.models.LocationVMFactory
import fitech.tutorials.rsmgraphlast.data.models.LocationViewModel
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.Station
import fitech.tutorials.rsmgraphlast.ui.CalibrationDialog
import fitech.tutorials.rsmgraphlast.ui.LoginScreen
import fitech.tutorials.rsmgraphlast.ui.SegmentControlBar
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
                val navController = rememberNavController()

                val isLoggedIn by homeViewModel.isLoggedIn.collectAsState()
                val isLoading by homeViewModel.isLoggingIn.collectAsState()
                val loginError by homeViewModel.loginError.collectAsState()

                // login başarılı olunca otomatik main'e geç
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                var showHomeScreen by remember { mutableStateOf(true) }
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            isLoading = isLoading,
                            error = loginError,
                            onLogin = { u, p -> homeViewModel.loginAndLoad(u, p) }
                        )
                    }

                    composable("main") {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            if (showHomeScreen) {
                                HomeScreen(
                                    homeViewModel = homeViewModel,
                                    onContinue = { showHomeScreen = false },
                                    onSignOut = {
                                        homeViewModel.logout()
                                        showHomeScreen = true
                                        homeViewModel.stopCoastingSimLoop()
                                        navController.navigate("login") {
                                            popUpTo("main") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            } else {
                                MainScreen(locationViewModel, homeViewModel, sensorManager!!, onBackToHome = { showHomeScreen = true } )
                            }
                        }
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
    val borderColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(250),
        label = "borderColor"
    )

    // Limit aşıldığında sonsuz nabız animasyonları
    val infinite = rememberInfiniteTransition(label = "limitPulse")

    // Ölçek
    val scale by if (isOverLimit) {
        infinite.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.5f,
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
            targetValue = 0.40f,
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
            initialValue = 300f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowRadiusDp"
        )
    } else remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    val glowRadiusPx = with(density) { glowRadiusDp.dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(90.dp)
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


@Composable
fun MainScreen(locationViewModel: LocationViewModel, homeViewModel: HomeViewModel, sensorManager : SensorManager, onBackToHome: () -> Unit) {
    val context = LocalContext.current
    val speed by locationViewModel.speed.collectAsState()
    val position by locationViewModel.position.collectAsState()
    val isTracking by locationViewModel.isTracking
    val dataNumber by locationViewModel.dataNumber.collectAsState()
    val absCenterPos by locationViewModel.absCenterPos.collectAsState()
    val initialStation by homeViewModel.selectedInitialStation.collectAsState()
    val finalStation by homeViewModel.selectedFinalStation.collectAsState()
    val segInit by homeViewModel.segmentInitialStation.collectAsState()
    val segFinal by homeViewModel.segmentFinalStation.collectAsState()
    val selectedTrack by homeViewModel.selectedTrack.collectAsState()
    val allConfigParams by homeViewModel.allConfigParams.collectAsState()
    val speedLimitPoints by homeViewModel.speedLimitPoints.collectAsState()
    val direction by homeViewModel.selectedDirection.collectAsState()
    val dasProfile by homeViewModel.dasProfilePoints.collectAsState()
    val coastingData by homeViewModel.coastingBand.collectAsState()
    val movementHint by homeViewModel.movementHint.collectAsState(initial = null)
    val velocityPoints = remember { mutableStateListOf<Entry>() }
    val speedCircleColor = remember { mutableStateOf(Color.Black) }
    val isOverLimit = remember { mutableStateOf(false) }

    // Segment bitti mi? (Continue bir defalık açılacak)
    var pendingContinue by remember { mutableStateOf(false) }

    // Hangi istasyonda segment bitti? (genelde bir önceki segmentin final'i)
    var arrivedStation by remember { mutableStateOf<Station?>(null) }

    val showCalibrationDialog = isTracking && (dataNumber <= allConfigParams.calibrationDataNumber)

    val train by homeViewModel.selectedTrain.collectAsState()
    val dir = direction ?: "West to East"
    val sign = if (dir == "West to East") 1 else -1

    val activeFinal = remember(pendingContinue, segFinal, arrivedStation) {
        // pendingContinue true ise artık varış istasyonunu kilitle
        if (pendingContinue) arrivedStation else segFinal
    }

    val targetCenter = remember(activeFinal, train, dir) {
        if (activeFinal == null || train == null) null
        else (activeFinal!!.berthingPosition + (sign * train!!.totalLength / 2.0)).toFloat()
    }

    val arrived = remember(absCenterPos, targetCenter, dir) {
        val t = targetCenter ?: return@remember false
        if (dir == "West to East") absCenterPos >= (t - 10f)
        else absCenterPos <= (t + 10f)
    }


    LaunchedEffect(selectedTrack!!.id) {
        locationViewModel.loadTrackFromAssets(context, selectedTrack!!.id)
    }

    LaunchedEffect(arrived) {
        if (isTracking && arrived && !pendingContinue) {
            pendingContinue = true
            arrivedStation = segFinal          // segmenti hangi istasyonda bitirdik
            homeViewModel.stopCoastingSimLoop()
        }
    }



    LaunchedEffect(speed, absCenterPos) {
        velocityPoints.add(Entry(absCenterPos, speed))
        println("En son grafiğe giden hız:${speed}, Position:${position}")

        val speedLimit = homeViewModel.speedLimitAt(absCenterPos)
        if(speed > speedLimit){
            speedCircleColor.value = Color.Red
            isOverLimit.value = true
        }
        else{
            speedCircleColor.value = Color.Black
            isOverLimit.value = false
        }
    }

    val stationList = remember(selectedTrack, direction) {
        if (selectedTrack == null || direction == null) emptyList()
        else if (direction == "West to East") selectedTrack!!.stations
        else selectedTrack!!.stationsInverted
    }

    val startLabel = if (!isTracking) "Start" else "Continue"
    val startEnabled = (!isTracking) || (isTracking && pendingContinue)


    Box(modifier = Modifier.fillMaxSize()){
        var showStopWarning by remember { mutableStateOf(false) }

        BackHandler(enabled = true) {
            if (isTracking) showStopWarning = true
            else onBackToHome()
        }

        if (showStopWarning) {
            AlertDialog(
                onDismissRequest = { showStopWarning = false },
                title = { Text("Çıkış yapılamıyor") },
                text = { Text("Ana ekrandan çıkmadan önce lütfen Stop’a bas ve tracking’i durdur.") },
                confirmButton = {
                    TextButton(onClick = { showStopWarning = false }) { Text("Tamam") }
                }
            )
        }

        MovementAlertBanner(
            text = movementHint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    if (isTracking) showStopWarning = true
                    else onBackToHome()
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0x66000000),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Spacer(Modifier.weight(1f))
        }

        CalibrationDialog(showCalibrationDialog, dataNumber/allConfigParams.calibrationDataNumber.toFloat())

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
                tracklineStart = selectedTrack!!.tracklineStart,
                tracklineEnd = selectedTrack!!.tracklineEnd,
                initialBerthing = initialStation!!.berthingPosition,
                finalBerthing = finalStation!!.berthingPosition,
                dasProfile = dasProfile,
                coastingBand = coastingData,
                direction = direction!!,
                modifier = Modifier.weight(1f)
            )

            SegmentControlBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),

                stations = stationList,
                initial = segInit,
                final = segFinal,

                onSelectInitial = { st ->
                    homeViewModel.setSegmentInitial(st)
                    homeViewModel.calculateSpeedLimits()
                    homeViewModel.fetchAllOutProfileOnce()
                },
                onSelectFinal = { st ->
                    homeViewModel.setSegmentFinal(st)
                    homeViewModel.calculateSpeedLimits()
                    homeViewModel.fetchAllOutProfileOnce()
                },

                onPrev = {
                    homeViewModel.goPrevSegment()
                    homeViewModel.calculateSpeedLimits()
                    homeViewModel.fetchAllOutProfileOnce()
                },
                onNext = {
                    homeViewModel.goNextSegment()
                    homeViewModel.calculateSpeedLimits()
                    homeViewModel.fetchAllOutProfileOnce()
                },

                startEnabled = startEnabled,
                startLabel = startLabel,
                onStart = {
                    if (!isTracking) {
                        velocityPoints.clear()
                        homeViewModel.resetTripReferences()
                        locationViewModel.startTracking(
                            LocationServices.getFusedLocationProviderClient(context),
                            sensorManager,
                            context
                        )
                    } else if (pendingContinue) {
                        // Eğer hala eski segment ekranındaysan: segFinal == arrivedStation
                        val onFinishedSegmentScreen = (arrivedStation != null && segFinal != null && segFinal!!.id == arrivedStation!!.id)

                        if (onFinishedSegmentScreen) {
                            // Kullanıcı Nexte basmamışsa, Continue basınca otomatik sonraki segmente geç
                            homeViewModel.goNextSegment()
                            homeViewModel.calculateSpeedLimits()
                            homeViewModel.fetchAllOutProfileOnce()
                        }

                        velocityPoints.clear()
                        homeViewModel.resetSegmentTimer()

                        homeViewModel.startCoastingSimLoop(
                            positionProvider = { absCenterPos },
                            speedProvider = { speed }
                        )

                        // Consume
                        pendingContinue = false
                        arrivedStation = null
                    }
                },

                stopEnabled = isTracking,
                onStop = {
                    locationViewModel.stopTracking()
                }
            )
        }
    }

}
