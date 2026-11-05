# R8 rules for DWR Sync.
# AndroidX libraries (Compose, WorkManager, Health Connect, security-crypto) ship their own
# consumer rules, so only app-specific and missing-class rules live here.

# Keep line numbers for readable crash traces; mapping.txt is attached to each GitHub release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager creates the worker by class name. Its consumer rules cover this already;
# kept explicitly because a renamed worker would break already-scheduled jobs after an update.
-keep class com.yogeshpaliyal.dwrsync.SyncWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Sync log state is stored by enum name (SyncState.valueOf).
-keepclassmembers enum com.yogeshpaliyal.dwrsync.SyncState { *; }

# Tink (used by EncryptedSharedPreferences) references optional annotation and HTTP classes.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**

# Strip Kotlin null-check intrinsics' parameter-name strings in release.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
}
