# Keep Room entities and DAOs
-keep class com.helucryptic.android.data.db.** { *; }

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# Keep BouncyCastle crypto classes
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep WebRTC classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep OkHttp + WebSocket internals
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep ZXing (QR)
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# Keep DataStore preference keys
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences$Key { *; }

# Keep JSON-serialised fields (JSONObject used directly - no reflection needed)

# Keep BuildConfig
-keep class com.helucryptic.android.BuildConfig { *; }
