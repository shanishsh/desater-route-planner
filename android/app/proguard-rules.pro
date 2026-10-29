# ==============================================================================
# Uttarakhand Trek Safety Application - ProGuard / R8 Optimization Rules
# ==============================================================================

# --- Kotlin Coroutines & Serialization ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <init>(...);
}
-keep class kotlinx.serialization.** { *; }
-keep class com.touristsafety.app.core.network.dto.** { *; }
-keep class com.touristsafety.app.core.model.** { *; }

# --- MapLibre Native Android SDK ---
-keep class org.maplibre.android.** { *; }
-keepinterface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
-keep class com.mapbox.** { *; }
-dontwarn com.mapbox.**

# Preserve JNI method bindings for MapLibre native code
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Room Database ---
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class com.touristsafety.app.core.database.entity.** { *; }
-keep interface com.touristsafety.app.core.database.dao.** { *; }
-dontwarn androidx.room.paging.**

# --- Retrofit & OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --- Dagger / Hilt ---
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class androidx.hilt.** { *; }
-dontwarn dagger.hilt.android.internal.**

# --- AndroidX Core & Compose ---
-keep class androidx.compose.ui.platform.** { *; }
-dontwarn androidx.compose.ui.platform.**

