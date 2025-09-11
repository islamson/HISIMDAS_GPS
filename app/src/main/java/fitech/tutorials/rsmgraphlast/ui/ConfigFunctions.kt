package fitech.tutorials.rsmgraphlast.ui

import SettingsDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fitech.tutorials.rsmgraphlast.data.models.SettingsState

fun onSignOut(){

}

@Composable
fun rememberOnOpenSettings(
    initial: SettingsState,
    onSave: (SettingsState) -> Unit
): () -> Unit {
    var show by remember { mutableStateOf(false) }

    if (show) {
        SettingsDialog(
            initial = initial,
            onDismiss = { show = false },
            onSave = { saved ->
                onSave(saved)
                show = false
            }
        )
    }

    return { show = true }
}
