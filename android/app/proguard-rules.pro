# Netty / Ktor specific rules
-dontwarn io.netty.**
-dontwarn io.ktor.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.slf4j.impl.**
-dontwarn reactor.blockhound.**
-dontwarn java.lang.management.**

# Keep Ktor/Netty classes that are used via reflection
-keep class io.netty.** { *; }
-keep class io.ktor.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**