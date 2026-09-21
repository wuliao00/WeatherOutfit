package com.jianyi.outfit.ui.scenery

/**
 * 天气 → 风景的映射规则。
 *
 * 设计原则：先按「有没有极端天气」决定基调（雪/雨/夜），再看温度与风力，
 * 最后用时段和月份做细化。规则从上到下短路，越靠前优先级越高，
 * 保证「下暴雨的中午」不会被判成「晴天的海岸」。
 *
 * 纯 Kotlin，不依赖 Compose，便于单元测试（见 SceneryResolverTest）。
 */
data class SceneryPick(val scenery: Scenery, val reason: String)

object SceneryResolver {

    /**
     * @param condition 天气现象文字，如「多云」「中雨」
     * @param tempC     当前气温（℃）
     * @param hour      24 小时制小时
     * @param windScale 蒲福风级
     * @param month     1~12 月，用于秋季换装感
     */
    fun pick(
        condition: String,
        tempC: Double,
        hour: Int,
        windScale: Int = 2,
        month: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
    ): SceneryPick {
        val night = hour < 6 || hour >= 19
        val text = condition.trim()

        fun pick(scenery: Scenery, reason: String) = SceneryPick(scenery, reason)

        // 1) 夜间优先：夜里看什么天气都不该是烈日海岸
        if (night) return pick(Scenery.NIGHT_STARS, "${hour} 时已是夜晚")

        // 2) 固态降水 / 极寒
        if (text.contains("雪") || text.contains("霰") || text.contains("冰粒")) {
            return pick(Scenery.SNOW_PINE, "有降雪")
        }
        if (tempC <= 2.0) return pick(Scenery.SNOW_PINE, "气温 ${tempC.toInt()}℃，接近冰点")

        // 3) 液态降水与低能见度
        if (text.contains("雨") || text.contains("雷")) return pick(Scenery.RAIN_HILLS, "有降水")
        if (text.contains("雾") || text.contains("霾")) return pick(Scenery.RAIN_HILLS, "能见度偏低")

        // 4) 高温
        if (tempC >= 32.0) return pick(Scenery.COAST_SUNSET, "气温 ${tempC.toInt()}℃，暑气重")

        // 5) 大风
        if (windScale >= 6) return pick(Scenery.AUTUMN_VALLEY, "风力 ${windScale} 级")

        // 6) 晴：按时段给不同光感
        if (text.contains("晴")) {
            return when (hour) {
                in 5..8 -> pick(Scenery.DAWN_RIDGE, "清晨有阳光")
                in 16..18 -> pick(Scenery.COAST_SUNSET, "傍晚有阳光")
                else -> pick(Scenery.ALPINE_LAKE, "白天晴好")
            }
        }

        // 7) 秋季加成：温度舒适 + 秋天，用层林点出季节感
        if (month in 9..11 && tempC in 8.0..20.0) {
            return pick(Scenery.AUTUMN_VALLEY, "秋季舒适温度")
        }

        // 8) 多云 / 阴：温和天气交给森林与静湖
        if (text.contains("多云") || text.contains("少云")) {
            return if (tempC in 12.0..26.0) pick(Scenery.FOREST_LIGHT, "多云且温度舒适")
            else pick(Scenery.ALPINE_LAKE, "多云")
        }
        if (text.contains("阴")) return pick(Scenery.ALPINE_LAKE, "阴天，光线平")

        return pick(Scenery.DEFAULT, "未匹配到特定天气，使用默认景")
    }
}
