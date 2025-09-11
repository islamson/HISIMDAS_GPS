package fitech.tutorials.rsmgraphlast.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fitech.tutorials.rsmgraphlast.R
import fitech.tutorials.rsmgraphlast.data.models.HomeViewModel
import fitech.tutorials.rsmgraphlast.data.models.SettingsState
import fitech.tutorials.rsmgraphlast.data.models.SettingsViewModel
import fitech.tutorials.rsmgraphlast.data.models.Station

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onContinue: () -> Unit,
) {
    val trains by homeViewModel.trains.collectAsState()
    val tracks by homeViewModel.tracks.collectAsState()
    val selectedTrain by homeViewModel.selectedTrain.collectAsState()
    val selectedTrack by homeViewModel.selectedTrack.collectAsState()
    val selectedDirection by homeViewModel.selectedDirection.collectAsState()
    val selectedInitialStation by homeViewModel.selectedInitialStation.collectAsState()
    val selectedFinalStation by homeViewModel.selectedFinalStation.collectAsState()

    var expandedTrain by remember { mutableStateOf(false) }
    var expandedTrack by remember { mutableStateOf(false) }
    var expandedDirection by remember { mutableStateOf(false) }
    var expandedInitialStation by remember { mutableStateOf(false) }
    var expandedFinalStation by remember { mutableStateOf(false) }

    // Durulmayacak istasyonlar için state
    var showSkipSheet by remember { mutableStateOf(false) }
    var skipStations by remember { mutableStateOf<Set<Station>>(emptySet()) } // şimdilik isimle tutuyoruz

    // Seçili yöne göre sıralı istasyon listesi (initial/final için kullandığınla aynı mantık)
    val orderedStations = remember(selectedTrack, selectedDirection) {
        if (selectedDirection == "West to East") selectedTrack?.stations
        else selectedTrack?.stationsInverted
    }

    // Initial–Final arasındaki istasyonları çıkar (uçlar hariç)
    val intermediateStations = remember(orderedStations, selectedInitialStation, selectedFinalStation) {
        val list = orderedStations ?: emptyList()
        val s = selectedInitialStation
        val f = selectedFinalStation
        if (s == null || f == null) emptyList()
        else {
            val i1 = list.indexOf(s)
            val i2 = list.indexOf(f)
            if (i1 == -1 || i2 == -1) emptyList()
            else {
                val start = minOf(i1, i2)
                val end = maxOf(i1, i2)
                if (end - start <= 1) emptyList()
                else list.subList(start + 1, end)
            }
        }
    }


    // Şimdilik sabit; sonra ViewModel'den oku
    val currentSettings by remember {
        mutableStateOf(
            SettingsState(
                minSpeedDiff = 1.5f,
                maxSpeedDiff = 35f,
                minPositionDiff = 1f,
                accDt = 0.2,
                gpsNoDataTime = 2.0,
                calibrationDataNumber = 100,
                autoStationTransition = true
            )
        )
    }

    val onOpenSettings = rememberOnOpenSettings(
        initial = currentSettings,
        onSave = { new ->
            // TODO: Burada ViewModel’e bağla (örn: viewModel.updateSettings(new))
            // şimdilik log/assignment yapabilirsin
            // currentSettings = new  --> eğer mutableStateOf ile var olarak tutarsan güncelle
        }
    )


    // Ortak TextField görünümü (beyaz kenarlık, transparan arka plan)
    val tfColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        disabledTextColor = Color.White.copy(alpha = 1f),
        cursorColor = Color.White,
        focusedIndicatorColor = Color.Blue,
        unfocusedIndicatorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedTrailingIconColor = Color.White,
        unfocusedTrailingIconColor = Color.White
    )
    val tfShape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        // SOLDAN SAĞA: %0, %36, %100
                        0.00f to Color(0xFF797979).copy(alpha = 0.75f), // açık gri (sol)
                        0.36f to Color(0xFF4B5A74).copy(alpha = 0.91f), // gri-mavi (orta)
                        1.00f to Color(0xFF0D234A)  // koyu lacivert (sağ)
                    ),
                    startX = 0f, endX = Float.POSITIVE_INFINITY
                )
            )
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        // Üst ikonlar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onSignOut() }) {
                Icon(
                    painter = painterResource(id = R.drawable.signout_icon),
                    contentDescription = "Sign out",
                    tint = Color.White
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(id = R.drawable.settings_icon),
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        // Form içeriği (iki sütun)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                // SOL SÜTUN
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Train
                    ExposedDropdownMenuBox(
                        expanded = expandedTrain,
                        onExpandedChange = { expandedTrain = it }
                    ) {
                        OutlinedTextField(
                            value = selectedTrain?.name ?: "Tren",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTrain)
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp),
                            shape = tfShape,
                            colors = tfColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
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
                                        homeViewModel.selectTrain(train)
                                        expandedTrain = false
                                    }
                                )
                            }
                        }
                    }

                    // Track
                    ExposedDropdownMenuBox(
                        expanded = expandedTrack,
                        onExpandedChange = { expandedTrack = it }
                    ) {
                        OutlinedTextField(
                            value = selectedTrack?.name ?: "Hat",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTrack)
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp),
                            shape = tfShape,
                            colors = tfColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
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
                                        homeViewModel.selectTrack(track)
                                        expandedTrack = false
                                    }
                                )
                            }
                        }
                    }

                    // Direction
                    ExposedDropdownMenuBox(
                        expanded = expandedDirection,
                        onExpandedChange = { expandedDirection = it }
                    ) {
                        OutlinedTextField(
                            value = selectedDirection ?: "Yön",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDirection)
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp),
                            shape = tfShape,
                            colors = tfColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDirection,
                            onDismissRequest = { expandedDirection = false }
                        ) {
                            listOf("West to East", "East to West").forEach { dir ->
                                DropdownMenuItem(
                                    text = { Text(dir) },
                                    onClick = {
                                        homeViewModel.selectDirection(dir)
                                        expandedDirection = false
                                    }
                                )
                            }
                        }
                    }
                }

                // SAĞ SÜTUN
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // İlk İstasyon
                    ExposedDropdownMenuBox(
                        expanded = expandedInitialStation,
                        onExpandedChange = { expandedInitialStation = it }
                    ) {
                        OutlinedTextField(
                            value = selectedInitialStation?.name ?: "İlk İstasyon",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedInitialStation)
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp),
                            shape = tfShape,
                            colors = tfColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedInitialStation,
                            onDismissRequest = { expandedInitialStation = false }
                        ) {
                            val stations = if (selectedDirection == "West to East")
                                selectedTrack?.stations else selectedTrack?.stationsInverted
                            stations?.forEach { station ->
                                DropdownMenuItem(
                                    text = { Text(station.name) },
                                    onClick = {
                                        homeViewModel.selectInitialStation(station)
                                        expandedInitialStation = false
                                    }
                                )
                            }
                        }
                    }

                    // Son İstasyon
                    ExposedDropdownMenuBox(
                        expanded = expandedFinalStation,
                        onExpandedChange = { expandedFinalStation = it }
                    ) {
                        OutlinedTextField(
                            value = selectedFinalStation?.name ?: "Son İstasyon",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFinalStation)
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp),
                            shape = tfShape,
                            colors = tfColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFinalStation,
                            onDismissRequest = { expandedFinalStation = false }
                        ) {
                            val stations = if (selectedDirection == "West to East")
                                selectedTrack?.stations else selectedTrack?.stationsInverted
                            stations?.forEach { station ->
                                DropdownMenuItem(
                                    text = { Text(station.name) },
                                    onClick = {
                                        homeViewModel.selectFinalStation(station)
                                        expandedFinalStation = false
                                    }
                                )
                            }
                        }
                    }
                    // "Durulmayacak İstasyonlar" butonu
                    OutlinedButton(
                        onClick = { showSkipSheet = true },
                        enabled = selectedInitialStation != null &&
                                selectedFinalStation != null &&
                                intermediateStations.isNotEmpty(),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color.White
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContentColor = Color.LightGray
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        val summary = if (skipStations.isEmpty())
                            "Durulmayacak İstasyonlar: Hiçbiri"
                        else
                            "Durulmayacak İstasyonlar: ${skipStations.size} tane seçildi"
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start){
                            Text(fontSize = 16.sp,text = summary, textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }

        // Alt kısım: Gradient "Başla" butonu
        Button(
            onClick = {
                onContinue()
                homeViewModel.calculateSpeedLimits()
            },
            enabled = selectedTrain != null &&
                    selectedTrack != null &&
                    selectedDirection != null &&
                    selectedInitialStation != null &&
                    selectedFinalStation != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 15.dp)
                .fillMaxWidth(0.65f)
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF001B5E), // koyu mavi
                            Color(0xFF0A45FF)  // canlı mavi
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        ) {
            Text("Başla", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }

    // === Alttan açılan seçim paneli ===
    if (showSkipSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Sheet içinde geçici seçim; her açılışta mevcutu kopyalayalım
        var tempSelection by remember(showSkipSheet) { mutableStateOf(skipStations) }

        ModalBottomSheet(
            onDismissRequest = { showSkipSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Durulmayacak İstasyonlar", style = MaterialTheme.typography.titleMedium)

                // Top bar: Tümünü Seç / Kaldır
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            tempSelection = intermediateStations.map { it }.toSet()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Tümünü Seç") }

                    OutlinedButton(
                        onClick = { tempSelection = emptySet() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Tümünü Kaldır") }
                }

                // Chip listesi (soldan sağa, satır kırmalı)
                if (intermediateStations.isEmpty()) {
                    Text("Ara istasyon yok.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        intermediateStations.forEach { st ->
                            val selected = st in tempSelection
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    tempSelection = if (selected) tempSelection - st else tempSelection + st
                                    // recompose için set et
                                    //tempSelection = tempSelection.toMutableSet()
                                },
                                label = { Text(st.name) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Kaydet butonu
                Button(
                    onClick = {
                        skipStations = tempSelection
                        showSkipSheet = false
                        homeViewModel.selectSkippedStations(skipStations)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Kaydet")
                }

                // İptal butonu
                Button(
                    onClick = {
                        showSkipSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("İptal")
                }

                // Sheet'in alt safe alanı için boşluk
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
