package com.tijoriapp.vault.core.vault

import com.tijoriapp.vault.core.crypto.Passphrase
import com.tijoriapp.vault.core.crypto.Strength
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Offline security check: weak, reused and old passwords, plus documents and cards about to expire. */
object Health {
    enum class Issue { Weak, Reused, Old, Expiring, Expired, No2FA }

    data class Finding(val item: VaultItem, val issue: Issue, val detail: String)

    data class Report(val score: Int, val findings: List<Finding>, val passwords: Int) {
        fun of(issue: Issue) = findings.filter { it.issue == issue }
        val needsAttention: Int get() = findings.filter { it.issue != Issue.No2FA }.map { it.item.id }.distinct().size
    }

    private const val OLD_DAYS = 365L
    private const val EXPIRY_WARN_DAYS = 45L

    fun analyze(items: List<VaultItem>, today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis(), words: Passphrase? = null): Report {
        val out = ArrayList<Finding>()
        val withPw = items.filter { !it.password.isNullOrBlank() }
        val byPassword = withPw.groupBy { it.password!! }

        for (item in withPw) {
            val pw = item.password!!
            val s = Strength.check(pw, words)
            if (s.level <= Strength.Level.Weak) out += Finding(item, Issue.Weak, s.warning ?: "Easy to guess")
            val twins = byPassword.getValue(pw).size
            if (twins > 1) out += Finding(item, Issue.Reused, "Same password as ${twins - 1} other ${if (twins == 2) "item" else "items"}")
            val ageDays = (now - item.passwordChangedAt) / 86_400_000L
            if (item.room == Room.MONEY && ageDays > OLD_DAYS) out += Finding(item, Issue.Old, "Not changed in ${ageDays / 30} months")
            // A gentle tip only (not counted in the score): many people use SMS codes, which we can't see.
            if (item.type == ItemType.EMAIL && item.first(FieldKind.TOTP) == null)
                out += Finding(item, Issue.No2FA, "Turn on 2-step verification for your email")
        }
        for (item in items) for (f in item.fields.filter { it.kind == FieldKind.EXPIRY && it.value.isNotBlank() }) {
            val d = Dates.parse(f.value) ?: continue
            val days = ChronoUnit.DAYS.between(today, d)
            when {
                days < 0 -> out += Finding(item, Issue.Expired, "${f.label} passed on ${f.value}")
                days <= EXPIRY_WARN_DAYS -> out += Finding(item, Issue.Expiring, "${f.label}: $days days left")
            }
        }

        // Score: each password-bearing item can lose up to 100/N points; expiries cost a little each.
        val n = withPw.size.coerceAtLeast(1)
        var penalty = 0.0
        withPw.forEach { item ->
            val issues = out.filter { it.item.id == item.id }.map { it.issue }.toSet()
            var p = 0.0
            if (Issue.Weak in issues) p += 0.6
            if (Issue.Reused in issues) p += 0.5
            if (Issue.Old in issues) p += 0.2
            penalty += p.coerceAtMost(1.0) * 100.0 / n
        }
        penalty += out.count { it.issue == Issue.Expired } * 4 + out.count { it.issue == Issue.Expiring } * 2
        val score = if (items.isEmpty()) 100 else (100 - penalty).toInt().coerceIn(0, 100)
        return Report(score, out, withPw.size)
    }
}
