# JNI entry points are looked up by name from native code.
-keep class com.boldblot.backgammonplyground.engine.NativeEngine { native <methods>; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.boldblot.backgammonplyground.** { *** Companion; }
-keepclasseswithmembers class com.boldblot.backgammonplyground.** { kotlinx.serialization.KSerializer serializer(...); }
