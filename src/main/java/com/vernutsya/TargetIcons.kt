package com.vernutsya

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawTargetBadge(icon: TargetIcon, center: Offset, radius: Float, accent: Color) {
    drawCircle(Color.White, radius, center)
    drawCircle(accent, radius, center, style = Stroke(width = radius * 0.12f))
    val h = radius * 0.62f
    val c = center
    val dark = Color(0xFF263238)

    when (icon) {
        TargetIcon.HOUSE -> {
            drawRect(accent, topLeft = Offset(c.x - h * 0.72f, c.y - h * 0.05f),
                size = Size(h * 1.44f, h * 0.95f))
            drawPath(Path().apply {
                moveTo(c.x, c.y - h)
                lineTo(c.x - h, c.y - h * 0.05f)
                lineTo(c.x + h, c.y - h * 0.05f)
                close()
            }, accent)
            drawRect(Color.White, topLeft = Offset(c.x - h * 0.16f, c.y + h * 0.35f),
                size = Size(h * 0.32f, h * 0.55f))
        }
        TargetIcon.CAR -> {
            drawPath(Path().apply {
                moveTo(c.x - h * 0.55f, c.y - h * 0.05f)
                lineTo(c.x - h * 0.3f, c.y - h * 0.55f)
                lineTo(c.x + h * 0.3f, c.y - h * 0.55f)
                lineTo(c.x + h * 0.6f, c.y - h * 0.05f)
                close()
            }, accent)
            drawRoundRect(accent, topLeft = Offset(c.x - h, c.y - h * 0.05f),
                size = Size(h * 2f, h * 0.6f), cornerRadius = CornerRadius(h * 0.18f))
            drawCircle(dark, h * 0.2f, Offset(c.x - h * 0.5f, c.y + h * 0.55f))
            drawCircle(dark, h * 0.2f, Offset(c.x + h * 0.5f, c.y + h * 0.55f))
        }
        TargetIcon.TENT -> {
            drawPath(Path().apply {
                moveTo(c.x, c.y - h * 0.9f)
                lineTo(c.x - h, c.y + h * 0.75f)
                lineTo(c.x + h, c.y + h * 0.75f)
                close()
            }, accent)
            drawPath(Path().apply {
                moveTo(c.x, c.y - h * 0.1f)
                lineTo(c.x - h * 0.3f, c.y + h * 0.75f)
                lineTo(c.x + h * 0.3f, c.y + h * 0.75f)
                close()
            }, Color.White)
        }
        TargetIcon.MUSHROOM -> {
            drawRoundRect(Color(0xFFF5E6C8), topLeft = Offset(c.x - h * 0.22f, c.y - h * 0.05f),
                size = Size(h * 0.44f, h * 0.9f), cornerRadius = CornerRadius(h * 0.2f))
            drawPath(Path().apply {
                moveTo(c.x - h, c.y)
                quadraticBezierTo(c.x - h * 0.7f, c.y - h, c.x, c.y - h)
                quadraticBezierTo(c.x + h * 0.7f, c.y - h, c.x + h, c.y)
                close()
            }, Color(0xFFE53935))
            drawCircle(Color.White, h * 0.12f, Offset(c.x - h * 0.4f, c.y - h * 0.45f))
            drawCircle(Color.White, h * 0.1f, Offset(c.x + h * 0.3f, c.y - h * 0.55f))
        }
        TargetIcon.FLAG -> {
            drawLine(dark, Offset(c.x - h * 0.45f, c.y - h * 0.9f),
                Offset(c.x - h * 0.45f, c.y + h * 0.9f), strokeWidth = h * 0.16f)
            drawPath(Path().apply {
                moveTo(c.x - h * 0.45f, c.y - h * 0.9f)
                lineTo(c.x + h * 0.85f, c.y - h * 0.5f)
                lineTo(c.x - h * 0.45f, c.y - h * 0.1f)
                close()
            }, accent)
        }
        TargetIcon.STAR -> {
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) h else h * 0.45f
                val a = Math.toRadians((-90.0 + i * 36.0))
                val x = c.x + (r * cos(a)).toFloat()
                val y = c.y + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color(0xFFFFC107))
        }
    }
}