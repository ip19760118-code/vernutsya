package com.vernutsya

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val ArrowGreen = Color(0xFF2E7D32)
private val ArrowRed = Color(0xFFE53935)
private val ArrowIdle = Color(0xFF90A4AE)

/**
 * Стрелка. Азимут читается только в фазе отрисовки — рекомпозиции экрана нет.
 * Азимут на цель «замораживается» на стоянке и вблизи цели, чтобы стрелка
 * не крутилась от дрожания GPS. Поворот телефона при этом учитывается всегда.
 */
@Composable
fun NavArrow(
    loc: Location?,
    targetLat: Double?,
    targetLon: Double?,
    arrived: Boolean,
    icon: TargetIcon,
    size: Dp = 300.dp,
    moving: Boolean = true,
    nearTarget: Boolean = false,
    overrideHeading: Float? = null
) {
    val heading = rememberHeading()
    val frozen = remember(targetLat, targetLon, overrideHeading != null) { floatArrayOf(Float.NaN) }

    var bearing: Float? = overrideHeading
    var gpsCourse: Float? = null
    if (bearing == null && loc != null && targetLat != null && targetLon != null) {
        val r = FloatArray(2)
        Location.distanceBetween(loc.latitude, loc.longitude, targetLat, targetLon, r)
        bearing = (r[1] + 360f) % 360f
    }
    if (loc != null && loc.hasBearing() && loc.speed > 1f) gpsCourse = loc.bearing

    ArrowCanvas(bearing, gpsCourse, arrived, icon, size, heading, frozen, moving, nearTarget)
}

@Composable
private fun ArrowCanvas(
    bearing: Float?,
    gpsCourse: Float?,
    arrived: Boolean,
    icon: TargetIcon,
    sizeDp: Dp,
    heading: HeadingState,
    frozen: FloatArray,
    moving: Boolean,
    nearTarget: Boolean
) {
    Canvas(Modifier.size(sizeDp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val badgeR = 22.dp.toPx()
        val borderR = size.minDimension / 2f - badgeR - 2.dp.toPx()
        val innerR = borderR - 18.dp.toPx()

        drawCircle(Color(0xFFDCEDC8), borderR, c)
        drawCircle(Color(0xFFE8F5E9), innerR, c)
        drawCircle(ArrowGreen, borderR, c, style = Stroke(4.dp.toPx()))

        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            drawLine(
                Color(0xFF81C784),
                Offset(c.x + ((innerR + 3.dp.toPx()) * sin(a)).toFloat(), c.y - ((innerR + 3.dp.toPx()) * cos(a)).toFloat()),
                Offset(c.x + ((borderR - 3.dp.toPx()) * sin(a)).toFloat(), c.y - ((borderR - 3.dp.toPx()) * cos(a)).toFloat()),
                strokeWidth = 2.dp.toPx()
            )
        }

        // --- заморозка азимута: стоя или у цели не обновляем ---
        if (bearing != null) {
            if (frozen[0].isNaN()) {
                frozen[0] = bearing
            } else if (moving && !nearTarget) {
                var diff = abs(bearing - frozen[0])
                if (diff > 180f) diff = 360f - diff
                if (diff >= 3f) frozen[0] = bearing
            }
        }
        val useBearing = if (!frozen[0].isNaN()) frozen[0] else bearing

        val az = heading.deg
        val hd = if (!az.isNaN()) az else gpsCourse
        val angle = if (useBearing != null && hd != null) useBearing - hd else null

        val color = when {
            angle == null -> ArrowIdle
            arrived -> ArrowGreen
            else -> ArrowRed
        }

        rotate(angle ?: 0f, c) {
            val path = Path().apply {
                moveTo(c.x, c.y - innerR * 0.72f)
                lineTo(c.x - innerR * 0.26f, c.y + innerR * 0.36f)
                lineTo(c.x, c.y + innerR * 0.16f)
                lineTo(c.x + innerR * 0.26f, c.y + innerR * 0.36f)
                close()
            }
            draw3DArrow(path, color)
        }

        if (angle != null) {
            val a = Math.toRadians(angle.toDouble())
            drawTargetBadge(
                icon,
                Offset(c.x + (borderR * sin(a)).toFloat(), c.y - (borderR * cos(a)).toFloat()),
                badgeR,
                if (arrived) ArrowGreen else ArrowRed
            )
        } else {
            drawCircle(ArrowIdle, 6.dp.toPx(), Offset(c.x, c.y - borderR))
        }
    }
}

private fun DrawScope.draw3DArrow(path: Path, base: Color) {
    val light = lerp(base, Color.White, 0.60f)
    val dark = lerp(base, Color.Black, 0.40f)
    val rim = lerp(base, Color.White, 0.80f)
    val depth = size.minDimension * 0.018f
    val b = path.getBounds()
    for (i in 4 downTo 1) drawPath(Path().apply { addPath(path, Offset(depth * 0.30f * i, depth * 0.45f * i)) }, Color.Black.copy(alpha = 0.07f))
    for (i in 4 downTo 1) drawPath(Path().apply { addPath(path, Offset(depth * 0.10f * i, depth * 0.16f * i)) }, dark)
    drawPath(path, Brush.linearGradient(listOf(light, base, dark), Offset(b.left, b.top), Offset(b.right, b.bottom)))
    drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
        Offset(b.left, b.top), Offset(b.left, b.top + b.height * 0.55f)))
    drawPath(path, rim, style = Stroke(depth * 0.14f))
}