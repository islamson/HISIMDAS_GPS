package fitech.tutorials.rsmgraphlast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onContinue: () -> Unit
) {
    val trains by viewModel.trains.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val selectedTrain by viewModel.selectedTrain.collectAsState()
    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val selectedDirection by viewModel.selectedDirection.collectAsState()
    val selectedInitialStation by viewModel.selectedInitialStation.collectAsState()
    val selectedFinalStation by viewModel.selectedFinalStation.collectAsState()

    var expandedTrain by remember { mutableStateOf(false) }
    var expandedTrack by remember { mutableStateOf(false) }
    var expandedDirection by remember { mutableStateOf(false) }
    var expandedInitialStation by remember { mutableStateOf(false) }
    var expandedFinalStation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select Train and Track",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 5.dp)
        )

        // Train Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedTrain,
            onExpandedChange = { expandedTrain = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
        ) {
            OutlinedTextField(
                value = selectedTrain?.name ?: "Train",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTrain) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expandedTrain,
                onDismissRequest = { expandedTrain = false }
            ) {
                trains.forEach { train ->
                    DropdownMenuItem(
                        text = { Text(train.name) },
                        onClick = {
                            viewModel.selectTrain(train)
                            expandedTrain = false
                        }
                    )
                }
            }
        }

        // Track Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedTrack,
            onExpandedChange = { expandedTrack = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
        ) {
            OutlinedTextField(
                value = selectedTrack?.name ?: "Track",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTrack) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expandedTrack,
                onDismissRequest = { expandedTrack = false }
            ) {
                tracks.forEach { track ->
                    DropdownMenuItem(
                        text = { Text(track.name) },
                        onClick = {
                            viewModel.selectTrack(track)
                            expandedTrack = false
                        }
                    )
                }
            }
        }

        // Direction Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedDirection,
            onExpandedChange = { expandedDirection = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
        ) {
            OutlinedTextField(
                value = selectedDirection ?: "Direction",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDirection) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expandedDirection,
                onDismissRequest = { expandedDirection = false }
            ) {
                for(i in 0..1) {
                    val directionText = if (i == 0) "West to East" else "East to West"
                    DropdownMenuItem(
                        text = { Text(directionText) },
                        onClick = {
                            viewModel.selectDirection(directionText)
                            expandedDirection = false
                        }
                    )
                }
            }
        }

        // Initial Station Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedInitialStation,
            onExpandedChange = { expandedInitialStation = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
        ) {
            OutlinedTextField(
                value = selectedInitialStation?.name ?: "Initial Station",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInitialStation) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expandedInitialStation,
                onDismissRequest = { expandedInitialStation = false }
            ) {
                val stations = if(selectedDirection == "West to East") selectedTrack?.stations else selectedTrack?.stationsInverted
                stations?.forEach{station->
                    DropdownMenuItem(
                        text = { Text(station.name) },
                        onClick = {
                            viewModel.selectInitialStation(station)
                            expandedInitialStation = false
                        }
                    )
                }
            }
        }

        // Final Station Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedFinalStation,
            onExpandedChange = { expandedFinalStation = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp)
        ) {
            OutlinedTextField(
                value = selectedFinalStation?.name ?: "Final Station",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFinalStation) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expandedFinalStation,
                onDismissRequest = { expandedFinalStation = false }
            ) {
                val stations = if(selectedDirection == "West to East") selectedTrack?.stations else selectedTrack?.stationsInverted
                stations?.forEach{station->
                    DropdownMenuItem(
                        text = { Text(station.name) },
                        onClick = {
                            viewModel.selectFinalStation(station)
                            expandedFinalStation = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = { onContinue()
                viewModel.calculateSpeedLimits()
                      },
            enabled = selectedTrain != null && selectedTrack != null && selectedDirection != null && selectedInitialStation != null && selectedFinalStation != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Text("Continue")
        }
    }
} 