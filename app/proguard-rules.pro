# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.iank.im.peek.data.remote.**$$serializer { *; }
-keepclassmembers class com.iank.im.peek.data.remote.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.iank.im.peek.domain.model.**$$serializer { *; }
-keepclassmembers class com.iank.im.peek.domain.model.** {
    *** Companion;
}

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# Google Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
