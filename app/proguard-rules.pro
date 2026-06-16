# Keep OTP parser models (serialized via kotlinx-serialization)
-keep class com.bigotp.app.parser.** { *; }
-keep class com.bigotp.app.config.** { *; }

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
