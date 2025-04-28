package fitech.tutorials.rsmgraphlast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.GridLines
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import kotlin.math.roundToInt

@Composable
fun SpeedChart(
    speedPoints: List<Point>,
    currentSpeed: Float = 0f,
    modifier: Modifier = Modifier
) {
    // Track chart width
    val chartWidthPx = remember { mutableStateOf(0f) }

    // Calculate axis step size based on actual width
    val steps = 28 // Fixed number of steps
    val axisStepSize = with(LocalDensity.current) {
        (chartWidthPx.value / steps).toDp()
    }

    // Static configuration that should be composed immediately
    val staticConfig = remember(chartWidthPx.value) {
        object {
            val xAxisData = AxisData.Builder()
                .axisStepSize(axisStepSize)
                .steps(steps)
                .labelData { value ->
                    val position = (value * 500).toInt()
                    if (position % 1000 == 0) {
                        position.toString()
                    } else ""
                }
                .labelAndAxisLinePadding(5.dp)
                .axisLabelAngle(90f)
                .axisLineColor(Color.Black)
                .axisLabelColor(Color.Black)
                .axisLabelFontSize(15.sp)
                .axisOffset(5.dp)
                .shouldDrawAxisLineTillEnd(true)
                .build()

            val yAxisData = AxisData.Builder()
                .axisStepSize(30.dp)
                .steps(12)
                .labelAndAxisLinePadding(20.dp)
                .labelData { value -> (value * 10).toInt().toString() }
                .axisLineColor(Color.Black)
                .axisLabelColor(Color.Black)
                .axisLabelFontSize(15.sp)
                .shouldDrawAxisLineTillEnd(true)
                .topPadding(1.dp)
                .build()

            val gridLines = GridLines(
                color = Color.Black.copy(alpha = 0.3f),
                enableHorizontalLines = true,
                enableVerticalLines = true,
                lineWidth = 1.dp
            )

            // Initialize with empty points to show grid structure
            val initialPoints = List(29) { index ->
                Point(index * 0.5f, 0f)
            }
        }
    }

    // Data points handling
    val currentPoints = if (speedPoints.isEmpty()) {
        staticConfig.initialPoints
    } else {
        speedPoints.filter { it.x <= 14f }
    }

    // Chart data configuration
    val lineChartData = LineChartData(
        linePlotData = LinePlotData(
            lines = listOf(
                // Pseudo line for grid structure
                Line(
                    dataPoints = List(29) { index ->
                        Point(index * 0.5f, 120f)  // Points at 0, 0.5, 1.0, ... 14.0 km, at max y-axis value
                    },
                    lineStyle = LineStyle(color = Color.Transparent, width = 0f)
                ),
                // Actual data line
                Line(
                    dataPoints = currentPoints,
                    lineStyle = LineStyle(color = Color.Black, width = 2.5f)
                )
            )
        ),
        bottomPadding = 25.dp,
        paddingTop = 8.dp,
        xAxisData = staticConfig.xAxisData,
        yAxisData = staticConfig.yAxisData,
        gridLines = staticConfig.gridLines,
        backgroundColor = Color.White,
        paddingRight = 20.dp,
        isZoomAllowed = true
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 5.dp, top = 1.dp, bottom = 0.dp, end = 5.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Legend at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem("Gradients", Color.Green)
                LegendItem("Speed Limit", Color.Red)
                LegendItem("Reference Curve", Color.Blue)
                LegendItem("Train Speed", Color.Black)
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

            // Chart taking most of the space
            LineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { layoutCoordinates ->
                        chartWidthPx.value = layoutCoordinates.size.width.toFloat()
                    },
                lineChartData = lineChartData
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

        // Speed Indicator Circle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 2.dp)
                .size(30.dp)
                .background(Color.White, CircleShape)
                .border(1.dp, Color.Black, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentSpeed.roundToInt().toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    color: Color,
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
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
