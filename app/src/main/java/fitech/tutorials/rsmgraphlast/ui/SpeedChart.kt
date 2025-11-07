package fitech.tutorials.rsmgraphlast.ui

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import fitech.tutorials.rsmgraphlast.R

@Composable
fun SpeedChart(
    speedPoints: List<Entry>,
    speedLimits: List<Entry>,
    initialStationBerthing: Float,
    finalStationBerthing: Float,
    modifier: Modifier = Modifier
)
{
    var autoCenter by remember { mutableStateOf(true) }
    var showRecenter by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize())
    {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Legend at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem("DASProfile", ComposeColor.Green)
                LegendItem("Speed Limit", ComposeColor.Red)
                LegendItem("Train Speed", ComposeColor.Black)
            }

            // Speed label for left y-axis
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = "Speed (km/h)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                )
            }

            // Chart
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        legend.isEnabled = false // Disable built-in legend since we have custom one
                        setTouchEnabled(true)
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        setDrawGridBackground(false)
                        // Configure X axis
                        xAxis.apply {
                            position = XAxis.XAxisPosition.BOTTOM
                            setDrawGridLines(true)
                            granularity = 10f
                            labelRotationAngle = 0f
                            isGranularityEnabled = false
                            axisMaximum = finalStationBerthing    //max(initialStationBerthing, finalStationBerthing).toFloat()
                            axisMinimum = initialStationBerthing  //min(initialStationBerthing, finalStationBerthing).toFloat()
                            setLabelCount(14)
                            gridColor = Color.LTGRAY
                            axisLineColor = Color.BLACK
                            textColor = Color.BLACK
                        }

                        // Configure Y axis
                        axisLeft.apply {
                            setDrawGridLines(true)
                            axisMinimum = 0f
                            axisMaximum = 120f
                            granularity = 20f
                            isGranularityEnabled = false
                            setLabelCount(7)
                            setDrawGridLines(true)
                            gridColor = Color.LTGRAY
                            axisLineColor = Color.BLACK
                            textColor = Color.BLACK
                        }
                        isAutoScaleMinMaxEnabled = false
                        axisRight.isEnabled = false

                        onChartGestureListener =
                            object : com.github.mikephil.charting.listener.OnChartGestureListener {
                                override fun onChartGestureStart(
                                    me: android.view.MotionEvent?,
                                    lastPerformedGesture: com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture?
                                ) {
                                }

                                override fun onChartGestureEnd(
                                    me: android.view.MotionEvent?,
                                    lastPerformedGesture: com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture?
                                ) {
                                }

                                override fun onChartLongPressed(me: android.view.MotionEvent?) {}

                                override fun onChartDoubleTapped(me: android.view.MotionEvent?) {
                                    autoCenter = false
                                    showRecenter = true
                                }

                                override fun onChartSingleTapped(me: android.view.MotionEvent?) {}

                                override fun onChartFling(
                                    me1: android.view.MotionEvent?,
                                    me2: android.view.MotionEvent?,
                                    velocityX: Float, velocityY: Float
                                ) {
                                }

                                override fun onChartScale(
                                    me: android.view.MotionEvent?, scaleX: Float, scaleY: Float
                                ) {
                                    autoCenter = false
                                    showRecenter = true
                                }

                                override fun onChartTranslate(
                                    me: android.view.MotionEvent?, dX: Float, dY: Float
                                ) {
                                    autoCenter = false
                                    showRecenter = true
                                }
                            }
                    }
                },
                update = { chart ->
                    val velocityDataSet = LineDataSet(speedPoints, "Speed").apply {
                        color = Color.BLACK
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }

                    val speedLimitDataSet = LineDataSet(speedLimits, "Speed Limit").apply {
                        color = Color.RED
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.LINEAR
                    }

                    chart.data = LineData(velocityDataSet, speedLimitDataSet)

                    // === Oto-merkezleme (yalnızca X ekseni) ===
                    val lastX = speedPoints.lastOrNull()?.x
                    if (autoCenter && lastX != null) {
                        // 1) Tüm zoom/pan'ı sıfırla (X+Y)
                        chart.fitScreen()

                        // 2) Y eksenini başlangıç ölçeğine getir (sabit kullanıyorsan)
                        chart.axisLeft.axisMinimum = 0f
                        chart.axisLeft.axisMaximum = 120f

                        // 3) X’te 800 m pencereyi yeniden kur
                        val windowMin = lastX - 400f
                        val windowMax = lastX + 400f
                        chart.xAxis.axisMinimum = windowMin
                        chart.xAxis.axisMaximum = windowMax
                    }

                    chart.invalidate()
                }
            )

            // Position and Gradient labels at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 3.dp, bottom = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Position (m)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Text(
                    text = "Gradient (%)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )

            }
        }
        if (showRecenter) {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = {
                    autoCenter = true
                    showRecenter = false
                },
                icon = {
                    androidx.compose.material3.Icon(painter = painterResource(R.drawable.center_icon), contentDescription = "Center")
                },
                text = {Text("Ortala") },
                containerColor = ComposeColor(0xDD2A2B2E),
                contentColor = ComposeColor.White,           // ikon + yazı rengi
                modifier = Modifier
                    .align(Alignment.BottomEnd)              // BoxScope içindeyiz
                    .padding(16.dp),
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp
                )
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: ComposeColor,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp, 1.dp)
                .background(color, RectangleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
