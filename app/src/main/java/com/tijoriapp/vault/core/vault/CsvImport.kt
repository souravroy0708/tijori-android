package com.tijoriapp.vault.core.vault

/**
 * Imports logins from the CSV files other password managers export:
 * Google Password Manager / Chrome, Samsung Pass (via Chrome), Bitwarden, 1Password, LastPass, Firefox, Dashlane, Edge.
 *
 * Runs fully on the phone. The CSV is plain text, so the app tells the user to delete it afterwards.
 * Pure Kotlin: unit-tested without a device.
 */
object CsvImport {
    enum class Source(val label: String) {
        Google("Google Password Manager / Chrome"), Bitwarden("Bitwarden"), OnePassword("1Password"),
        LastPass("LastPass"), Firefox("Firefox"), Generic("CSV file"),
    }

    data class Result(
        val source: Source,
        val items: List<VaultItem>,
        /** Rows already in the vault (same site, username and password). */
        val duplicates: Int,
        /** Rows with no password and no username (folders, cards, notes in other formats). */
        val skipped: Int,
    )

    /** Hosts where an "Email account" item fits better than a plain login. */
    private val EMAIL_HOSTS = setOf(
        "accounts.google.com", "mail.google.com", "gmail.com", "myaccount.google.com",
        "login.live.com", "outlook.live.com", "outlook.com", "login.yahoo.com", "mail.yahoo.com",
        "mail.zoho.com", "accounts.zoho.com", "zoho.com", "rediffmail.com", "mail.rediff.com", "icloud.com", "proton.me", "account.proton.me",
    )

    private val PRETTY = mapOf(
        "accounts.google.com" to "Google (Gmail)", "mail.google.com" to "Gmail", "myaccount.google.com" to "Google account",
        "login.live.com" to "Microsoft (Outlook)", "login.yahoo.com" to "Yahoo Mail",
        "amazon.in" to "Amazon", "flipkart.com" to "Flipkart", "netflix.com" to "Netflix", "facebook.com" to "Facebook",
        "instagram.com" to "Instagram", "linkedin.com" to "LinkedIn", "x.com" to "X (Twitter)", "twitter.com" to "X (Twitter)",
        "github.com" to "GitHub", "irctc.co.in" to "IRCTC", "swiggy.com" to "Swiggy", "zomato.com" to "Zomato",
        "eportal.incometax.gov.in" to "Income tax portal", "myntra.com" to "Myntra", "hotstar.com" to "JioHotstar",
    )

    fun parse(text: String, existing: List<VaultItem> = emptyList(), now: Long = System.currentTimeMillis()): Result {
        val rows = parseCsv(text.removePrefix("\uFEFF")).filter { r -> r.any { it.isNotBlank() } }
        require(rows.isNotEmpty()) { "This file is empty" }
        val head = rows.first().map { it.trim().lowercase() }
        val col = Columns.detect(head) ?: throw IllegalArgumentException(
            "This doesn't look like a password export. Tijori needs a CSV with url, username and password columns.")

        val seen = existing.mapNotNull { it.dedupeKey() }.toHashSet()
        var dupes = 0
        var skipped = 0
        val out = ArrayList<VaultItem>()
        for (r in rows.drop(1)) {
            fun cell(i: Int?) = if (i == null || i >= r.size) "" else r[i].trim()
            val url = cell(col.url)
            val user = cell(col.username)
            val pass = r.getOrNull(col.password ?: -1) ?: "" // passwords keep spaces exactly
            val name = cell(col.name)
            val note = cell(col.note)
            val totp = cell(col.totp)
            val type = cell(col.type).lowercase()
            if (type.isNotEmpty() && type != "login") { skipped++; continue } // Bitwarden cards/notes/identities
            if (pass.isEmpty() && user.isEmpty()) { skipped++; continue }

            val item = toItem(url, user, pass, name, note, totp, col.favorite?.let { cell(it) } ?: "", now)
            val key = item.dedupeKey()
            if (key != null && !seen.add(key)) { dupes++; continue }
            out += item
        }
        return Result(col.source, out, dupes, skipped)
    }

    private fun toItem(url: String, user: String, pass: String, name: String, note: String, totp: String, fav: String, now: Long): VaultItem {
        val androidPkg = androidPackage(url)
        val host = if (androidPkg == null) Urls.host(url) ?: Urls.host("https://$url") else null
        val isEmail = host != null && (host in EMAIL_HOSTS || EMAIL_HOSTS.any { host.endsWith(".$it") })
        val title = name.takeIf { it.isNotBlank() && it != url }
            ?: host?.let { PRETTY[it] ?: prettyHost(it) }
            ?: androidPkg?.let { prettyPackage(it) }
            ?: "Imported login"
        val fields = buildList {
            if (isEmail) add(Field("Email", FieldKind.EMAIL, user)) else add(Field("Username", FieldKind.USERNAME, user))
            add(Field("Password", FieldKind.PASSWORD, pass))
            if (host != null) add(Field("Website", FieldKind.URL, if (url.contains("://")) url else "https://$url"))
            if (totp.isNotBlank()) add(Field("2FA code", FieldKind.TOTP, totp))
        }
        return VaultItem(
            room = Room.EVERYDAY,
            type = if (isEmail) ItemType.EMAIL else ItemType.LOGIN,
            title = title.take(80),
            fields = fields,
            notes = note,
            favorite = fav == "1" || fav.equals("true", ignoreCase = true),
            apps = listOfNotNull(androidPkg),
            createdAt = now, updatedAt = now, passwordChangedAt = now,
        )
    }

    /** Google Password Manager exports app logins as "android://<hash>@com.example.app/". */
    fun androidPackage(url: String): String? {
        if (!url.startsWith("android://")) return null
        val rest = url.removePrefix("android://")
        val pkg = rest.substringAfter('@').substringBefore('/').trim()
        return pkg.takeIf { Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(it) }
    }

    private fun prettyHost(host: String): String {
        val parts = host.split('.')
        // "netbanking.hdfcbank.com" -> "Hdfcbank", "co.in"/"gov.in" style suffixes are skipped.
        val core = when {
            parts.size >= 3 && parts[parts.size - 2] in setOf("co", "gov", "org", "net", "ac", "nic") -> parts[parts.size - 3]
            parts.size >= 2 -> parts[parts.size - 2]
            else -> host
        }
        return core.replaceFirstChar { it.uppercase() }
    }

    private fun prettyPackage(pkg: String): String =
        pkg.split('.').lastOrNull { it !in setOf("android", "app", "mobile", "client", "main") }?.replaceFirstChar { it.uppercase() } ?: pkg

    private fun VaultItem.dedupeKey(): String? {
        val pw = password ?: return null
        val where = hosts.firstOrNull() ?: apps.firstOrNull() ?: title.lowercase()
        return "${where.lowercase()}|${(username ?: "").lowercase()}|$pw"
    }

    private data class Columns(
        val source: Source, val url: Int?, val username: Int?, val password: Int?,
        val name: Int?, val note: Int?, val totp: Int?, val type: Int?, val favorite: Int?,
    ) {
        companion object {
            fun detect(h: List<String>): Columns? {
                fun idx(vararg names: String): Int? = names.firstNotNullOfOrNull { n -> h.indexOf(n).takeIf { it >= 0 } }
                val password = idx("password", "login_password", "pass") ?: return null
                val source = when {
                    "login_password" in h -> Source.Bitwarden
                    "otpauth" in h && "title" in h -> Source.OnePassword
                    "grouping" in h && "extra" in h -> Source.LastPass
                    "httprealm" in h || "formactionorigin" in h -> Source.Firefox
                    h.take(4) == listOf("name", "url", "username", "password") -> Source.Google
                    else -> Source.Generic
                }
                return Columns(
                    source = source,
                    url = idx("url", "login_uri", "website", "web site", "uri", "origin_url"),
                    username = idx("username", "login_username", "user", "email", "login", "user name"),
                    password = password,
                    name = idx("name", "title", "account"),
                    note = idx("note", "notes", "extra", "comment"),
                    totp = idx("totp", "login_totp", "otpauth", "otp"),
                    type = idx("type").takeIf { source == Source.Bitwarden },
                    favorite = idx("favorite", "fav"),
                )
            }
        }
    }

    /** RFC 4180 CSV: quoted fields, doubled quotes, commas and newlines inside quotes, CRLF or LF. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = ArrayList() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
