package com.jianyi.outfit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自定义穿搭模板表：用户保存的穿搭方案，
 * 相同天气条件下可直接调用，避免重复选择。
 */
@Entity(tableName = "outfit_templates")
data class OutfitTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,               // 模板名称
    val scene: String,              // 适用场景：通勤 / 户外 / 休闲
    val minTemp: Int,               // 适配最低温（℃）
    val maxTemp: Int,               // 适配最高温（℃）
    val itemsJson: String,          // 单品清单（JSON 数组字符串）
    val tip: String,                // 搭配小贴士
    val createdAt: Long = System.currentTimeMillis()
)
