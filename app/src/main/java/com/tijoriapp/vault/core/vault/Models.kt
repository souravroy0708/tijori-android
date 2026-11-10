package com.tijoriapp.vault.core.vault

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Three rooms, three independent random keys:
 * - EVERYDAY opens with the vault.
 * - MONEY needs a fresh fingerprint or passphrase each time and re-locks after a short idle time.
 * - LEGACY opens with the vault, and your family can open it with k nominee cards.
 */
enum class Room(val en: String, val hi: String) {
    EVERYDAY("Everyday", "रोज़मर्रा"),
    MONEY("Money", "पैसा"),
    LEGACY("Family", "परिवार"),
}

enum class FieldKind(val secret: Boolean) {
    TEXT(false), USERNAME(false), EMAIL(false), PHONE(false), NUMBER(false), URL(false),
    PASSWORD(true), PIN_HINT(false), SECRET(true), NOTE(false), TOTP(true), EXPIRY(false), DATE(false),
}

data class Field(
    val label: String,
    val kind: FieldKind,
    val value: String,
    val id: String = UUID.randomUUID().toString(),
) {
    val isSecret: Boolean get() = kind.secret
}

enum class ItemType(val en: String, val hi: String, val room: Room) {
    LOGIN("Login", "लॉगिन", Room.EVERYDAY),
    EMAIL("Email account", "ईमेल खाता", Room.EVERYDAY),
    WIFI("Wi-Fi", "वाई-फ़ाई", Room.EVERYDAY),
    NOTE("Secure note", "सुरक्षित नोट", Room.EVERYDAY),
    ID_DOC("ID document", "पहचान पत्र", Room.EVERYDAY),
    NET_BANKING("Net banking", "नेट बैंकिंग", Room.MONEY),
    UPI("UPI app", "UPI ऐप", Room.MONEY),
    CARD("Bank card", "बैंक कार्ड", Room.MONEY),
    INVESTMENT("PPF / EPF / NPS / MF", "निवेश खाता", Room.MONEY),
    TAX("Income tax", "इनकम टैक्स", Room.MONEY),
    INSURANCE("Insurance", "बीमा", Room.LEGACY),
    LOCKER("Locker & will", "लॉकर व वसीयत", Room.LEGACY),
    LETTER("Letter to family", "परिवार के लिए पत्र", Room.LEGACY),
    CUSTOM("Other", "अन्य", Room.EVERYDAY),
}

data class VaultItem(
    val id: String = UUID.randomUUID().toString(),
    val room: Room,
    val type: ItemType,
    val title: String,
    val fields: List<Field> = emptyList(),
    val notes: String = "",
    val favorite: Boolean = false,
    /** Android package names allowed to autofill this item (exact match only: no fuzzy matching). */
    val apps: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val passwordChangedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
) {
    fun first(kind: FieldKind): Field? = fields.firstOrNull { it.kind == kind && it.value.isNotBlank() }
    val password: String? get() = first(FieldKind.PASSWORD)?.value
    val username: String? get() = (first(FieldKind.USERNAME) ?: first(FieldKind.EMAIL) ?: first(FieldKind.PHONE))?.value
    val website: String? get() = first(FieldKind.URL)?.value

    /** One line under the title in lists. Never shows a secret. */
    val subtitle: String get() = username
        ?: fields.firstOrNull { !it.isSecret && it.kind != FieldKind.NOTE && it.value.isNotBlank() }?.let { "${it.label}: ${it.value}" }
        ?: type.en

    /** Website hosts this item can fill on, e.g. "netbanking.hdfcbank.com". */
    val hosts: List<String> get() = fields.filter { it.kind == FieldKind.URL }.mapNotNull { Urls.host(it.value) }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return title.lowercase().contains(q) || type.en.lowercase().contains(q) ||
            fields.any { !it.isSecret && it.kind != FieldKind.NOTE && it.value.lowercase().contains(q) }
    }
}

object Urls {
    /** "https://NetBanking.hdfcbank.com/login" -> "netbanking.hdfcbank.com". Null for app links or junk. */
    fun host(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.contains(' ')) return null
        if ("://" in s) {
            val scheme = s.substringBefore("://")
            if (scheme != "http" && scheme != "https") return null
            s = s.substringAfter("://")
        }
        s = s.substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':')
        if (!s.contains('.') || s.startsWith('.') || s.endsWith('.')) return null
        return s.removePrefix("www.")
    }

    /**
     * Anti-phishing rule: a saved host fills on the same host or its subdomains, never on look-alikes.
     * "hdfcbank.com" fills on "netbanking.hdfcbank.com" but never on "hdfcbank.com.evil.in".
     */
    fun sameSite(saved: String, visited: String): Boolean {
        val a = saved.removePrefix("www.")
        val b = visited.lowercase().removePrefix("www.")
        return a == b || b.endsWith(".$a")
    }
}

object Dates {
    /** Accepts "08/29", "08/2029", "2029-08", "2029-08-31", "31/08/2029". Month-only means the month's last day. */
    fun parse(text: String): LocalDate? {
        val s = text.trim()
        return runCatching {
            when {
                Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s) -> LocalDate.parse(s)
                Regex("^\\d{4}-\\d{2}$").matches(s) -> YearMonth.parse(s).atEndOfMonth()
                Regex("^\\d{1,2}/\\d{1,2}/\\d{4}$").matches(s) -> s.split('/').let { LocalDate.of(it[2].toInt(), it[1].toInt(), it[0].toInt()) }
                Regex("^\\d{1,2}/\\d{4}$").matches(s) -> s.split('/').let { YearMonth.of(it[1].toInt(), it[0].toInt()).atEndOfMonth() }
                Regex("^\\d{1,2}/\\d{2}$").matches(s) -> s.split('/').let { YearMonth.of(2000 + it[1].toInt(), it[0].toInt()).atEndOfMonth() }
                else -> null
            }
        }.getOrNull()
    }
}
