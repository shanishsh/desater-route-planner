package com.touristsafety.app.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverConfigManager: ServerConfigManager
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var currentUrlStr = serverConfigManager.getCurrentUrl()

        // Fail-safe: A physical device can never reach the Android emulator's loopback alias (10.0.2.2).
        // Automatically correct to live Render Cloud if detected.
        if (!ServerConfigManager.isRunningOnEmulator() && (currentUrlStr.contains("10.0.2.2") || currentUrlStr.contains("127.0.0.1"))) {
            currentUrlStr = ServerConfigManager.PRESET_RENDER
            serverConfigManager.setBaseUrl(ServerConfigManager.PRESET_RENDER)
        }

        val configuredBase = currentUrlStr.toHttpUrlOrNull()

        if (configuredBase != null) {
            val oldUrl = request.url
            val newUrl = oldUrl.newBuilder()
                .scheme(configuredBase.scheme)
                .host(configuredBase.host)
                .port(configuredBase.port)
                .build()

            request = request.newBuilder()
                .url(newUrl)
                .build()
        }

        return chain.proceed(request)
    }
}
