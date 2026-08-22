package com.jianyi.outfit.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jianyi.outfit.data.local.dao.OutfitTemplateDao
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity
import com.jianyi.outfit.data.model.CustomOutfitTemplate
import com.jianyi.outfit.data.model.OutfitPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 穿搭模板仓库：保存/删除用户自定义穿搭方案与收藏的推荐方案。
 */
class OutfitTemplateRepository(
    private val dao: OutfitTemplateDao,
    private val gson: Gson = Gson()
) {

    /** 观察全部模板（按创建时间倒序） */
    val templates: Flow<List<CustomOutfitTemplate>> = dao.observeAll().map { list ->
        list.map { it.toModel() }
    }

    /** 保存自定义模板，返回新记录 id */
    suspend fun save(template: CustomOutfitTemplate): Long =
        dao.insert(template.toEntity())

    /** 将推荐方案收藏为模板（首页长按推荐卡片触发） */
    suspend fun saveFromPlan(plan: OutfitPlan, minTemp: Int, maxTemp: Int): Long =
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

    suspend fun delete(id: Long) = dao.deleteById(id)

    /* ============ 实体与领域模型互转 ============ */

    private fun OutfitTemplateEntity.toModel(): CustomOutfitTemplate {
        val items = runCatching {
            gson.fromJson<List<String>>(itemsJson, object : TypeToken<List<String>>() {}.type)
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
            itemsJson = gson.toJson(items),
            tip = tip,
            createdAt = createdAt
        )
}
