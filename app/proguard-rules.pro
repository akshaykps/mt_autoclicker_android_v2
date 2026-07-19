-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Keep kotlinx.serialization generated serializers used by presets, macros, and telemetry.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class net.mtautoclicker.android.data.**$$serializer { *; }
-keepclassmembers class net.mtautoclicker.android.data.** {
    *** Companion;
}
