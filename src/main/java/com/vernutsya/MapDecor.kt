package com.vernutsya

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

fun metersPerPixel(lat: Double, zoom: Double): Double {
    if (lat.isNaN() || zoom.isNaN()) return 0.0
    val v = 156543.03392 * cos(Math.toRadians(lat.coerceIn(-85.0, 85.0))) / 2.0.pow(zoom.coerceIn(0.0, 22.0))
    return if (v.isNaN() || v.isInfinite() || v <= 0.0) 0.0 else v
}

@Composable
fun ScaleBar(mpp: Double, modifier: Modifier = Modifier) {
    if (mpp <= 0.0 || mpp.isNaN() || mpp.isInfinite()) return
    val density = LocalDensity.current
    val targetM = mpp * 90f
    if (targetM <= 0.0 || targetM.isNaN()) return
    val pow = 10.0.pow(floor(log10(targetM)))
    if (pow <= 0.0 || pow.isNaN()) return
    val nice = when { targetM / pow >= 5 -> 5 * pow; targetM / pow >= 2 -> 2 * pow; else -> pow }
    val barPx = (nice / mpp).toFloat()
    if (barPx.isNaN() || barPx <= 0f || barPx > 400f) return
    val barDp = with(density) { barPx.toDp() }
    val label = if (nice >= 1000) "${(nice / 1000).roundToInt()} км" else "${nice.roundToInt()} м"
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        Canvas(Modifier.width(barDp).height(7.dp)) {
            val y = size.height - 1.dp.toPx()
            drawLine(Color.Black, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            drawLine(Color.Black, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
            drawLine(Color.Black, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
fun NorthArrow(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("С", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Canvas(Modifier.size(22.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawPath(Path().apply {
                moveTo(c.x, 0f)
                lineTo(c.x - size.width * 0.32f, size.height)
                lineTo(c.x, size.height * 0.72f)
                lineTo(c.x + size.width * 0.32f, size.height)
                close()
            }, Color(0xFFD32F2F))
        }
    }
}

@Composable
fun MapDecorations(mpp: Double, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),
            color = Color.White.copy(alpha = 0.88f), shape = RoundedCornerShape(8.dp)) {
            NorthArrow(Modifier.padding(6.dp))
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
            color = Color.White.copy(alpha = 0.88f), shape = RoundedCornerShape(8.dp)) {
            ScaleBar(mpp, Modifier.padding(6.dp))
        }
    }
}