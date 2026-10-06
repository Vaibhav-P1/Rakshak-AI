# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguard-android-optimize.txt in build.gradle.

# Keep Room entities
-keep class com.safety.rakshak.data.** { *; }

# Keep Retrofit and Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Remove verbose/debug/info logging from release builds. Takes effect once R8 is
# enabled (Phase 5). Warnings and errors are kept; they only carry exception types.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
