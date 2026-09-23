package com.jianyi.outfit.data.repository

import com.jianyi.outfit.data.local.dao.OutfitTemplateDao
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 穿搭模板 itemsJson 的 Gson ↔ kotlinx.serialization 对拍测试（刚从 Gson 迁走）。
 *
 * 与天气缓存不同，这里存的是**用户亲手保存的模板**——解不出来等于用户丢数据，
 * 所以双向都要钉：
 * - 旧版本（Gson）写进库的行，新代码必须原样读出；
 * - 新代码写出的行，旧版本 Gson 仍要能读回（降级兼容）。
 * 用 fake DAO 走真实仓库代码路径，不直接测序列化器。
 */
class OutfitTemplateJsonTest {

    /** 内存 DAO：镜像真实 DAO 的契约 —— observeAll 按 createdAt 倒序 */
    private class FakeDao(
        initial: List<OutfitTemplateEntity> = emptyList()
    ) : OutfitTemplateDao {
        val rows = MutableStateFlow(initial)
        override fun observeAll(): Flow<List<OutfitTemplateEntity>> =
            rows.map { list -> list.sortedByDescending { it.createdAt } }
        override suspend fun insert(template: OutfitTemplateEntity): Long {
            rows.value = rows.value + template
            return (rows.value.indexOf(template) + 1).toLong()
        }
        override suspend fun deleteById(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
        override suspend fun delete(template: OutfitTemplateEntity) {
            rows.value = rows.value - template
        }
    }

    private val gson = Gson()

    /** 老版本 Gson 落库的行长这样：普通 JSON 字符串数组 */
    private val legacyRow = OutfitTemplateEntity(
        id = 7,
        name = "夏日通勤",
        scene = "通勤",
        minTemp = 26,
        maxTemp = 33,
        itemsJson = """["短袖","长裤","防晒帽"]""",
        tip = "轻薄透气",
        createdAt = 1758500000000L
    )

    @Test
    fun `legacy gson row decodes to the same items`() = runBlocking {
        val repo = OutfitTemplateRepositoryImpl(FakeDao(listOf(legacyRow)))
        val templates = repo.templates.first()
        assertEquals(1, templates.size)
        assertEquals(listOf("短袖", "长裤", "防晒帽"), templates[0].items)
        assertEquals("夏日通勤", templates[0].name)
        assertEquals(7L, templates[0].id)
    }

    @Test
    fun `newly written row is still gson readable`() = runBlocking {
        val dao = FakeDao()
        val repo = OutfitTemplateRepositoryImpl(dao)
        repo.save(
            com.jianyi.outfit.data.model.CustomOutfitTemplate(
                name = "雨季户外",
                scene = "户外",
                minTemp = 20,
                maxTemp = 28,
                items = listOf("速干T", "防风外套", "防水鞋"),
                tip = "带伞"
            )
        )
        val stored = dao.rows.value.single().itemsJson
        val back = gson.fromJson<List<String>>(stored, object : TypeToken<List<String>>() {}.type)
        assertEquals(listOf("速干T", "防风外套", "防水鞋"), back)
    }

    @Test
    fun `save then read round trips through the repository`() = runBlocking {
        val dao = FakeDao()
        val repo = OutfitTemplateRepositoryImpl(dao)
        repo.save(
            com.jianyi.outfit.data.model.CustomOutfitTemplate(
                name = "含引号\"与中文，逗号",
                scene = "休闲",
                minTemp = 15,
                maxTemp = 22,
                items = listOf("连帽衫", "运动鞋", "含\"引号\"的单品"),
                tip = ""
            )
        )
        val loaded = repo.templates.first().single()
        assertEquals(listOf("连帽衫", "运动鞋", "含\"引号\"的单品"), loaded.items)
        assertEquals("含引号\"与中文，逗号", loaded.name)
    }

    @Test
    fun `corrupt json becomes empty items instead of crashing`() = runBlocking {
        val corrupt = legacyRow.copy(id = 8, itemsJson = "not a json array")
        val repo = OutfitTemplateRepositoryImpl(FakeDao(listOf(corrupt)))
        val templates = repo.templates.first()
        assertTrue(templates[0].items.isEmpty())
    }

    @Test
    fun `gson legacy row and kotlinx new row coexist`() = runBlocking {
        // 升级后的典型状态：库里同时有旧版写的行和新版写的行
        val dao = FakeDao(listOf(legacyRow))
        val repo = OutfitTemplateRepositoryImpl(dao)
        repo.save(
            com.jianyi.outfit.data.model.CustomOutfitTemplate(
                name = "新版模板", scene = "休闲", minTemp = 18, maxTemp = 26,
                items = listOf("卫衣"), tip = ""
            )
        )
        val items = repo.templates.first().map { it.items }
        assertEquals(listOf(listOf("卫衣"), listOf("短袖", "长裤", "防晒帽")), items)
    }
}
