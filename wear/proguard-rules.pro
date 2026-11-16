# Kotlinx Serialization & Wear DTOs
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
-keep class com.iank.im.peek.wear.data.bridge.** { *; }
-keep,includedescriptorclasses class com.iank.im.peek.wear.data.bridge.**$$serializer { *; }
