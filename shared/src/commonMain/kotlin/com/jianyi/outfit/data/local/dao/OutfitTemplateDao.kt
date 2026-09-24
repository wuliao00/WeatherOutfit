package com.jianyi.outfit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity
import kotlinx.coroutines.flow.Flow

/** 自定义穿搭模板 DAO */
@Dao
interface OutfitTemplateDao {

    /** 观察全部模板，按创建时间倒序 */
    @Query("SELECT * FROM outfit_templates ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OutfitTemplateEntity>>

    @Insert
    suspend fun insert(template: OutfitTemplateEntity): Long

    /** 按 id 删除模板 */
    @Query("DELETE FROM outfit_templates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(template: OutfitTemplateEntity)
}
