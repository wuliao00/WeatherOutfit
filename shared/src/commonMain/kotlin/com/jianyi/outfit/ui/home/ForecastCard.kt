package com.jianyi.outfit.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianyi.outfit.data.model.ForecastDay
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.ui.theme.HighTempOrange
import com.jianyi.outfit.ui.theme.LowTempBlue
import com.jianyi.outfit.ui.theme.MotionSpecs
import com.jianyi.outfit.util.Formatters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 七日预报卡：温度曲线 + 逐日高低温。
 *
 * 曲线用 Canvas 手绘而不是引入图表库：只有 7 个点，多一个依赖不如多 60 行代码。
 * 首次出现时按 700ms 从左往右「画」出来（clipRect 推进），
 * 比整块淡入更能让人意识到「这是一条趋势」。
 */
@Composable
fun ForecastCard(
    days: List<ForecastDay>,
    note: String?,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier,
    dark: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "未来 7 天",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "接口按日提供",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        val usable = days.filter { it.highTemp != null && it.lowTemp != null }
        if (usable.size < 2) {
            Text(
                text = note ?: "暂无逐日数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            TempCurve(
                days = usable,
                tempUnit = tempUnit,
                dark = dark,
                modifier = Modifier.fillMaxWidth().height(96.dp)
            )
            Spacer(Modifier.height(6.dp))
            DayLabels(days = usable, tempUnit = tempUnit)
            if (note != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 温度曲线本体 */
@Composable
private fun TempCurve(
    days: List<ForecastDay>,
    tempUnit: TempUnit,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(days.size) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(MotionSpecs.FADE_MS * 2 + 180, easing = MotionSpecs.EaseOut))
    }

    val highs = days.map { it.highTemp!!.toDouble() }
    val lows = days.map { it.lowTemp!!.toDouble() }
    val maxT = (highs.max() + 1).toFloat()
    val minT = (lows.min() - 1).toFloat()
    val span = (maxT - minT).coerceAtLeast(1f)

    val lineHigh = if (dark) HighTempOrange.copy(alpha = 0.95f) else HighTempOrange
    val lineLow = if (dark) LowTempBlue.copy(alpha = 0.95f) else LowTempBlue

    Canvas(modifier) {
        val stepX = if (days.size > 1) size.width / (days.size - 1) else size.width
        // 上下各留 16dp 给温度文字，避免贴边被裁
        val topPad = 20.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val usableH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun point(i: Int, temp: Double): Offset {
            val ratio = ((maxT - temp.toFloat()) / span).coerceIn(0f, 1f)
            return Offset(i * stepX, topPad + ratio * usableH)
        }

        val highPoints = highs.mapIndexed { i, t -> point(i, t) }
        val lowPoints = lows.mapIndexed { i, t -> point(i, t) }
        val highPath = smoothPath(highPoints)
        val lowPath = smoothPath(lowPoints)

        // 两条线之间填一层极淡的渐变，读起来像「温度带」而不是两根孤立的线
        val band = bandPath(highPoints, lowPoints)
        clipRect(right = size.width * reveal.value) {
            drawPath(
                path = band,
                brush = Brush.verticalGradient(
                    listOf(lineHigh.copy(alpha = 0.22f), lineLow.copy(alpha = 0.10f))
                )
            )
            drawPath(
                path = highPath,
                color = lineHigh,
                style = Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            drawPath(
                path = lowPath,
                color = lineLow,
                style = Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            highPoints.forEach { drawCircle(lineHigh, radius = 2.6.dp.toPx(), center = it) }
            lowPoints.forEach { drawLowDot(it, lineLow) }
        }
    }
}

/** 低温度数点：小圆点带一圈同色半透明外环，密集时也不会糊成一片 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLowDot(
    center: Offset,
    color: Color
) {
    drawCircle(color.copy(alpha = 0.28f), radius = 5.2.dp.toPx(), center = center)
    drawCircle(color, radius = 2.6.dp.toPx(), center = center)
}

/** 逐日标签：星期 + 高低温 */
@Composable
private fun DayLabels(days: List<ForecastDay>, tempUnit: TempUnit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEachIndexed { index, day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Formatters.forecastDayLabel(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${convert(day.highTemp!!, tempUnit)}° / ${convert(day.lowTemp!!, tempUnit)}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun convert(celsius: Int, unit: TempUnit): Int = when (unit) {
    TempUnit.CELSIUS -> celsius
    // 先按浮点算再取整：整数版 celsius * 9 / 5 会先截断，0℃ 会变成 31℉
    TempUnit.FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt()
}

/** 相邻两点间塞一段三次贝塞尔：控制点水平偏移 40% */
private fun appendSmoothSegment(path: Path, start: Offset, end: Offset) {
    val dx = abs(end.x - start.x) * 0.4f
    path.cubicTo(start.x + dx, start.y, end.x - dx, end.y, end.x, end.y)
}

/**
 * 把离散点连成平滑曲线。
 *
 * 不用折线，是因为折线在高低点处会「起棱」，看起来像数据出错；
 * 也不用完整样条，7 个点的情况下样条容易冲出上下边界。
 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) appendSmoothSegment(path, points[i], points[i + 1])
    return path
}

/** 高温曲线 + 反向走的低温曲线围成的闭合区域 */
private fun bandPath(high: List<Offset>, low: List<Offset>): Path {
    val path = Path()
    if (high.size < 2 || low.size < 2) return path
    path.moveTo(high.first().x, high.first().y)
    for (i in 0 until high.size - 1) appendSmoothSegment(path, high[i], high[i + 1])
    val back = low.reversed()
    path.lineTo(back.first().x, back.first().y)
    for (i in 0 until back.size - 1) appendSmoothSegment(path, back[i], back[i + 1])
    path.close()
    return path
}
