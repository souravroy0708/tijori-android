# Tijori release rules.
# Strip debug/verbose/info logging from release builds (we never log secrets, but belt and braces).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Bouncy Castle: we call Argon2BytesGenerator directly (no reflection, no JCA provider).
# Unused parts of the jar reference desktop-only APIs; silence those.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# Autofill service and activities are referenced from the manifest (kept automatically by AAPT rules).
