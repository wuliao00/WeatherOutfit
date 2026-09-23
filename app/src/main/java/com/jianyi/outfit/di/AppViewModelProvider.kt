package com.jianyi.outfit.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jianyi.outfit.WeatherOutfitApp
import com.jianyi.outfit.ui.city.CityViewModel
import com.jianyi.outfit.ui.detail.DETAIL_ARG_CACHE_KEY
import com.jianyi.outfit.ui.detail.OutfitDetailViewModel
import com.jianyi.outfit.ui.home.HomeViewModel
import com.jianyi.outfit.ui.settings.SettingsViewModel

/**
 * ViewModel 工厂：统一从 Application 的依赖容器构造 ViewModel。
 * 页面内使用 viewModel(factory = AppViewModelProvider.Factory) 获取实例。
 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { HomeViewModel(app().container) }
        initializer {
            // 导航参数在 app 侧从 SavedStateHandle 取出后传给 shared 的 VM（VM 不感知导航实现）
            val handle = createSavedStateHandle()
            OutfitDetailViewModel(app().container, handle.get<String>(DETAIL_ARG_CACHE_KEY).orEmpty())
        }
        initializer { CityViewModel(app()) }
        initializer { SettingsViewModel(app().container) }
    }

    /** 从 CreationExtras 中取出应用实例 */
    private fun CreationExtras.app(): WeatherOutfitApp =
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WeatherOutfitApp
}
