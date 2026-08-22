package com.jianyi.outfit.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jianyi.outfit.data.model.Gender
import com.jianyi.outfit.data.model.StylePreference
import com.jianyi.outfit.data.model.TempUnit
import com.jianyi.outfit.data.model.ToleranceLevel
import com.jianyi.outfit.data.model.UserPreferences
import com.jianyi.outfit.data.model.WindUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 应用级 DataStore 实例（懒加载单例） */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * 设置仓库：基于 Preferences DataStore 的轻量配置持久化。
 */
class SettingsRepository(private val context: Context) {

    /** 观察用户偏好（读取异常时回退默认值） */
    val preferences: Flow<UserPreferences> = context.settingsStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it.toUserPreferences() }

    suspend fun setTempUnit(unit: TempUnit) = update { it[KEY_TEMP_UNIT] = unit.name }

    suspend fun setWindUnit(unit: WindUnit) = update { it[KEY_WIND_UNIT] = unit.name }

    suspend fun setTolerance(level: ToleranceLevel) = update { it[KEY_TOLERANCE] = level.name }

    suspend fun setStyle(style: StylePreference) = update { it[KEY_STYLE] = style.name }

    suspend fun setGender(gender: Gender) = update { it[KEY_GENDER] = gender.name }

    suspend fun setDailyPush(enabled: Boolean) = update { it[KEY_DAILY_PUSH] = enabled }

    suspend fun setExtremeAlert(enabled: Boolean) = update { it[KEY_EXTREME_ALERT] = enabled }

    /** 是否已确认首次启动免责声明（勾选「不再提示」后为 true） */
    val disclaimerAccepted: Flow<Boolean> = context.settingsStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it[KEY_DISCLAIMER_ACCEPTED] ?: false }

    /** 标记 / 重置免责声明确认状态（false 时下次启动重新弹出） */
    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        if (accepted) update { it[KEY_DISCLAIMER_ACCEPTED] = true }
        else context.settingsStore.edit { it.remove(KEY_DISCLAIMER_ACCEPTED) }
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }

    companion object {
        private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
        private val KEY_WIND_UNIT = stringPreferencesKey("wind_unit")
        private val KEY_TOLERANCE = stringPreferencesKey("tolerance")
        private val KEY_STYLE = stringPreferencesKey("style")
        private val KEY_GENDER = stringPreferencesKey("gender")
        private val KEY_DAILY_PUSH = booleanPreferencesKey("daily_push")
        private val KEY_EXTREME_ALERT = booleanPreferencesKey("extreme_alert")
        private val KEY_DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")

        /** Preferences → 领域模型（未写入过的键取默认值） */
        private fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
            tempUnit = TempUnit.safe(this[KEY_TEMP_UNIT]),
            windUnit = WindUnit.safe(this[KEY_WIND_UNIT]),
            coldHeatTolerance = ToleranceLevel.safe(this[KEY_TOLERANCE]),
            style = StylePreference.safe(this[KEY_STYLE]),
            gender = Gender.safe(this[KEY_GENDER]),
            dailyPushEnabled = this[KEY_DAILY_PUSH] ?: false,
            extremeAlertEnabled = this[KEY_EXTREME_ALERT] ?: true
        )
    }
}
