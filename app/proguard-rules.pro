# Kotlinx Serialization & API DTOs
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    <fields>;
}
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.iank.im.peek.data.remote.** { *; }
-keep,includedescriptorclasses class com.iank.im.peek.data.remote.**$$serializer { *; }
-keep class com.iank.im.peek.domain.model.** { *; }
-keep,includedescriptorclasses class com.iank.im.peek.domain.model.**$$serializer { *; }
-keep class com.iank.im.peek.wearbridge.** { *; }
-keep,includedescriptorclasses class com.iank.im.peek.wearbridge.**$$serializer { *; }

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
