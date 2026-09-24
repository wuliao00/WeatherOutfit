package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.local.dao.OutfitTemplateDao
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity
import com.jianyi.outfit.data.model.CustomOutfitTemplate
import com.jianyi.outfit.data.model.OutfitPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 穿搭模板仓库实现。
 *
 * 接口在同包的 OutfitTemplateRepository.kt 里（同包，无需 import）。
 *
 * itemsJson 是**用户数据**（自定义模板的单品清单），落盘格式是 JSON 字符串数组。
 * 这里从 Gson 换成 kotlinx.serialization 时，两种格式的写法逐字节一致
 * （纯字符串数组没有任何 Gson 特有语法），对拍单测钉住双向兼容：
 * 旧版本写进库的行必须能读，新写出的行旧版本也必须能读。
 */
class OutfitTemplateRepositoryImpl(
    private val dao: OutfitTemplateDao
) : OutfitTemplateRepository {

    /** 与天气侧的 weatherJson 同款宽松配置；解析失败按"无单品"处理，绝不抛 */
    private val templateJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 观察全部模板（按创建时间倒序） */
    override val templates: Flow<List<CustomOutfitTemplate>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    /** 保存自定义模板，返回新记录 id */
    override suspend fun save(template: CustomOutfitTemplate): Long =
        dao.insert(template.toEntity())

    /** 将推荐方案收藏为模板（首页长按推荐卡片触发） */
    override suspend fun saveFromPlan(plan: OutfitPlan, minTemp: Int, maxTemp: Int): Long =
        save(
            CustomOutfitTemplate(
                name = "推荐方案·${plan.scene}",
                scene = plan.scene,
                minTemp = minTemp,
                maxTemp = maxTemp,
                items = plan.items,
                tip = plan.tip
            )
        )

    override suspend fun delete(id: Long) = dao.deleteById(id)

    /* ============ 实体与领域模型互转 ============ */

    private fun OutfitTemplateEntity.toModel(): CustomOutfitTemplate {
        val items = runCatching {
            templateJson.decodeFromString<List<String>>(itemsJson)
        }.getOrNull() ?: emptyList()
        return CustomOutfitTemplate(
            id = id,
            name = name,
            scene = scene,
            minTemp = minTemp,
            maxTemp = maxTemp,
            items = items,
            tip = tip,
            createdAt = createdAt
        )
    }

    private fun CustomOutfitTemplate.toEntity(): OutfitTemplateEntity =
        OutfitTemplateEntity(
            id = if (id == 0L) 0L else id,
            name = name,
            scene = scene,
            minTemp = minTemp,
            maxTemp = maxTemp,
            itemsJson = templateJson.encodeToString(items),
            tip = tip,
            createdAt = createdAt
        )
}
