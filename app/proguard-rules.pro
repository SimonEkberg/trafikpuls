# kotlinx.serialization: keep generated serializers for our DTOs and navigation routes.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.simon.trafikpuls.**$$serializer { *; }
-keepclassmembers class com.simon.trafikpuls.** {
    *** Companion;
}
-keepclasseswithmembers class com.simon.trafikpuls.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interface (suspend functions use reflection on generic signatures).
-keep,allowobfuscation interface com.simon.trafikpuls.data.remote.TrafiklabApi
