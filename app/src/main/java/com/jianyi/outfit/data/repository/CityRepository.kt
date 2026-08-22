package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.local.dao.CityDao
import com.jianyi.outfit.data.local.entity.CityEntity
import kotlinx.coroutines.flow.Flow

/**
 * 城市仓库：管理历史城市列表与当前城市切换。
 */
class CityRepository(private val dao: CityDao) {

    /** 全部历史城市（按最近使用时间倒序） */
    val allCities: Flow<List<CityEntity>> = dao.observeAll()

    /** 当前选中城市 */
    val currentCity: Flow<CityEntity?> = dao.observeCurrent()

    /** 新增（或复用已存在）城市并设为当前城市 */
    suspend fun switchTo(province: String, city: String, source: String) {
        val now = System.currentTimeMillis()
        val existing = dao.find(province, city)
        val id = existing?.id
            ?: dao.insert(
                CityEntity(province = province, city = city, source = source, lastUsedAt = now)
            )
        dao.clearCurrent()
        dao.setCurrent(id, now)
    }

    /** 清除当前选中城市（回到自动定位链：GPS 优先 → IP 兜底） */
    suspend fun clearCurrentSelection() = dao.clearCurrent()

    /** 删除历史城市；若删除的是当前城市，自动切换到最近使用的城市 */
    suspend fun delete(city: CityEntity) {
        dao.delete(city)
        if (city.isCurrent) {
            dao.latest()?.let { next ->
                dao.clearCurrent()
                dao.setCurrent(next.id, System.currentTimeMillis())
            }
        }
    }
}
