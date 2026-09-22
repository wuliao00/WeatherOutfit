package com.jianyi.outfit.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS 只能用 Darwin(NSURLSession) 引擎 —— App Store 不接受用非系统网络栈的写法，
 * 而且后台 URLSession、ATS、蜂窝网络切换这些行为都得走系统实现。
 */
internal actual fun httpClientEngine(): HttpClientEngine = Darwin.create()
