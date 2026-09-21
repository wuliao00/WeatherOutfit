package com.jianyi.outfit.ui.scenery

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.jianyi.outfit.R

/**
 * 内置风景主题：每一套都对应一种天气/时段气质。
 *
 * sky / ground / accent 三个颜色是从对应图片上、中、下三段实测采样得到的
 * （见 tools/scenery_colors.py），不是手调的近似值——这样状态栏底色、
 * 玻璃描边色和背景不会出现「色差边」。
 */
enum class Scenery(
    val key: String,
    @param:DrawableRes val resId: Int,
    val label: String,
    val poetic: String,
    val sky: Color,
    val ground: Color,
    val accent: Color,
    /** 深色调风景：前景文字走亮色，遮罩方向也要反过来 */
    val dark: Boolean
) {
    DAWN_RIDGE(
        key = "dawn_ridge",
        resId = R.drawable.bg_dawn_ridge,
        label = "晨山云海",
        poetic = "天光初开，山脊浮在云上",
        sky = Color(0xFFD8DCD6),
        ground = Color(0xFF9B9B94),
        accent = Color(0xFFE4CDB4),
        dark = false
    ),
    ALPINE_LAKE(
        key = "alpine_lake",
        resId = R.drawable.bg_alpine_lake,
        label = "高山静湖",
        poetic = "水面如镜，山色倒映",
        sky = Color(0xFFBDCFC6),
        ground = Color(0xFFA3B9B5),
        accent = Color(0xFF72A5BE),
        dark = false
    ),
    FOREST_LIGHT(
        key = "forest_light",
        resId = R.drawable.bg_forest_light,
        label = "林间晨光",
        poetic = "光落在针叶林里",
        sky = Color(0xFF6B7268),
        ground = Color(0xFF4D5548),
        accent = Color(0xFF9AA484),
        dark = true
    ),
    COAST_SUNSET(
        key = "coast_sunset",
        resId = R.drawable.bg_coast_sunset,
        label = "海岸暮色",
        poetic = "潮线退去，余温未散",
        sky = Color(0xFFD0B4AA),
        ground = Color(0xFFBDA08F),
        accent = Color(0xFFDDAA88),
        dark = false
    ),
    RAIN_HILLS(
        key = "rain_hills",
        resId = R.drawable.bg_rain_hills,
        label = "烟雨青峦",
        poetic = "雨雾在岭间流动",
        sky = Color(0xFF869BA4),
        ground = Color(0xFF3E4E46),
        accent = Color(0xFF829AA9),
        dark = true
    ),
    SNOW_PINE(
        key = "snow_pine",
        resId = R.drawable.bg_snow_pine,
        label = "雪原松林",
        poetic = "落雪无声，天地一色",
        sky = Color(0xFFD9E1E0),
        ground = Color(0xFFC2CACA),
        accent = Color(0xFFBBCDD2),
        dark = false
    ),
    NIGHT_STARS(
        key = "night_stars",
        resId = R.drawable.bg_night_stars,
        label = "星野银河",
        poetic = "夜色沉在山脊背后",
        sky = Color(0xFF0F2234),
        ground = Color(0xFF2F5C82),
        accent = Color(0xFF3F7EB0),
        dark = true
    ),
    AUTUMN_VALLEY(
        key = "autumn_valley",
        resId = R.drawable.bg_autumn_valley,
        label = "秋谷层林",
        poetic = "层林染透，风里有凉意",
        sky = Color(0xFFB2A89A),
        ground = Color(0xFF604937),
        accent = Color(0xFF8D643B),
        dark = true
    );

    companion object {
        /** 兜底主题：数据未加载时展示 */
        val DEFAULT = ALPINE_LAKE

        val ordered: List<Scenery> = entries.toList()

        fun fromKey(key: String?): Scenery? =
            entries.firstOrNull { it.key == key }

        /** 用于「每日轮换」：同一天内结果稳定，跨天自动换景 */
        fun forDay(dayOfYear: Int): Scenery =
            ordered[((dayOfYear % ordered.size) + ordered.size) % ordered.size]
    }
}
