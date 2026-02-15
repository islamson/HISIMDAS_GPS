package fitech.tutorials.rsmgraphlast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fitech.tutorials.rsmgraphlast.data.models.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentControlBar(
    modifier: Modifier = Modifier,
    stations: List<Station>,
    initial: Station?,
    final: Station?,
    onSelectInitial: (Station) -> Unit,
    onSelectFinal: (Station) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    startEnabled: Boolean,
    startLabel: String,
    onStart: () -> Unit,
    stopEnabled: Boolean,
    onStop: () -> Unit,
) {
    var expInit by remember { mutableStateOf(false) }
    var expFinal by remember { mutableStateOf(false) }

    val h = 44.dp
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prev
            OutlinedButton(
                onClick = onPrev,
                modifier = Modifier.height(h)
            ) { Text("Prev") }

            Spacer(Modifier.width(8.dp))

            // Initial dropdown (küçük)
            ExposedDropdownMenuBox(
                expanded = expInit,
                onExpandedChange = { expInit = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = initial?.name ?: "İlk",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .height(h),
                    shape = shape,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expInit) }
                )
                ExposedDropdownMenu(expanded = expInit, onDismissRequest = { expInit = false }) {
                    stations.forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st.name) },
                            onClick = { onSelectInitial(st); expInit = false }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Start/Stop ortada
            Button(
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier.height(h)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = startLabel, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(startLabel)
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onStop,
                enabled = stopEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.height(h)
            ) {
                Box(Modifier.size(10.dp).background(Color.White))
                Spacer(Modifier.width(8.dp))
                Text("Stop", color = Color.White)
            }

            Spacer(Modifier.width(8.dp))

            // Final dropdown (küçük)
            ExposedDropdownMenuBox(
                expanded = expFinal,
                onExpandedChange = { expFinal = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = final?.name ?: "Son",
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .height(h),
                    shape = shape,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expFinal) }
                )
                ExposedDropdownMenu(expanded = expFinal, onDismissRequest = { expFinal = false }) {
                    stations.forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st.name) },
                            onClick = { onSelectFinal(st); expFinal = false }
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Next
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.height(h)
            ) { Text("Next") }
        }
    }
}

