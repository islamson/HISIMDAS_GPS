package fitech.tutorials.rsmgraphlast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

    var expandedTrain by remember { mutableStateOf(false) }
    var expandedTrack by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select Train and Track",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Train Dropdown
        ExposedDropdownMenuBox(
            expanded = expandedTrain,
            onExpandedChange = { expandedTrain = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = selectedTrain?.name ?: "Select Train",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTrain) },
                modifier = Modifier
                    .fillMaxWidth()
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
                .padding(bottom = 32.dp)
        ) {
            OutlinedTextField(
                value = selectedTrack?.name ?: "Select Track",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTrack) },
                modifier = Modifier
                    .fillMaxWidth()
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

        Button(
            onClick = onContinue,
            enabled = selectedTrain != null && selectedTrack != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Continue")
        }
    }
} 