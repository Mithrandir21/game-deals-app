# Add project specific ProGuard rules here.
# R8 full mode is enabled for release (isMinifyEnabled = true, shrinkResources = true).
#
# Most libraries here ship their own consumer R8 rules: Coil3, Sentry (KMP), Ktor, Room (KSP),
# kotlinx.serialization (embedded since 1.x). Koin uses the constructor DSL (no reflection) and needs
# nothing. The rules below are the belt-and-suspenders that those consumer rules don't fully cover —
# reflective serializer resolution for type-safe Navigation and the Room @ProvidedTypeConverter classes
# we instantiate ourselves. Add app-specific keeps below only when a release-build crash proves one is
# missing (validate with a real `assembleRelease` + on-device smoke test, not just a compile).

# ---- kotlinx.serialization ----------------------------------------------------------------------
# Annotations the serialization runtime reads.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep the generated $Companion + serializer() for every @Serializable type (DTOs and nav routes).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Type-safe Navigation -----------------------------------------------------------------------
# Compose type-safe routes are resolved reflectively from their KType, so the route hierarchy and its
# fields must survive shrinking/obfuscation intact.
-keep class pm.bam.gamedeals.common.navigation.Destination
-keep class pm.bam.gamedeals.common.navigation.Destination$** { *; }

# ---- Room ---------------------------------------------------------------------------------------
# Entities/DAOs are KSP-generated and referenced directly. Keep the @ProvidedTypeConverter classes
# (constructed via Koin, not Room) and entity members Room's generated code maps by name.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.ProvidedTypeConverter class * { *; }
