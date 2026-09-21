package com.jianyi.outfit.engine

import com.jianyi.outfit.data.model.LifeIndex
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WeatherNow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 生活指数推导引擎。
 *
 * 数据源接口不提供这些指数，全部由实况（温度 / 湿度 / 降水 / 风 / 紫外线）本地算出。
 * 因此 UI 必须标注「本地估算」——把估算包装成官方数据是这个 App 最不该做的事。
 *
 * 纯 Kotlin，不依赖 Android 框架，可直接单测。
 */
object LifeIndexEngine {

    fun compute(weather: WeatherNow, prefs: UserPreferences): List<LifeIndex> {
        // 与穿搭引擎用同一套耐寒/耐热修正，避免「穿衣建议说冷、运动指数说不冷」的自相矛盾
        val rawFeels = weather.feelsLike ?: weather.temperature
        val feelsLike = rawFeels + when (prefs.coldHeatTolerance) {
            com.jianyi.outfit.data.model.ToleranceLevel.LOW -> -2.0
            com.jianyi.outfit.data.model.ToleranceLevel.HIGH -> 2.0
            com.jianyi.outfit.data.model.ToleranceLevel.MEDIUM -> 0.0
        }
        val windScale = OutfitRecommendationEngine.parseWindScale(
            weather.windScaleText, weather.windSpeedMs
        )
        val rainy = OutfitRecommendationEngine.hasPrecipitation(weather)
        val snowy = weather.condition.contains("雪")
        val humidity = weather.humidity ?: 50

        return listOf(
            clothing(feelsLike, rainy, snowy, windScale),
            sunscreen(weather),
            exercise(feelsLike, rainy, snowy, windScale, weather.uvIndex),
            carWash(rainy, snowy, windScale, weather.condition),
            cold(feelsLike, weather, humidity)
        )
    }

    /* ============ 五项指数 ============ */

    private fun clothing(feels: Double, rainy: Boolean, snowy: Boolean, wind: Int): LifeIndex {
        val (score, advice) = when {
            snowy || feels <= 0 -> 1 to "积雪或零度以下，必须羽绒服加围巾手套，别指望靠走路取暖。"
            feels < 8 -> 2 to "偏冷，建议外套里面加一层保暖，重点护住脖子和脚踝。"
            feels < 16 -> 3 to "早晚凉、午后暖，最省事的办法是叠穿一件能随时脱的外套。"
            feels < 24 -> 5 to "温度舒服，单层长袖加薄外套就够，穿脱都方便。"
            feels < 30 -> 4 to "有点热，短袖加透气长裤，选浅色更凉快。"
            else -> 2 to "高温闷热，尽量短袖短裤并主动补水，正午少出门。"
        }
        val extra = when {
            rainy -> " 有降水，记得带伞。"
            wind >= 5 -> " 风大，少穿宽松敞口的衣物。"
            else -> ""
        }
        return LifeIndex("clothing", "穿衣", LifeIndex.levelText(score), score, advice + extra)
    }

    private fun sunscreen(weather: WeatherNow): LifeIndex {
        val (score, advice) = when (weather.uvIndex) {
            in 0..2 -> 5 to "紫外线很弱，不需要特别防晒。"
            in 3..4 -> 4 to "紫外线偏弱，长时间在外抹一层轻薄防晒即可。"
            in 5..6 -> 3 to "中等强度，建议涂防晒并戴帽子。"
            in 7..9 -> 2 to "强度高，防晒霜加墨镜加遮阳帽，尽量走阴凉处。"
            else -> 1 to "紫外线很强，正午时段尽量避免露天活动。"
        }
        return LifeIndex("sunscreen", "防晒", LifeIndex.levelText(score), score, advice)
    }

    private fun exercise(
        feels: Double, rainy: Boolean, snowy: Boolean, wind: Int, uv: Int
    ): LifeIndex {
        var score = 5
        val reasons = mutableListOf<String>()

        if (rainy || snowy) {
            score -= 3
            reasons += if (snowy) "有降雪，路面湿滑" else "有降水"
        }
        when {
            feels >= 33 -> { score -= 2; reasons += "体感偏热，容易中暑" }
            feels <= 3 -> { score -= 2; reasons += "体感偏冷，热身时间要拉长" }
            feels in 4.0..9.0 -> { score -= 1; reasons += "偏凉，注意起跑后及时加衣" }
        }
        if (wind >= 6) { score -= 1; reasons += "风力较大" }
        if (uv >= 7) { score -= 1; reasons += "紫外线强" }

        val advice = if (reasons.isEmpty()) {
            "天气条件很适合户外运动，傍晚比正午更舒适。"
        } else {
            "${reasons.joinToString("、")}，建议改成室内或缩短时长。"
        }
        val final = score.coerceIn(1, 5)
        return LifeIndex("exercise", "运动", LifeIndex.levelText(final), final, advice)
    }

    private fun carWash(rainy: Boolean, snowy: Boolean, wind: Int, condition: String): LifeIndex {
        val (score, advice) = when {
            rainy || snowy -> 1 to "近期有降水，洗完很快又会脏，建议等天晴。"
            condition.contains("雾") || condition.contains("霾") ->
                2 to "空气湿度大且有颗粒物，洗车后容易附着脏污。"
            wind >= 5 -> 2 to "风大，沙尘会很快把车重新弄脏。"
            else -> 5 to "天气干燥稳定，洗车后能保持较久。"
        }
        return LifeIndex("carwash", "洗车", LifeIndex.levelText(score), score, advice)
    }

    private fun cold(feels: Double, weather: WeatherNow, humidity: Int): LifeIndex {
        val diff = if (weather.dayHigh != null && weather.dayLow != null) {
            abs(weather.dayHigh!! - weather.dayLow!!)
        } else 0.0

        var score = 5
        val reasons = mutableListOf<String>()
        if (feels <= 8) { score -= 2; reasons += "气温低" }
        if (diff >= 10) { score -= 2; reasons += "一天之内温差 ${diff.roundToInt()}℃" }
        else if (diff >= 8) { score -= 1; reasons += "温差接近 ${diff.roundToInt()}℃" }
        if (humidity >= 80 && feels <= 16) { score -= 1; reasons += "湿冷明显" }

        val advice = when {
            reasons.isEmpty() -> "气温平稳，感冒风险不高。"
            else -> "${reasons.joinToString("、")}，注意颈部与脚踝保暖，出汗后别立刻吹风。"
        }
        val final = score.coerceIn(1, 5)
        return LifeIndex("cold", "感冒", LifeIndex.levelText(final), final, advice)
    }
}
