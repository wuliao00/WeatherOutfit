package com.jianyi.outfit.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.jianyi.outfit.data.local.dao.CityDao
import com.jianyi.outfit.data.local.dao.OutfitTemplateDao
import com.jianyi.outfit.data.local.entity.CityEntity
import com.jianyi.outfit.data.local.entity.OutfitTemplateEntity

/**
 * Room 业务数据库：历史城市、穿搭模板。
 * 天气缓存已拆分至独立的 WeatherCacheDatabase（便于备份排除）。
 *
 * 这个类现在在 shared 的 commonMain 里，两端共用同一份实体与 DAO 定义。
 * 开库动作是平台的（Android 要 Context 与 filesDir，iOS 要 Documents 路径），
 * 所以拆到各自的 source set：androidMain 的 buildAppDatabase(context) 与
 * iosMain 的 buildAppDatabase()。**文件名 jianyi.db 两端一致**，
 * 且 Android 侧沿用 2.6.1 时代的同一个重载与同一条迁移，老用户的数据目录不用动。
 */
@Database(
    entities = [
        CityEntity::class,
        OutfitTemplateEntity::class
    ],
    version = 2,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun outfitTemplateDao(): OutfitTemplateDao

    companion object {
        /**
         * v1 → v2：天气缓存表迁移至独立的 weather_cache.db，业务库不再保留该表。
         * 缓存数据为临时数据，直接丢弃即可。
         *
         * 参数类型从 Android 的 SupportSQLiteDatabase 换成了 androidx.sqlite 的
         * SQLiteConnection —— 这是 Room 2.7 KMP 化的唯一 API 变化，语义一致
         * （Room 已经在事务里调用它），SQL 一字没改。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                val statement = connection.prepare("DROP TABLE IF EXISTS weather_cache")
                try {
                    statement.step()
                } finally {
                    statement.close()
                }
            }
        }
    }
}

/**
 * Room 2.7 的 KMP 要求：数据库实现类的构造入口。
 *
 * Android 侧靠反射按类名找 `AppDatabase_Impl`，而 Kotlin/Native 没有反射，
 * 只能由 KSP 为每个平台生成 `actual object`，再通过 @ConstructedBy 的
 * associated object 查回来（`findDatabaseConstructorAndInitDatabaseImpl`）。
 * 所以这里 expect、各平台由注解处理器补 actual —— 我们一行 actual 都不用手写。
 *
 * `KotlinNoActualForExpect` 的抑制是给 commonMain 的元数据编译看的：
 * 那次编译看不到任何平台的 actual，但三个 target 的实际编译都能看到。
 */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
