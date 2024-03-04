# GENERAL OPTIONS

-allowaccessmodification

# Preserve some attributes that may be required for reflection.
-keepattributes RuntimeVisible*Annotations, AnnotationDefault

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
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

# TODO check if these 3 types are processed with Gson, if they are, annotate them
-keepclassmembers class com.keylesspalace.tusky.components.conversation.ConversationAccountEntity { *; }
-keepclassmembers class com.keylesspalace.tusky.db.DraftAttachment { *; }

-keep enum com.keylesspalace.tusky.db.DraftAttachment$Type {
    public *;
}

# Retain generic signatures of classes used in MastodonApi so Retrofit works
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.collections.List
-keep,allowobfuscation,allowshrinking class kotlin.collections.Map
-keep,allowobfuscation,allowshrinking class retrofit2.Call

# https://github.com/square/retrofit/pull/3563
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

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
