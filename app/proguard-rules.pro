# Second Brain - Production ProGuard / R8 Rules

# Keep Room entities, DAOs, and database classes
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.brain.data.entity.** { *; }
-keep class com.example.brain.data.dao.** { *; }
-keep class com.example.brain.data.model.** { *; }
-keep class com.example.brain.data.backup.** { *; }
-dontwarn androidx.room.paging.**

# Keep SQLite Support classes
-keep class androidx.sqlite.db.** { *; }

# Keep Lifecycle and ViewModel reflection
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Kotlin Coroutines reflection
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Security Crypto & Biometrics
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

# Keep WorkManager Workers
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
