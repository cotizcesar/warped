# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.warped.**$$serializer { *; }
-keep,allowobfuscation,allowshrinking class **$$serializer { *; }
-keep,allowobfuscation,allowshrinking class com.warped.**$$serializer { *; }
-keepclassmembers class com.warped.** { *** Companion; }
-keepclasseswithmembers class com.warped.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# LiteRT-LM — keep ALL classes, members, and prevent ANY optimization of JNI-bound code
-keep,allowshrinking class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }
-keepnames class com.google.ai.edge.litertlm.** { *; }
-keepclassmembernames class com.google.ai.edge.litertlm.** { *; }
-keep interface com.google.ai.edge.litertlm.MessageCallback { *; }
-keep interface com.google.ai.edge.litertlm.ToolProvider { *; }
-keep class com.google.ai.edge.litertlm.MessageCallback$* { *; }
-keep class com.google.ai.edge.litertlm.ToolProvider$* { *; }
-dontoptimize
-dontobfuscate

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Hilt / Dagger
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel

# Hilt WorkManager — keep @HiltWorker classes and their generated
# WorkerAssistedFactory so HiltWorkerFactory can create them at runtime.
-keep,allowobfuscation @interface androidx.hilt.work.HiltWorker
-keep @androidx.hilt.work.HiltWorker class * {
    @dagger.assisted.AssistedInject <init>(...);
}
-keep class * extends androidx.hilt.work.WorkerAssistedFactory { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose stability annotations
-keep,allowobfuscation @interface androidx.compose.runtime.Immutable
-keep,allowobfuscation @interface androidx.compose.runtime.Stable

# Keep attributes for reflective access
-keepattributes InnerClasses,EnclosingMethod
