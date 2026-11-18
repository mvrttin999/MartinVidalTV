# Reglas de ofuscacion (R8) para la build release.
# Moshi en modo reflexivo y Retrofit necesitan conservar firmas y anotaciones.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Modelos del portal y respuestas (Moshi reflexivo: generateAdapter = false).
-keep class com.martinvidal.tv.data.net.** { *; }
-keep class com.martinvidal.tv.data.model.** { *; }
# Manifiesto de actualizaciones: se parsea por reflexion desde JSON.
-keep class com.martinvidal.tv.data.repo.UpdateManifest { *; }

-keep,allowobfuscation @interface com.squareup.moshi.JsonClass
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.Json <fields>;
}

# Retrofit: interfaces y anotaciones de los metodos HTTP.
-keepclasseswithmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation @interface retrofit2.http.*

# Kotlin: metadatos y clases de apoyo.
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

-dontwarn org.jetbrains.annotations.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.**
