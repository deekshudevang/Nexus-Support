# ──────────────────────────────────────────────────────────────────────────────
# Nexus-Support / MeshLink – Production ProGuard / R8 Rules
# ──────────────────────────────────────────────────────────────────────────────

# Keep stack trace line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-dontwarn kotlin.**

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

# ── SQLCipher ─────────────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ── Retrofit / Gson ───────────────────────────────────────────────────────────
-keep class com.squareup.retrofit2.** { *; }
-keep interface com.squareup.retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepattributes Signature
-keepattributes Exceptions
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── OsmDroid / Mapsforge ──────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }
-keep class org.mapsforge.** { *; }
-dontwarn org.osmdroid.**
-dontwarn org.mapsforge.**

# ── Google Nearby / Play Services ─────────────────────────────────────────────
-keep class com.google.android.gms.nearby.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Timber ────────────────────────────────────────────────────────────────────
# Strip all Timber debug/verbose logs from release
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
}

# ── App domain model ──────────────────────────────────────────────────────────
# Keep all entities and domain models intact for Room and JSON serialisation
-keep class com.meshlink.app.domain.model.** { *; }
-keep class com.meshlink.app.data.local.entity.** { *; }
-keep class com.meshlink.app.data.remote.dto.** { *; }

# ── Protobuf ──────────────────────────────────────────────────────────────────
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**