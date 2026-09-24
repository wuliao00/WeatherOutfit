package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.local.dao.CityDao
import com.jianyi.outfit.data.local.entity.CityEntity
import com.jianyi.outfit.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

/**
 * 城市仓库接口 + 实现：管理历史城市列表与当前城市切换，便于 JVM 单测替换实现。
 *
 * 这一步能整体下沉，是因为 Room 的 CityEntity 现在也是 shared 里的类型了
 * （见 data/local/ 下的实体）。此前它卡在 app：接口签名里带 Room 实体，
 * 而 Room 2.6 只有 Android 产物，等于把整个城市链锁死在 Android。
 */
interface CityRepository {

    /** 全部历史城市（按最近使用时间倒序） */
    val allCities: Flow<List<CityEntity>>

    /** 当前选中城市 */
    val currentCity: Flow<CityEntity?>

    /** 新增（或复用已存在）城市并设为当前城市 */
    suspend fun switchTo(province: String, city: String, source: String)

    /** 清除当前选中城市（回到自动定位链：GPS 优先 → IP 兜底） */
    suspend fun clearCurrentSelection()

    /** 删除历史城市；若删除的是当前城市，自动切换到最近使用的城市 */
    suspend fun delete(city: CityEntity)
}

/**
 * 城市仓库实现。
 * switchTo 的「查重 → 插入 → 清标记 → 设当前」多步写由 CityDao 的
 * @Transaction 方法保证原子性，避免并发下出现双当前城市或丢失标记。
 */
class CityRepositoryImpl(private val dao: CityDao) : CityRepository {

    override val allCities: Flow<List<CityEntity>> = dao.observeAll()

    override val currentCity: Flow<CityEntity?> = dao.observeCurrent()

    override suspend fun switchTo(province: String, city: String, source: String) {
        dao.switchTo(province, city, source, currentTimeMillis())
    }

    override suspend fun clearCurrentSelection() = dao.clearCurrent()

    override suspend fun delete(city: CityEntity) {
        dao.delete(city)
        if (city.isCurrent) {
            dao.latest()?.let { next ->
                dao.clearCurrent()
                dao.setCurrent(next.id, currentTimeMillis())
            }
        }
    }
}
