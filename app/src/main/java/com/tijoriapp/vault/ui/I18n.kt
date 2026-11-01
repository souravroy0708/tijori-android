package com.tijoriapp.vault.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Lang {
    var current by mutableStateOf("en")

    private val HI = mapOf(
        // Navigation & Rooms
        "Rooms" to "तिजोरी रूम्स",
        "Nominee Kit" to "नॉमिनी किट",
        "Trust Page" to "ट्रस्ट पेज",
        "Settings" to "सेटिंग्स",
        "Everyday" to "रोज़मर्रा",
        "Money" to "बैंक और धन",
        "Legacy" to "परिवार और वसीयत",

        // Actions & Headlines
        "Chaabi sirf aapke paas." to "चाबी सिर्फ़ आपके पास।",
        "Unlock Vault" to "तिजोरी खोलें",
        "Add New Login" to "नया लॉगिन जोड़ें",
        "Lock Vault" to "तिजोरी लॉक करें",
        "Export Emergency Kit PDF" to "इमरजेंसी किट PDF डाउनलोड करें",
        "Export Nominee Cards PDF" to "नॉमिनी कार्ड PDF बनाएं",
        "Master Passphrase" to "मास्टर पासफ़्रेज़",
        "Secret Key" to "सीक्रेट की (128-बिट)",
        "Search your vault…" to "तिजोरी में खोजें…",
        "Save" to "सेव करें",
        "Cancel" to "रद्द करें",
        "Reveal" to "देखें",
        "Copy" to "कॉपी करें",
        "Copied! Clears in 30s" to "कॉपी हो गया! 30 सेकंड में हटेगा",
    )

    fun t(en: String): String = if (current == "hi") HI[en] ?: en else en
}

fun t(en: String): String = Lang.t(en)
