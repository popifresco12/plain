# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Gson: los modelos se serializan por nombre de campo — no ofuscar
-keep class com.plain.app.data.** { *; }
-keep class com.plain.app.data.auth.** { *; }

# Retrofit + OkHttp ya traen sus consumers de reglas (META-INF/proguard)
# pero por robustez:
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.logging.**
