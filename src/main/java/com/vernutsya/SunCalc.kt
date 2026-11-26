package com.vernutsya

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

object SunCalc {
    private const val DAY_MS = 86400000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val RAD = Math.PI / 180.0
    private const val E = RAD * 23.4397
    private const val J0 = 0.0009

    private fun toJulian(ms: Long) = ms / DAY_MS - 0.5 + J1970
    private fun fromJulian(j: Double) = ((j + 0.5 - J1970) * DAY_MS).toLong()
    private fun toDays(ms: Long) = toJulian(ms) - J2000
    private fun solarMeanAnomaly(d: Double) = RAD * (357.5291 + 0.98560028 * d)
    private fun eclipticLongitude(m: Double): Double {
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        return m + c + RAD * 102.9372 + Math.PI
    }
    private fun declination(l: Double) = asin(sin(E) * sin(l))
    private fun julianCycle(d: Double, lw: Double) = Math.round(d - J0 - lw / (2 * Math.PI)).toDouble()
    private fun approxTransit(ht: Double, lw: Double, n: Double) = J0 + (ht + lw) / (2 * Math.PI) + n
    private fun solarTransitJ(ds: Double, m: Double, l: Double) = J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
    private fun hourAngle(h: Double, phi: Double, d: Double): Double {
        val x = (sin(h) - sin(phi) * sin(d)) / (cos(phi) * cos(d))
        return if (x < -1 || x > 1) Double.NaN else acos(x)
    }

    fun sunset(lat: Double, lon: Double, dateMs: Long): Long? {
        val lw = RAD * -lon; val phi = RAD * lat; val d = toDays(dateMs)
        val n = julianCycle(d, lw); val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds); val l = eclipticLongitude(m); val dec = declination(l)
        val ha = hourAngle(RAD * -0.833, phi, dec)
        if (ha.isNaN()) return null
        return fromJulian(solarTransitJ(approxTransit(ha, lw, n), m, l))
    }

    fun sunrise(lat: Double, lon: Double, dateMs: Long): Long? {
        val lw = RAD * -lon; val phi = RAD * lat; val d = toDays(dateMs)
        val n = julianCycle(d, lw); val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds); val l = eclipticLongitude(m); val dec = declination(l)
        val ha = hourAngle(RAD * -0.833, phi, dec)
        if (ha.isNaN()) return null
        val jset = solarTransitJ(approxTransit(ha, lw, n), m, l)
        val jnoon = solarTransitJ(ds, m, l)
        return fromJulian(jnoon - (jset - jnoon))
    }
}

private fun shortDur(ms: Long): String {
    val h = ms / 3600000; val m = (ms % 3600000) / 60000
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

@Composable
fun DaylightCard(state: TrackerState, now: Long) {
    val loc = state.location
    val minuteKey = now / 60000
    val sunset = remember(loc?.latitude, loc?.longitude, minuteKey) {
        loc?.let { SunCalc.sunset(it.latitude, it.longitude, now) }
    }
    val sunrise = remember(loc?.latitude, loc?.longitude, minuteKey) {
        loc?.let { SunCalc.sunrise(it.latitude, it.longitude, now) }
    }
    val remaining = sunset?.minus(now)
    val main = when {
        loc == null -> "До заката: — (нет GPS)"
        remaining == null -> "Полярный день / ночь"
        remaining <= 0 -> "Солнце уже село"
        else -> "До заката: ${shortDur(remaining)}"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌇", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(main, fontWeight = FontWeight.Bold)
                if (sunrise != null && sunset != null && sunset > sunrise)
                    Text("Световой день: ${shortDur(sunset - sunrise)}", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}