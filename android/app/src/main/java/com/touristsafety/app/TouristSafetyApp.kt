package com.touristsafety.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TouristSafetyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Global uncaught exception logger to record any startup crash
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FATAL_APP_CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText("Thread: ${thread.name}\nTimestamp: ${System.currentTimeMillis()}\nError: ${throwable.stackTraceToString()}")
            } catch (_: Throwable) {
                // Ignore secondary errors during crash recording
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            // Configure MapLibre's HTTP client to strictly comply with OpenFreeMap terms:
            // 1. At most four parallel requests (maxRequests = 4, maxRequestsPerHost = 4)
            // 2. Distinct descriptive User-Agent identifying the application and repository
            val dispatcher = Dispatcher().apply {
                maxRequests = 4
                maxRequestsPerHost = 4
            }
            val maplibreOkHttpClient = OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val requestWithAgent = original.newBuilder()
                        .header(
                            "User-Agent",
                            "UttarakhandDisasterRoutePlanner/1.0 (Android; OpenFreeMap Free Basemap Client; +https://github.com/shanishsh/desater-route-planner)"
                        )
                        .build()
                    chain.proceed(requestWithAgent)
                }
                .build()

            try {
                HttpRequestUtil.setOkHttpClient(maplibreOkHttpClient)
            } catch (t: Throwable) {
                Log.w("TouristSafetyApp", "HttpRequestUtil configuration skipped or unavailable", t)
            }

            // Initialize MapLibre Native SDK
            try {
                MapLibre.getInstance(this)
            } catch (t: Throwable) {
                Log.e("TouristSafetyApp", "MapLibre Native binary initialization deferred or unsupported on this architecture", t)
            }
        } catch (t: Throwable) {
            Log.e("TouristSafetyApp", "MapLibre SDK initialization failed safely", t)
        }
    }

    override val workManagerConfiguration: Configuration
        get() {
            val builder = Configuration.Builder()
            if (::workerFactory.isInitialized) {
                builder.setWorkerFactory(workerFactory)
            }
            return builder.build()
        }
}

