package com.touristsafety.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.touristsafety.app.core.crash.CrashReportActivity
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

        // Global uncaught exception logger and crash reporter
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            Log.e("FATAL_APP_CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText("Thread: ${thread.name}\nTimestamp: ${System.currentTimeMillis()}\nError: $stackTrace")
            } catch (_: Throwable) {
                // Ignore secondary errors during file write
            }

            try {
                val crashIntent = Intent(this, CrashReportActivity::class.java).apply {
                    putExtra(
                        CrashReportActivity.EXTRA_ERROR_DETAILS,
                        "Thread: ${thread.name}\n${throwable.javaClass.name}: ${throwable.message}\n\n$stackTrace"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(crashIntent)
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (fallbackError: Throwable) {
                Log.e("FATAL_APP_CRASH", "Failed to launch CrashReportActivity", fallbackError)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Initialize Firebase SDK safely (runs cleanly with or without google-services.json)
        try {
            val firebaseApp = FirebaseApp.initializeApp(this)
            if (firebaseApp != null) {
                Log.i("TouristSafetyApp", "Firebase initialized successfully: ${firebaseApp.name}")
            } else {
                Log.w("TouristSafetyApp", "FirebaseApp.initializeApp returned null (configuration pending)")
            }
        } catch (t: Throwable) {
            Log.w("TouristSafetyApp", "Firebase initialization skipped or pending configuration: ${t.message}")
        }

        try {
            // 1. Initialize MapLibre Native SDK FIRST before any HTTP or view classes are touched
            MapLibre.getInstance(this)

            // 2. Configure MapLibre's HTTP client
            val dispatcher = Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 8
            }
            val loggingInterceptor = okhttp3.logging.HttpLoggingInterceptor { message ->
                Log.d("MapLibreHttp", message)
            }.apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
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
                .addInterceptor(loggingInterceptor)
                .build()

            HttpRequestUtil.setLogEnabled(true)
            HttpRequestUtil.setPrintRequestUrlOnFailure(true)
            HttpRequestUtil.setOkHttpClient(maplibreOkHttpClient)
            Log.i("TouristSafetyApp", "MapLibre Native SDK and OkHttpClient initialized successfully")
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

