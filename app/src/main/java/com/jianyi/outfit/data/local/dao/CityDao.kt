package com.jianyi.outfit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jianyi.outfit.data.local.entity.CityEntity
import kotlinx.coroutines.flow.Flow

/** 历史城市 DAO */
@Dao
interface CityDao {

    /** 观察全部历史城市，按最近使用时间倒序 */
    @Query("SELECT * FROM cities ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<CityEntity>>

    /** 观察当前选中城市 */
    @Query("SELECT * FROM cities WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrent(): Flow<CityEntity?>

    /** 按省市精确查找（唯一索引保证不重复） */
    @Query("SELECT * FROM cities WHERE province = :province AND city = :city LIMIT 1")
    suspend fun find(province: String, city: String): CityEntity?

    /** 最近使用的单个城市（删除当前城市后用于自动切换） */
    @Query("SELECT * FROM cities ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun latest(): CityEntity?

    @Insert
    suspend fun insert(city: CityEntity): Long

    @Update
    suspend fun update(city: CityEntity)

    /** 清除全部“当前城市”标记 */
    @Query("UPDATE cities SET isCurrent = 0")
    suspend fun clearCurrent()

    /** 将指定城市设为当前城市并刷新使用时间 */
    @Query("UPDATE cities SET isCurrent = 1, lastUsedAt = :now WHERE id = :id")
    suspend fun setCurrent(id: Long, now: Long)

    @Delete
    suspend fun delete(city: CityEntity)
}
