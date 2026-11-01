# Tijori release rules. Obfuscate and strip logging.
-keep class com.tijoriapp.vault.MainActivity
-keep class com.tijoriapp.vault.autofill.TijoriAutofillService
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
