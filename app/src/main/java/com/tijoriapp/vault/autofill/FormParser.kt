package com.tijoriapp.vault.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/**
 * Finds the login fields on a screen. Prefers what the app declares (autofill hints, HTML input types),
 * then falls back to input types and ids. Only trusts a web domain when it comes from a known browser.
 */
object FormParser {
    data class Form(
        val packageName: String,
        /** Only set for trusted browsers, so a random app can't claim to be "hdfcbank.com". */
        val webDomain: String?,
        val username: AutofillId?,
        val password: AutofillId?,
        val usernameValue: String? = null,
        val passwordValue: String? = null,
    ) {
        val ids: List<AutofillId> get() = listOfNotNull(username, password)
        val isBrowser: Boolean get() = packageName in TRUSTED_BROWSERS
    }

    /** Browsers that report the real page domain to autofill services. */
    val TRUSTED_BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "com.microsoft.emmx", "com.brave.browser", "com.brave.browser_beta", "com.sec.android.app.sbrowser",
        "com.opera.browser", "com.opera.mini.native", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        "com.kiwibrowser.browser", "com.mi.globalbrowser", "com.ecosia.android",
    )

    private val USER_WORDS = listOf("user", "email", "login", "mobile", "phone", "customer", "account", "userid", "member", "pan", "uan")
    private val PASS_WORDS = listOf("pass", "pwd", "pin", "secret")

    fun parse(structure: AssistStructure, ownPackage: String): Form? {
        val pkg = structure.activityComponent?.packageName ?: return null
        if (pkg == ownPackage) return null
        var domain: String? = null
        var user: AutofillId? = null
        var pass: AutofillId? = null
        var userVal: String? = null
        var passVal: String? = null
        var lastText: AutofillId? = null
        var lastTextVal: String? = null

        fun visit(n: AssistStructure.ViewNode) {
            if (domain == null && !n.webDomain.isNullOrBlank()) domain = n.webDomain
            val id = n.autofillId
            if (id != null && n.importantForAutofill != View.IMPORTANT_FOR_AUTOFILL_NO && n.importantForAutofill != View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS && n.autofillType == View.AUTOFILL_TYPE_TEXT) {
                val kind = classify(n)
                val value = n.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                when (kind) {
                    Kind.Password -> if (pass == null) { pass = id; passVal = value; if (user == null && lastText != null) { user = lastText; userVal = lastTextVal } }
                    Kind.Username -> if (user == null) { user = id; userVal = value }
                    Kind.Other -> { lastText = id; lastTextVal = value }
                    Kind.Skip -> {}
                }
            }
            for (i in 0 until n.childCount) visit(n.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
        if (user == null && pass == null) return null
        val trustedDomain = if (pkg in TRUSTED_BROWSERS) domain?.lowercase()?.removePrefix("www.") else null
        return Form(pkg, trustedDomain, user, pass, userVal, passVal)
    }

    private enum class Kind { Username, Password, Other, Skip }

    private fun classify(n: AssistStructure.ViewNode): Kind {
        n.autofillHints?.forEach { h ->
            val x = h.lowercase()
            if (x.contains("password") || x == View.AUTOFILL_HINT_PASSWORD.lowercase()) return Kind.Password
            if (x.contains("username") || x.contains("email") || x.contains("phone") || x == View.AUTOFILL_HINT_USERNAME.lowercase()) return Kind.Username
            if (x.contains("otp") || x.contains("sms") || x.contains("creditcard") || x.contains("postal") || x.contains("name")) return Kind.Skip
        }
        n.htmlInfo?.let { html ->
            if (html.tag.equals("input", true)) {
                val attrs = html.attributes?.associate { it.first.lowercase() to (it.second ?: "").lowercase() } ?: emptyMap()
                val type = attrs["type"] ?: "text"
                val ac = attrs["autocomplete"] ?: ""
                if (type == "password" || ac.contains("password")) return Kind.Password
                if (type == "email" || ac.contains("username") || ac.contains("email")) return Kind.Username
                if (type == "hidden" || type == "submit" || type == "checkbox" || type == "radio" || ac.contains("one-time-code")) return Kind.Skip
                val nameish = (attrs["name"] ?: "") + " " + (attrs["id"] ?: "")
                if (PASS_WORDS.any { nameish.contains(it) }) return Kind.Password
                if (USER_WORDS.any { nameish.contains(it) }) return Kind.Username
            }
        }
        val it = n.inputType
        val cls = it and InputType.TYPE_MASK_CLASS
        val variation = it and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) return Kind.Password
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return Kind.Password
        if (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) return Kind.Username
        val hay = listOfNotNull(n.idEntry, n.hint, n.contentDescription?.toString()).joinToString(" ").lowercase()
        if (PASS_WORDS.any { hay.contains(it) } && !hay.contains("otp")) return Kind.Password
        if (USER_WORDS.any { hay.contains(it) }) return Kind.Username
        return if (n.className?.contains("EditText") == true || (n.htmlInfo?.tag.equals("input", true))) Kind.Other else Kind.Skip
    }
}
