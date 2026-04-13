# APK Forge ProGuard Rules

# Keep Application class
-keep class com.apkforge.ApkForgeApp { *; }

# Keep all model classes
-keep class com.apkforge.model.** { *; }

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.apkforge.security.NativeCrypto { *; }
-keep class com.apkforge.utils.NativeFileOps { *; }

# Keep Java API & core classes
-keep class com.apkforge.network.GithubApiClient { *; }
-keep class com.apkforge.network.GithubApiClient$* { *; }
-keep class com.apkforge.core.BuildManager { *; }
-keep class com.apkforge.core.BuildManager$* { *; }
-keep class com.apkforge.webhook.WebhookTrigger { *; }
-keep class com.apkforge.utils.FileUtils { *; }

# Keep ViewModels
-keep class com.apkforge.viewmodel.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Navigation Component
-keep class androidx.navigation.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
