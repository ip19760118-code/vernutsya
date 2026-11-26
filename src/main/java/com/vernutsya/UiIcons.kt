package com.vernutsya

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF263238)

@Composable fun GearIcon(modifier: Modifier = Modifier.size(22.dp), color: Color = Ink) { Canvas(modifier) { drawGear(color) } }
@Composable fun ShareIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawShareIcon(color) } }
@Composable fun LayersIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawLayersIcon(color) } }
@Composable fun CrosshairIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawCrosshairIcon(color) } }
@Composable fun DiagonalArrowsIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawDiagonalArrowsIcon(color) } }
@Composable fun BackIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawBackIcon(color) } }
@Composable fun DownloadIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawDownloadIcon(color) } }
@Composable fun FolderIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawFolderIcon(color) } }
@Composable fun MapIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawMapIcon(color) } }
@Composable fun BookIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawBookIcon(color) } }
@Composable fun CameraIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawCameraIcon(color) } }
@Composable fun PinIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawPinIcon(color) } }
@Composable fun EditIcon(modifier: Modifier = Modifier.size(16.dp), color: Color = Ink) { Canvas(modifier) { drawEditIcon(color) } }
@Composable fun PlusIcon(modifier: Modifier = Modifier.size(20.dp), color: Color = Ink) { Canvas(modifier) { drawPlusIcon(color) } }

fun DrawScope.drawGear(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f); val r = size.minDimension / 2f
    for (i in 0 until 8) {
        val a = Math.toRadians((i * 45).toDouble())
        drawLine(color,
            Offset(c.x + (r * 0.60f * cos(a)).toFloat(), c.y + (r * 0.60f * sin(a)).toFloat()),
            Offset(c.x + (r * 0.95f * cos(a)).toFloat(), c.y + (r * 0.95f * sin(a)).toFloat()),
            strokeWidth = r * 0.28f, cap = StrokeCap.Round)
    }
    drawCircle(color, r * 0.56f, c, style = Stroke(r * 0.20f))
    drawCircle(color, r * 0.22f, c, style = Stroke(r * 0.16f))
}

fun DrawScope.drawShareIcon(color: Color) {
    val w = size.width; val h = size.height; val m = size.minDimension
    val a = Offset(w * 0.26f, h * 0.74f); val b = Offset(w * 0.74f, h * 0.22f); val c = Offset(w * 0.74f, h * 0.78f)
    drawLine(color, a, b, strokeWidth = m * 0.10f, cap = StrokeCap.Round)
    drawLine(color, a, c, strokeWidth = m * 0.10f, cap = StrokeCap.Round)
    val ring = Stroke(width = m * 0.11f)
    drawCircle(color, m * 0.15f, a, style = ring)
    drawCircle(color, m * 0.15f, b, style = ring)
    drawCircle(color, m * 0.15f, c, style = ring)
}

fun DrawScope.drawLayersIcon(color: Color) {
    val w = size.width; val h = size.height; val m = size.minDimension
    val st = Stroke(m * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.16f); lineTo(w * 0.86f, h * 0.40f); lineTo(w * 0.5f, h * 0.64f); lineTo(w * 0.14f, h * 0.40f); close()
    }, color, style = st)
    drawPath(Path().apply { moveTo(w * 0.14f, h * 0.58f); lineTo(w * 0.5f, h * 0.82f); lineTo(w * 0.86f, h * 0.58f) }, color, style = st)
}

fun DrawScope.drawCrosshairIcon(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f); val m = size.minDimension; val sw = m * 0.09f
    drawCircle(color, m * 0.30f, c, style = Stroke(sw))
    drawLine(color, Offset(c.x, c.y - m * 0.46f), Offset(c.x, c.y - m * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y + m * 0.34f), Offset(c.x, c.y + m * 0.46f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x - m * 0.46f, c.y), Offset(c.x - m * 0.34f, c.y), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x + m * 0.34f, c.y), Offset(c.x + m * 0.46f, c.y), strokeWidth = sw, cap = StrokeCap.Round)
    drawCircle(color, m * 0.07f, c)
}

fun DrawScope.drawDiagonalArrowsIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.10f
    val s = Offset(w * 0.22f, h * 0.78f); val e = Offset(w * 0.78f, h * 0.22f)
    drawLine(color, s, e, strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, e, Offset(w * 0.56f, h * 0.22f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, e, Offset(w * 0.78f, h * 0.44f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, s, Offset(w * 0.44f, h * 0.78f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, s, Offset(w * 0.22f, h * 0.56f), strokeWidth = sw, cap = StrokeCap.Round)
}

fun DrawScope.drawBackIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.10f
    drawLine(color, Offset(w * 0.80f, h * 0.5f), Offset(w * 0.24f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.46f, h * 0.26f), Offset(w * 0.24f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.24f, h * 0.5f), Offset(w * 0.46f, h * 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
}

fun DrawScope.drawDownloadIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.10f
    drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.60f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.30f, h * 0.42f), Offset(w * 0.5f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.70f, h * 0.42f), Offset(w * 0.5f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.18f, h * 0.64f), Offset(w * 0.18f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.82f, h * 0.64f), Offset(w * 0.82f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.82f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
}

fun DrawScope.drawFolderIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.09f
    drawPath(Path().apply {
        moveTo(w * 0.12f, h * 0.78f); lineTo(w * 0.12f, h * 0.24f); lineTo(w * 0.42f, h * 0.24f)
        lineTo(w * 0.52f, h * 0.36f); lineTo(w * 0.88f, h * 0.36f); lineTo(w * 0.88f, h * 0.78f); close()
    }, color, style = Stroke(sw, join = StrokeJoin.Round))
}

fun DrawScope.drawMapIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.09f
    val st = Stroke(sw, join = StrokeJoin.Round, cap = StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(w * 0.12f, h * 0.26f); lineTo(w * 0.37f, h * 0.16f); lineTo(w * 0.63f, h * 0.26f)
        lineTo(w * 0.88f, h * 0.16f); lineTo(w * 0.88f, h * 0.74f); lineTo(w * 0.63f, h * 0.84f)
        lineTo(w * 0.37f, h * 0.74f); lineTo(w * 0.12f, h * 0.84f); close()
    }, color, style = st)
    drawLine(color, Offset(w * 0.37f, h * 0.16f), Offset(w * 0.37f, h * 0.74f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.63f, h * 0.26f), Offset(w * 0.63f, h * 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
}

fun DrawScope.drawBookIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.09f
    val st = Stroke(sw, join = StrokeJoin.Round, cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(w * 0.5f, h * 0.26f); lineTo(w * 0.5f, h * 0.82f) }, color, style = st)
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.26f); lineTo(w * 0.14f, h * 0.18f); lineTo(w * 0.14f, h * 0.74f); lineTo(w * 0.5f, h * 0.82f)
    }, color, style = st)
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.26f); lineTo(w * 0.86f, h * 0.18f); lineTo(w * 0.86f, h * 0.74f); lineTo(w * 0.5f, h * 0.82f)
    }, color, style = st)
}

fun DrawScope.drawCameraIcon(color: Color) {
    val w = size.width; val h = size.height; val m = size.minDimension; val sw = m * 0.09f
    drawRoundRect(color, topLeft = Offset(w * 0.10f, h * 0.30f), size = Size(w * 0.80f, h * 0.52f),
        cornerRadius = CornerRadius(m * 0.10f), style = Stroke(sw))
    drawPath(Path().apply {
        moveTo(w * 0.34f, h * 0.30f); lineTo(w * 0.40f, h * 0.18f); lineTo(w * 0.60f, h * 0.18f); lineTo(w * 0.66f, h * 0.30f)
    }, color, style = Stroke(sw, join = StrokeJoin.Round))
    drawCircle(color, m * 0.16f, Offset(w * 0.5f, h * 0.56f), style = Stroke(sw))
}

fun DrawScope.drawPinIcon(color: Color) {
    val w = size.width; val h = size.height; val m = size.minDimension; val sw = m * 0.09f
    drawPath(Path().apply {
        moveTo(w * 0.5f, h * 0.88f)
        quadraticBezierTo(w * 0.16f, h * 0.52f, w * 0.20f, h * 0.36f)
        quadraticBezierTo(w * 0.24f, h * 0.12f, w * 0.5f, h * 0.12f)
        quadraticBezierTo(w * 0.76f, h * 0.12f, w * 0.80f, h * 0.36f)
        quadraticBezierTo(w * 0.84f, h * 0.52f, w * 0.5f, h * 0.88f)
        close()
    }, color, style = Stroke(sw, join = StrokeJoin.Round))
    drawCircle(color, m * 0.11f, Offset(w * 0.5f, h * 0.36f))
}

fun DrawScope.drawEditIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.11f
    drawLine(color, Offset(w * 0.20f, h * 0.80f), Offset(w * 0.72f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.82f, h * 0.38f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.16f, h * 0.86f), Offset(w * 0.30f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
}

fun DrawScope.drawPlusIcon(color: Color) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.12f
    drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
}