# GENERAL OPTIONS

-allowaccessmodification

# Preserve some attributes that may be required for reflection.
-keepattributes RuntimeVisible*Annotations, AnnotationDefault

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
}

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Preserve annotated Javascript interface methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# The support libraries contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version. We know about them, and they are safe.
-dontnote androidx.**
-dontwarn androidx.**

# This class is deprecated, but remains for backward compatibility.
-dontwarn android.util.FloatMath

# These classes are duplicated between android.jar and core-lambda-stubs.jar.
-dontnote java.lang.invoke.**

# TUSKY SPECIFIC OPTIONS

<<<<<<< HEAD
# keep members of our model classes, they are used in json de/serialization
-keepclassmembers class com.keylesspalace.tusky.entity.* { *; }

-keep public enum com.keylesspalace.tusky.entity.*$** {
    **[] $VALUES;
    public *;
}

-keepclassmembers class com.keylesspalace.tusky.components.conversation.ConversationAccountEntity { *; }
-keepclassmembers class com.keylesspalace.tusky.db.entity.DraftAttachment { *; }

-keep enum com.keylesspalace.tusky.db.entity.DraftAttachment$Type {
    public *;
}

# https://github.com/google/gson/blob/master/examples/android-proguard-example/proguard.cfg

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

=======
>>>>>>> develop
# preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Bouncy Castle -- Keep EC
-keep class org.bouncycastle.jcajce.provider.asymmetric.EC$* { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGeneratorSpi$EC

# remove all logging from production apk
-assumenosideeffects class android.util.Log {
    public static *** getStackTraceString(...);
    public static *** d(...);
    public static *** w(...);
    public static *** v(...);
    public static *** i(...);
}
-assumenosideeffects class java.lang.String {
    public static java.lang.String format(...);
}

# remove some kotlin overhead
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

# Preference fragments can be referenced by name, ensure they remain
# https://github.com/tuskyapp/Tusky/issues/3161
-keep class * extends androidx.preference.PreferenceFragmentCompat
