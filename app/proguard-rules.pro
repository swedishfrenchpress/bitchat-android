# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
-keep class com.bitchat.android.protocol.** { *; }
-keep class com.bitchat.android.crypto.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# JNA (Java Native Access) rules - Required for CDK FFI
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# CDK FFI bindings - Keep all uniffi generated classes
-keep class uniffi.** { *; }
-keep class uniffi.cdk_ffi.** { *; }
-dontwarn uniffi.**

# Native method preservation
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep SecureIdentityStateManager from being obfuscated to prevent reflection issues
-keep class com.bitchat.android.identity.SecureIdentityStateManager {
    private android.content.SharedPreferences prefs;
    *;
}

# Keep all classes that might use reflection
-keep class com.bitchat.android.favorites.** { *; }
-keep class com.bitchat.android.nostr.** { *; }
-keep class com.bitchat.android.identity.** { *; }

# Arti (Tor) ProGuard rules
-keep class info.guardianproject.arti.** { *; }
-keep class org.torproject.jni.** { *; }
-keepnames class org.torproject.jni.**
-dontwarn info.guardianproject.arti.**
-dontwarn org.torproject.jni.**
