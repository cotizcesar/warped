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
# 45-02 LRT-09: explicit keeps for 0.17.x tool entry points (verified present in
# litertlm-android-0.17.1 AAR classes.jar: ToolSet, OpenApiTool, Tool/ToolParam
# annotations, ReflectionTool, ToolKt tool() wrapper, Capabilities). The broad
# com.google.ai.edge.litertlm.** keeps above already cover these, but explicit
# rules protect the Phase-47 tool-wiring surface against future keep narrowing.
-keep interface com.google.ai.edge.litertlm.ToolSet { *; }
-keep interface com.google.ai.edge.litertlm.OpenApiTool { *; }
-keep @interface com.google.ai.edge.litertlm.Tool { *; }
-keep @interface com.google.ai.edge.litertlm.ToolParam { *; }
-keep class com.google.ai.edge.litertlm.ReflectionTool { *; }
-keep class com.google.ai.edge.litertlm.ToolKt { *; }
-keep class com.google.ai.edge.litertlm.Capabilities { *; }
-keepattributes *Annotation*
# NOTE: no global -dontoptimize/-dontobfuscate — release hardening stays on.
# If a 0.17.x native crash ever requires an exemption, scope it narrowly to the
# crashing class with a stack-trace citation and re-verify assembleRelease.

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

# Phase 49 (DEL-02/DEL-06): the com.warped skills surface is deleted — no
# keeps reference deleted classes. LiteRT-LM SDK keeps above stay intact.

# Phase 56 (56-01): keep the data.agentic ToolSet surface for LiteRT-LM
# @Tool reflection (mirrors the deleted data.skills keeps, 47 precedent —
# R8 must never strip the web_search/web_fetch schemas).
-keep class com.warped.data.agentic.** { *; }
-keepclassmembers class com.warped.data.agentic.** { *; }
