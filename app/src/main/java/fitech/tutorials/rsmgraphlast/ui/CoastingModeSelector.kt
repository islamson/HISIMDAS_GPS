import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fitech.tutorials.rsmgraphlast.data.models.dataClasses.CoastingMode

@Composable
fun CoastingModeSelector(
    selectedMode: CoastingMode,
    onModeSelected: (CoastingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedMode == CoastingMode.DYNAMIC,
            onClick = { onModeSelected(CoastingMode.DYNAMIC) },
            label = { Text("Dynamic") }
        )

        FilterChip(
            selected = selectedMode == CoastingMode.FIXED,
            onClick = { onModeSelected(CoastingMode.FIXED) },
            label = { Text("Fixed") }
        )

        FilterChip(
            selected = selectedMode == CoastingMode.STATIC,
            onClick = { onModeSelected(CoastingMode.STATIC) },
            label = { Text("Static") }
        )
    }
}