# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { INSTANCE; }
-keep,includedescriptorclasses class ai.altertable.sdk.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ai.altertable.sdk.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor OkHttp engine
-dontwarn okhttp3.**
-dontwarn okio.**
