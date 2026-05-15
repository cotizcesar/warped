# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.warped.**$$serializer { *; }
-keepclassmembers class com.warped.** { *** Companion; }
-keepclasseswithmembers class com.warped.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# LiteRT-LM
-keep class com.google.ai.edge.litertlm.** { *; }

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

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep attributes for reflective access
-keepattributes InnerClasses,EnclosingMethod

# Highlights — syntax tokenization engine (Phase 28)
-keep class dev.snipme.highlights.** { *; }
-dontwarn dev.snipme.highlights.**

# Syntax Highlighting domain models (Phase 28-30)
-keep class com.warped.domain.model.SyntaxTheme { *; }
-keep class com.warped.domain.model.SyntaxTheme$Companion { *; }
-keep class com.warped.domain.model.SyntaxColor { *; }
-keep class com.warped.domain.model.TokenType { *; }
-keep class com.warped.domain.model.SyntaxToken { *; }
-keep class com.warped.ui.chat.components.MarkdownBlock { *; }
-keep class com.warped.ui.chat.components.MarkdownBlock$** { *; }

# Hilt EntryPoints for composables (Phase 29)
-keep class com.warped.ui.chat.components.SyntaxHighlightingEntryPoint { *; }
-keep class com.warped.ui.chat.components.MarkdownEntryPoint { *; }

# Hilt DI module (Phase 28)
-keep class com.warped.di.SyntaxModule { *; }

# Kotlinx Serialization — HuggingFace DTOs (Phase 30 description field)
-keep class com.warped.data.remote.dto.HuggingFaceModel { *; }
-keep class com.warped.data.remote.dto.HuggingFaceSearchResponse { *; }

