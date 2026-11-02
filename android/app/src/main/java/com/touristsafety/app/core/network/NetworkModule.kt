package com.touristsafety.app.core.network

import android.content.Context
import com.touristsafety.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        // 50 MB HTTP disk cache for offline resilience and repeated query acceleration
        val cacheDir = File(context.cacheDir, "http_cache")
        val httpCache = Cache(cacheDir, 50L * 1024 * 1024)
        val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

        val builder = OkHttpClient.Builder()
            .cache(httpCache)
            .connectionPool(connectionPool)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val request = chain.request()
                // Cache successful GET responses with a short freshness window if upstream didn't specify
                if (request.method == "GET" && response.isSuccessful) {
                    val existingCacheControl = response.header("Cache-Control")
                    if (existingCacheControl.isNullOrBlank() || existingCacheControl.contains("no-cache")) {
                        response.newBuilder()
                            .header("Cache-Control", "public, max-age=60")
                            .build()
                    } else {
                        response
                    }
                } else {
                    response
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}
