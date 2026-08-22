package com.jianyi.outfit.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 历史城市表：保存用户查询过的城市，用于快速切换。
 */
@Entity(
    tableName = "cities",
    indices = [Index(value = ["province", "city"], unique = true)]
)
data class CityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val province: String,          // 省份短名，如“四川”（接口约定不带“省”字）
    val city: String,              // 城市/区名，如“成都”
    val isCurrent: Boolean = false,// 是否为当前展示城市
    val lastUsedAt: Long = 0L,     // 最近使用时间戳（毫秒），列表按此倒序
    val source: String = "search"  // 来源：search 搜索 / gps 定位 / ip 自动定位
)
