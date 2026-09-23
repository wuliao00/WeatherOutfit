package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.model.CustomOutfitTemplate
import com.jianyi.outfit.data.model.OutfitPlan
import kotlinx.coroutines.flow.Flow

/**
 * 穿搭模板仓库接口：保存/删除用户自定义穿搭方案与收藏的推荐方案，
 * 便于 JVM 单测替换实现。接口在 shared，Room 实现留在 app。
 */
interface OutfitTemplateRepository {

    /** 观察全部模板（按创建时间倒序） */
    val templates: Flow<List<CustomOutfitTemplate>>

    /** 保存自定义模板，返回新记录 id */
    suspend fun save(template: CustomOutfitTemplate): Long

    /** 将推荐方案收藏为模板（首页长按推荐卡片触发） */
    suspend fun saveFromPlan(plan: OutfitPlan, minTemp: Int, maxTemp: Int): Long

    suspend fun delete(id: Long)
}
