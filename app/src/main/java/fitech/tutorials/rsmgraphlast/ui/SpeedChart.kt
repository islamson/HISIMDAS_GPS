package fitech.tutorials.rsmgraphlast.ui

import android.graphics.Color
import android.util.Log
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
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import fitech.tutorials.rsmgraphlast.R
import kotlin.math.max
import kotlin.math.min

@Composable
fun SpeedChart(
    speedPoints: List<Entry>,
    speedLimits: List<Entry>,
    tracklineStart: Float,
    tracklineEnd: Float,
    initialBerthing: Float,
    finalBerthing: Float,
    dasProfile: List<Entry>,
    coastingBands: List<Pair<Float, Float>>,
    direction: String,
    modifier: Modifier = Modifier
)
{
    var autoCenter by remember { mutableStateOf(true) }
    var showRecenter by remember { mutableStateOf(false) }
    var yMax by remember(initialBerthing, finalBerthing) { mutableStateOf<Float?>(null) }

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
                LegendItem("DASProfile", ComposeColor.Blue)
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
                            axisMaximum = if(direction == "West to East") max(initialBerthing, finalBerthing) + 200 else -min(initialBerthing, finalBerthing) + tracklineStart + tracklineEnd + 200
                            axisMinimum = if(direction == "West to East") min(initialBerthing, finalBerthing) - 200 else -max(initialBerthing, finalBerthing) + tracklineStart + tracklineEnd - 200
                            setLabelCount(14)
                            gridColor = Color.LTGRAY
                            axisLineColor = Color.BLACK
                            textColor = Color.BLACK
                        }

                        // Configure Y axis
                        axisLeft.apply {
                            setDrawGridLines(true)
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
                    val absLow  = minOf(initialBerthing, finalBerthing)
                    val absHigh = maxOf(initialBerthing, finalBerthing)

                    val sortedLimits = speedLimits.sortedBy { it.x }

                    fun stepLimitAt(x: Float): Float {
                        if (sortedLimits.isEmpty()) return 0f
                        var last = sortedLimits.first().y
                        for (e in sortedLimits) {
                            if (e.x <= x) last = e.y else break
                        }
                        return last
                    }

                    val candidates = mutableListOf<Float>()
                    candidates += stepLimitAt(absLow)
                    for (e in sortedLimits) {
                        if (e.x in absLow..absHigh) candidates += e.y
                    }

                    val maxLimit = candidates.maxOrNull() ?: 150f

                    if(yMax == null)
                        yMax = (maxLimit * 1.10f)

                    fun ceilTo(step: Float, v: Float) = kotlin.math.ceil(v / step) * step
                    yMax = ceilTo(10f, if(yMax!! > (speedPoints.lastOrNull()?.y ?: 0f)) yMax!! else ((speedPoints.lastOrNull()?.y ?: 0f) * 1.10f))

                    val isE2W = direction == "East to West"
                    val xStart = minOf(tracklineStart, tracklineEnd)
                    val xEnd   = maxOf(tracklineStart, tracklineEnd)
                    println("Trackline start: ${tracklineStart}, tracklineEnd: ${tracklineEnd}")
                    Log.d("YMAX", "speedLimits size=${speedLimits.size} maxLimit=$maxLimit yMax=$yMax")

                    fun chartX(absX: Float): Float = if (!isE2W) absX else (xStart + xEnd - absX)

                    val segMinAbs = minOf(initialBerthing, finalBerthing) - 200f
                    val segMaxAbs = maxOf(initialBerthing, finalBerthing) + 200f

                    val segMinChart = minOf(chartX(segMinAbs), chartX(segMaxAbs))
                    val segMaxChart = maxOf(chartX(segMinAbs), chartX(segMaxAbs))

                    chart.xAxis.axisMinimum = segMinChart
                    chart.xAxis.axisMaximum = segMaxChart
                    chart.xAxis.setLabelCount(14, true)
                    chart.axisLeft.axisMaximum = yMax!!
                    chart.axisLeft.axisMinimum = 0f


                    fun mirrorX(x: Float): Float = xStart + xEnd - x
                    fun transform(list: List<Entry>): List<Entry> {
                        if (!isE2W) return list
                        return list.map { e -> Entry(mirrorX(e.x), e.y) }
                    }

                    val isDasDescending = dasProfile.size >= 2 && dasProfile.first().x > dasProfile.last().x

                    fun transformDasProfileIfNeeded(list: List<Entry>): List<Entry> {
                        if (!isE2W) return list
                        if (!isDasDescending) return list

                        return list
                            .map { e -> Entry(mirrorX(e.x), e.y) }
                            .sortedBy { it.x }
                    }

                    fun transformBandIfNeeded(band: Pair<Float, Float>): Pair<Float, Float> {
                        if (!isE2W) {
                            return minOf(band.first, band.second) to maxOf(band.first, band.second)
                        }

                        if (band.first > band.second) {
                            val mirroredStart = mirrorX(band.first)
                            val mirroredEnd = mirrorX(band.second)
                            return minOf(mirroredStart, mirroredEnd) to maxOf(mirroredStart, mirroredEnd)
                        }

                        else
                            return minOf(band.first, band.second) to maxOf(band.first, band.second)
                    }

                    val lineDataSets = mutableListOf<ILineDataSet>()

                    // Tüm coasting band'leri ekle
                    coastingBands.forEach { band ->
                        val chartBand = transformBandIfNeeded(band)
                        lineDataSets += buildCoastingBandDataSet(chartBand, yMax = yMax!!)
                    }

                    chart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                            return if (!isE2W) {
                                value.toInt().toString()
                            } else {
                                // value (artan) -> label (azalan)
                                val inverted = xStart + xEnd - value
                                inverted.toInt().toString()

                            }
                        }
                    }

                    val velocityDataSet = LineDataSet(transform(speedPoints), "Speed").apply {
                        color = Color.BLACK
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }

                    val speedLimitDataSet = LineDataSet(transform(speedLimits), "Speed Limit").apply {
                        color = Color.RED
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.LINEAR
                    }

                    // DASProfile (All-Out/Coasting profili)
                    val dasProfileForChart = transformDasProfileIfNeeded(dasProfile)

                    val dasDataSet = LineDataSet(dasProfileForChart, "DASProfile").apply {
                        color = Color.BLUE
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.LINEAR
                    }

                    Log.d(
                        "SpeedChart",
                        "direction=$direction isDasDescending=$isDasDescending firstX=${dasProfile.firstOrNull()?.x} lastX=${dasProfile.lastOrNull()?.x}"
                    )

                    lineDataSets.add(velocityDataSet)
                    lineDataSets.add(speedLimitDataSet)
                    lineDataSets.add(dasDataSet)

                    chart.data = LineData(lineDataSets)

                    // === Oto-merkezleme (yalnızca X ekseni) ===
                    val lastX = speedPoints.lastOrNull()?.x

                    if (autoCenter && lastX != null) {
                        // 1) Tüm zoom/pan'ı sıfırla (X+Y)
                        chart.fitScreen()
                        chart.axisLeft.axisMinimum = 0f
                        chart.axisLeft.axisMaximum = yMax!!
                        // 3) X’te 800 m pencereyi yeniden kur
                        //val windowMin = lastX - 400f
                        //val windowMax = lastX + 400f
                        //chart.xAxis.axisMinimum = windowMin
                        //chart.xAxis.axisMaximum = windowMax
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

/** Coasting band dataseti üretir (LineChart için).
 *  yMax: grafikteki üst hız sınırın (ör. 140 km/h)
 */
fun buildCoastingBandDataSet(
    band: Pair<Float, Float>,
    yMax: Float
): LineDataSet {
    val (x0, x1) = band
    val pts = listOf(
        Entry(x0, 0f),
        Entry(x0, yMax),
        Entry(x1, yMax),
        Entry(x1, 0f)
    )

    return LineDataSet(pts, "Coasting Region").apply {
        setDrawValues(false)
        setDrawCircles(false)
        mode = com.github.mikephil.charting.data.LineDataSet.Mode.LINEAR
        lineWidth = 0f                  // Hat görünmesin
        color = android.graphics.Color.TRANSPARENT
        setDrawFilled(true)
        fillAlpha = 50                  // yarı saydam
        fillColor = android.graphics.Color.GREEN
        // 0 tabanına doldurur; ekstra FillFormatter gerekmez.
    }
}




