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

-keepclassmembers class **.R$* {
    public static <fields>;
}

# TUSKY SPECIFIC OPTIONS

# keep members of our model classes, they are used in json de/serialization
-keepclassmembers class com.keylesspalace.tusky.entity.* { *; }

-keep public enum com.keylesspalace.tusky.entity.*$** {
    **[] $VALUES;
    public *;
}

-keepclassmembers class com.keylesspalace.tusky.components.conversation.ConversationAccountEntity { *; }
-keepclassmembers class com.keylesspalace.tusky.db.DraftAttachment { *; }

-keep enum com.keylesspalace.tusky.db.DraftAttachment$Type {
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

# Retain generic signatures of classes used in MastodonApi so Retrofit works
-keep,allowobfuscation,allowshrinking class io.reactivex.rxjava3.core.Single
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.collections.List
-keep,allowobfuscation,allowshrinking class kotlin.collections.Map
-keep,allowobfuscation,allowshrinking class retrofit2.Call

# https://r8.googlesource.com/r8/+/refs/heads/master/compatibility-faq.md#retrofit
-keepattributes Signature
-keep class kotlin.coroutines.Continuation

# We can remove these rules after updating to OkHttp 4.10.1
# https://github.com/square/okhttp/blob/339732e3a1b78be5d792860109047f68a011b5eb/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro#L11-L14
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

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
