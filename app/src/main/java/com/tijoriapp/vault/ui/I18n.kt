package com.tijoriapp.vault.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-app English / हिन्दी switch (people often want Hindi for the family-facing parts only).
 * Untranslated strings fall back to English.
 */
object Lang {
    var current by mutableStateOf("en")

    private val HI = mapOf(
        "Vault" to "तिजोरी", "Security" to "सुरक्षा", "Family" to "परिवार", "Settings" to "सेटिंग्स",
        "Everyday" to "रोज़मर्रा", "Money" to "पैसा",
        "Namaste" to "नमस्ते",
        "Chaabi sirf aapke paas" to "चाबी सिर्फ़ आपके पास",
        "Create my Tijori" to "मेरी तिजोरी बनाएँ",
        "Restore from backup" to "बैकअप से वापस लाएँ",
        "I'm a nominee" to "मैं नॉमिनी हूँ",
        "Unlock" to "खोलें",
        "Unlock with fingerprint" to "फ़िंगरप्रिंट से खोलें",
        "Tijori is locked" to "तिजोरी बंद है",
        "Master passphrase" to "मास्टर पासफ़्रेज़",
        "Search your vault" to "तिजोरी में खोजें",
        "Add" to "जोड़ें", "Save" to "सेव करें", "Cancel" to "रद्द करें", "Delete" to "हटाएँ", "Edit" to "बदलें",
        "Copy" to "कॉपी", "Show" to "दिखाएँ", "Hide" to "छिपाएँ", "Done" to "हो गया", "Continue" to "आगे बढ़ें", "Back" to "पीछे",
        "Lock" to "लॉक करें",
        "Security score" to "सुरक्षा स्कोर",
        "Needs attention" to "ध्यान दें",
        "Items" to "आइटम",
        "Favourites" to "पसंदीदा",
        "Recently used" to "हाल में इस्तेमाल",
        "All items" to "सभी आइटम",
        "Money room is locked" to "पैसा रूम बंद है",
        "Open Money room" to "पैसा रूम खोलें",
        "Locks in" to "बंद होगा",
        "Weak passwords" to "कमज़ोर पासवर्ड",
        "Reused passwords" to "दोहराए गए पासवर्ड",
        "Expiring soon" to "जल्द ख़त्म हो रहे",
        "Expired" to "ख़त्म हो चुके",
        "Nominee cards" to "नॉमिनी कार्ड",
        "Family package" to "फ़ैमिली पैकेज",
        "Emergency Kit" to "इमरजेंसी किट",
        "Backup" to "बैकअप",
        "Language" to "भाषा",
        "Theme" to "थीम",
        "Auto-lock" to "ऑटो-लॉक",
        "Generate password" to "पासवर्ड बनाएँ",
        "Copied. Clears in %s s" to "कॉपी हुआ। %s सेकंड में मिट जाएगा",
        "Nothing here yet" to "अभी यहाँ कुछ नहीं है",
        "Your vault, only on this phone" to "आपकी तिजोरी, सिर्फ़ इस फ़ोन पर",
    )

    fun t(en: String): String = if (current == "hi") HI[en] ?: en else en
}

fun t(en: String): String = Lang.t(en)
fun t(en: String, vararg args: Any): String = Lang.t(en).replace("%s", args.firstOrNull()?.toString() ?: "")
