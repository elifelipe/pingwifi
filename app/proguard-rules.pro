# OkHttp/Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepattributes *Annotation*

# Compose (normalmente o BOM já cuida)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
