# Keep SQLDelight generated classes
-keep class com.lush1us.podder.db.** { *; }

# Keep Koin
-keep class org.koin.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; }

# Keep Media3 service
-keep class androidx.media3.** { *; }

# Keep WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
