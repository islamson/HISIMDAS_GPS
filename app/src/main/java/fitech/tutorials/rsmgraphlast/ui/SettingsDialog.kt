import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fitech.tutorials.rsmgraphlast.data.models.AllConfigParams

@Composable
fun SettingsDialog(
    initial: AllConfigParams,
    onDismiss: () -> Unit,
    onSave: (Boolean) -> Unit
) {
    var autoStTrans by remember { mutableStateOf(initial.autoStationTransition) }

    // Yardımcı TF'ler
    @Composable
    fun TfDecimal(value: String, onChange: (String)->Unit) = OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
    @Composable
    fun TfInt(value: String, onChange: (String)->Unit) = OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    @Composable
    fun onOffButton(value: Boolean, onChange: (Boolean)->Unit){
        Switch(checked = value, modifier = Modifier, onCheckedChange = onChange)
    }

    @Composable
    fun RowItem(label: String, content: @Composable () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, modifier = Modifier.weight(1.0f))
            Text("=", modifier = Modifier)
            Box(modifier = Modifier.weight(1.2f)) { content() }
        }
    }

    @Composable
    fun RowItem_OnOff(label: String, content: @Composable () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(label, modifier = Modifier.weight(1.0f))
            Text("=", modifier = Modifier)
            Box(modifier = Modifier.weight(1.2f)) { content() }
        }
    }

    val configuration = LocalConfiguration.current
    val maxDialogHeight = configuration.screenHeightDp.dp * 0.75f // ekranın %75’i kadar sınırla

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .widthIn(max = 680.dp)
        ) {
            // Kolonun yüksekliğini sınırla; ortadaki liste scroll edecek
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .padding(20.dp)
            ) {
                Text("Parametreler", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                // === SCROLLABLE ALAN ===
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // kalan yüksekliği kapla (üst başlık ve alt butonlar sabit)
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    item { RowItem_OnOff("Otomatik İstasyon Geçiş") { onOffButton(autoStTrans) { autoStTrans = it } }}
                    //item { RowItem("Max. Speed Diff")        { TfDecimal(maxSpeed) { maxSpeed = it } } }
                    //item { RowItem("Min. Position Diff")     { TfDecimal(minPos)   { minPos   = it } } }

                }

                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("İptal") }

                    Spacer(Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            onSave(autoStTrans)
                        }
                    ) { Text("Kaydet") }
                }
            }
        }
    }
}
