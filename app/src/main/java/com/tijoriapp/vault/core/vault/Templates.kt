package com.tijoriapp.vault.core.vault

/** India-first templates. Labels are what people actually see on bank and government sites. */
object Templates {
    data class Template(val type: ItemType, val fields: List<Pair<String, FieldKind>>, val tip: String, val defaults: Map<String, String> = emptyMap())

    private fun f(label: String, kind: FieldKind) = label to kind

    val all: List<Template> = listOf(
        Template(ItemType.LOGIN, listOf(f("Username", FieldKind.USERNAME), f("Password", FieldKind.PASSWORD), f("Website", FieldKind.URL), f("2FA code", FieldKind.TOTP)),
            "Add the website so autofill only ever works on the real site."),
        Template(ItemType.EMAIL, listOf(f("Email", FieldKind.EMAIL), f("Password", FieldKind.PASSWORD), f("Recovery phone", FieldKind.PHONE), f("Website", FieldKind.URL), f("2FA code", FieldKind.TOTP)),
            "Your email resets every other password. Give it the strongest password you have."),
        Template(ItemType.NET_BANKING, listOf(f("Bank", FieldKind.TEXT), f("Customer ID", FieldKind.USERNAME), f("Login password", FieldKind.PASSWORD),
            f("Profile / transaction password", FieldKind.PASSWORD), f("Account number", FieldKind.NUMBER), f("IFSC", FieldKind.TEXT), f("Website", FieldKind.URL)),
            "Lives in the Money room: it needs your fingerprint every time and locks itself after a minute."),
        Template(ItemType.UPI, listOf(f("App", FieldKind.TEXT), f("UPI ID", FieldKind.USERNAME), f("Linked bank", FieldKind.TEXT), f("UPI PIN hint", FieldKind.PIN_HINT)),
            "Never save your real UPI PIN anywhere. Save a hint only you understand."),
        Template(ItemType.CARD, listOf(f("Bank", FieldKind.TEXT), f("Name on card", FieldKind.TEXT), f("Last 4 digits", FieldKind.NUMBER),
            f("Expiry (MM/YY)", FieldKind.EXPIRY), f("PIN hint", FieldKind.PIN_HINT), f("Customer care", FieldKind.PHONE)),
            "Tijori stores a PIN hint, not your PIN or CVV. If the card is lost, call customer care first."),
        Template(ItemType.INVESTMENT, listOf(f("Scheme", FieldKind.TEXT), f("Account / UAN / PRAN", FieldKind.NUMBER), f("Bank or office", FieldKind.TEXT),
            f("Portal password", FieldKind.PASSWORD), f("Nominee", FieldKind.TEXT), f("Maturity date", FieldKind.DATE), f("Website", FieldKind.URL)),
            "Add the nominee name. Families often don't know these accounts exist."),
        Template(ItemType.TAX, listOf(f("PAN", FieldKind.USERNAME), f("Password", FieldKind.PASSWORD), f("Website", FieldKind.URL)),
            "Tip: AIS and Form 26AS are on the same portal.", defaults = mapOf("Website" to "https://eportal.incometax.gov.in")),
        Template(ItemType.ID_DOC, listOf(f("Document", FieldKind.TEXT), f("Number", FieldKind.SECRET), f("Name as printed", FieldKind.TEXT),
            f("Valid until", FieldKind.EXPIRY), f("Original kept at", FieldKind.TEXT)),
            "Aadhaar, PAN, passport, voter ID or driving licence. Tijori reminds you before it expires."),
        Template(ItemType.INSURANCE, listOf(f("Insurer", FieldKind.TEXT), f("Policy number", FieldKind.NUMBER), f("Sum assured", FieldKind.TEXT),
            f("Premium due", FieldKind.EXPIRY), f("Nominee", FieldKind.TEXT), f("Agent / helpline", FieldKind.PHONE), f("Policy bond kept at", FieldKind.TEXT)),
            "Family room: your nominees can open this with their cards if something happens to you."),
        Template(ItemType.LOCKER, listOf(f("Bank & branch", FieldKind.TEXT), f("Locker number", FieldKind.NUMBER), f("Key kept at", FieldKind.TEXT),
            f("Joint holder", FieldKind.TEXT), f("Will kept with", FieldKind.TEXT)),
            "Write it the way you'd explain it to your family, in plain words."),
        Template(ItemType.LETTER, listOf(f("To", FieldKind.TEXT)),
            "Anything your family should know: who to call first, which accounts exist, your wishes."),
        Template(ItemType.WIFI, listOf(f("Network name", FieldKind.TEXT), f("Password", FieldKind.PASSWORD), f("Router admin password", FieldKind.PASSWORD)),
            "Handy when guests ask."),
        Template(ItemType.NOTE, emptyList(), "Encrypted like everything else. Only titles you can see are on your screen."),
        Template(ItemType.CUSTOM, listOf(f("Username", FieldKind.USERNAME), f("Password", FieldKind.PASSWORD)), "Add any fields you need."),
    )

    fun of(type: ItemType): Template = all.first { it.type == type }

    fun newItem(type: ItemType): VaultItem {
        val t = of(type)
        return VaultItem(room = type.room, type = type, title = "", fields = t.fields.map { (label, kind) -> Field(label, kind, t.defaults[label] ?: "") })
    }
}
