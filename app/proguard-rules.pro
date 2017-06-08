# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/andrew/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

## for okhttp
-dontwarn okio.**

## for picasso
-dontwarn com.squareup.okhttp.**

## for retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-keep class com.keylesspalace.tusky.entity.** { *; }

-keep public enum com.keylesspalace.tusky.entity.*$** {
    **[] $VALUES;
    public *;
}


# preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable

# remove all logging from production apk
-assumenosideeffects class android.util.Log {
    public static *** getStackTraceString(...);
    public static *** d(...);
    public static *** w(...);
    public static *** v(...);
    public static *** i(...);
}

# for jsoup
-keep public class org.jsoup.** {
public *;
}