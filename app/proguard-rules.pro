# Hilt
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
}

# Room — keep entity and DAO class names
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Moshi — Kotlin reflection
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Timber
-dontwarn org.jetbrains.annotations.**

# Whoop protocol — keep byte ordering / math (no JVM intrinsification surprises)
-keep class com.sploot.whoopble.protocol.** { *; }
