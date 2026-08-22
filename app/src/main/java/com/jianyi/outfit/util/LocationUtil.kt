package com.jianyi.outfit.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 定位工具：封装 FusedLocationProviderClient（Google Play Services）。
 * 权限由调用方（UI 层）先行申请；设备无 Play Services 时返回 null，
 * 调用方应回退到 IP 定位。
 */
class LocationUtil(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * 获取最近一次位置；没有缓存位置时发起一次平衡精度请求。
     * 需要调用方已获得 ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION 权限。
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Location? {
        val cached = try {
            client.lastLocation.awaitOrNull()
        } catch (e: Exception) {
            // 权限缺失时部分机型同步抛 SecurityException，视为不可用
            null
        }
        if (cached != null) return cached
        return try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).awaitOrNull()
        } catch (e: Exception) {
            // 无 Play Services 或定位服务未开启时静默失败，由上层回退 IP 定位
            null
        }
    }

    companion object {
        /**
         * 是否已授予任一定位权限。
         * 冷启动「GPS 优先」策略的静默前置条件：未授权时不触发定位请求，
         * 直接走 IP 兜底，避免无权限异常与打扰。
         */
        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}

/** 将 Google Play Services 的 Task 转为挂起函数，失败/取消时返回 null */
private suspend fun <T : Any> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
