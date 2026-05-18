# ProGuard / R8 rules for :app release builds.
#
# AGP 9 ships full-mode R8 by default; the rules below cover the libraries we
# rely on at runtime that R8 cannot fully analyse statically (reflection-based
# initialization, service loaders, serializer companion lookup, etc.).
# See: https://developer.android.com/build/shrink-code

# --- Sentry: keep source-file + line-number information so symbolicated
# stack traces remain useful. Pair with -renamesourcefileattribute SourceFile
# so the mapping.txt produced by R8 can deobfuscate frames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ----------------------------------------------------
# Standard rules from the kotlinx.serialization README — required because the
# library performs reflective lookup of `<Type>$Companion.serializer()`.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep @Serializable classes and their generated companions/serializer fields.
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keep class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the app's @Serializable DTOs and their nested $Companion / $$serializer
# classes — covers domain + remote model packages used by Ktor + Coil network.
-keep,includedescriptorclasses class pm.bam.gamedeals.**$$serializer { *; }
-keepclassmembers class pm.bam.gamedeals.** {
    *** Companion;
}
-keepclasseswithmembers class pm.bam.gamedeals.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor ---------------------------------------------------------------------
# Ktor's engines + plugins are looked up reflectively / via ServiceLoader.
# These -dontwarn rules silence R8 warnings about optional engines we don't
# ship (we use OkHttp on Android); -keep keeps the engines we DO ship.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.client.plugins.** { *; }
-dontwarn io.ktor.network.sockets.**
-dontwarn io.ktor.client.engine.curl.**
-dontwarn io.ktor.client.engine.cio.**
-dontwarn io.ktor.client.engine.jetty.**
-dontwarn io.ktor.client.engine.java.**
-dontwarn io.ktor.client.engine.android.**
-dontwarn io.ktor.client.engine.darwin.**

# --- OkHttp / Okio (transitive via Ktor-OkHttp) -------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Koin ---------------------------------------------------------------------
# Koin 4 is mostly DSL-driven and does not need reflection-based keep rules,
# but the generated ViewModel/scope helpers occasionally reference reflective
# constructors on KClass. Suppress optional warnings.
-dontwarn org.koin.**

# --- Sentry-KMP ---------------------------------------------------------------
# Sentry serialises its own protocol classes + reads them back; keep the
# model surface area. The androidx-startup initializer also references
# Sentry classes via Manifest <meta-data>, so keep its bootstrapping types.
-keep class io.sentry.** { *; }
-keep interface io.sentry.** { *; }
-dontwarn io.sentry.**

# --- Coil3 --------------------------------------------------------------------
# Coil registers decoders / fetchers via ServiceLoader entries packaged in
# META-INF/services. R8 keeps the META-INF entries, but the implementations
# themselves must not be stripped.
-keep class coil3.** { *; }
-keep interface coil3.** { *; }
-dontwarn coil3.**

# --- Kotlin / reflection ------------------------------------------------------
# kotlin-reflect is pulled in transitively (Koin, kotlinx-serialization
# fallbacks). Keep the core reflection surface to avoid no-class-def errors
# when libraries probe for Metadata.
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# --- AndroidX Compose runtime stable rules ------------------------------------
# Compose ships consumer rules with the artifact, but keep our own
# @Composable entry points to avoid surprises in dynamic feature graphs.
-keep class androidx.compose.runtime.** { *; }
