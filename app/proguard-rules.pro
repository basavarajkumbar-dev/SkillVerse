# kotlinx.serialization: keep generated serializers for content models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.skillverse.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.skillverse.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.skillverse.app.**$$serializer { *; }

# WorkManager workers are instantiated reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
