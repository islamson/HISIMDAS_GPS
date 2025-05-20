package fitech.tutorials.rsmgraphlast

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import co.yml.charts.common.model.Point
import com.google.android.gms.location.LocationServices
import fitech.tutorials.rsmgraphlast.ui.HomeScreen
import fitech.tutorials.rsmgraphlast.ui.HomeViewModel
import fitech.tutorials.rsmgraphlast.ui.LocationViewModel
import fitech.tutorials.rsmgraphlast.ui.SpeedChart
import fitech.tutorials.rsmgraphlast.ui.theme.RSMGRAPHLASTTheme

class MainActivity : ComponentActivity() {
    private val locationViewModel: LocationViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

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
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            RSMGRAPHLASTTheme {
                var showHomeScreen by remember { mutableStateOf(true) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showHomeScreen) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onContinue = { showHomeScreen = false }
                        )
                    } else {
                        MainScreen(locationViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val speed by viewModel.speed.collectAsState(initial = 0f)
    val position by viewModel.position.collectAsState(initial = 0f)
    val isTracking by viewModel.isTracking

    val points = remember { mutableStateListOf<Point>() }

    LaunchedEffect(speed, position) {
        if (position <= 14000) {  // Only add points within our x-axis range
            points.add(Point(position / 1000f, speed))  // Convert position to km for x-axis
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpeedChart(
            speedPoints = points,
            currentSpeed = speed,
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
                        points.clear()
                        viewModel.startTracking(
                            LocationServices.getFusedLocationProviderClient(context)
                        )
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
                onClick = { viewModel.stopTracking() },
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
