# GENERAL OPTIONS

# turn on all optimizations except those that are known to cause problems on Android
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 6
-allowaccessmodification
-dontpreverify

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-keepattributes *Annotation*

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
}
# keep setters in Views so that animations can still work.
# see http://proguard.sourceforge.net/manual/examples.html#beans
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}
# We want to keep methods in Activity that could be used in the XML attribute onClick
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# TUSKY SPECIFIC OPTIONS

# keep members of our model classes, they are used in json de/serialization
-keepclassmembers class com.keylesspalace.tusky.entity.* { *; }

-keep public enum com.keylesspalace.tusky.entity.*$** {
    **[] $VALUES;
    public *;
}

# preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# remove all logging from production apk
-assumenosideeffects class android.util.Log {
    public static *** getStackTraceString(...);
    public static *** d(...);
    public static *** w(...);
    public static *** v(...);
    public static *** i(...);
}

# remove some kotlin overhead
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

# without this emoji font downloading fails with AbstractMethodError
-keep class * extends android.os.AsyncTask {
    public *;
}
