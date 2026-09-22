package com.jianyi.outfit.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android 用 OkHttp 引擎。
 *
 * 选它而不是 ktor-client-android：OkHttp 的连接池与线程模型我们已经用了好几年，
 * 超时、代理、TLS 行为都是验证过的；换引擎等于在换网络库的同时悄悄换了底层实现。
 */
internal actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()
