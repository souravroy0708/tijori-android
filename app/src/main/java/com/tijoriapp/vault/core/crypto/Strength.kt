package com.tijoriapp.vault.core.crypto

import kotlin.math.log2

/**
 * Conservative password strength estimate. It deliberately under-scores passwords built from
 * common patterns (years, keyboard runs, repeated chars, well-known passwords, Indian name/number combos).
 */
object Strength {
    enum class Level { VeryWeak, Weak, Fair, Strong, VeryStrong }

    data class Result(val bits: Int, val level: Level, val warning: String?)

    private val COMMON = setOf(
        "password", "password1", "password123", "123456", "12345678", "123456789", "1234567890", "qwerty",
        "qwerty123", "abc123", "111111", "000000", "iloveyou", "admin", "welcome", "india123", "india@123",
        "p@ssw0rd", "passw0rd", "letmein", "monkey", "dragon", "sunshine", "princess", "football", "cricket",
        "sachin", "krishna", "ganesh", "jaimatadi", "omsairam", "hanuman", "radhe", "bharat", "hindustan",
    )
    private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890", "abcdefghijklmnopqrstuvwxyz")

    fun check(password: String, wordlist: Passphrase? = null): Result {
        val s = password
        if (s.isEmpty()) return Result(0, Level.VeryWeak, null)
        val lower = s.lowercase()
        if (lower in COMMON || lower.trimEnd { it.isDigit() || !it.isLetterOrDigit() } in COMMON)
            return Result(8, Level.VeryWeak, "This is one of the most common passwords")

        // Passphrase made of wordlist words: score per word, which is honest for generated ones.
        val parts = s.split(Regex("[\\s\\-_.]+")).filter { it.isNotEmpty() }
        if (wordlist != null && parts.size >= 3 && parts.count { wordlist.isWord(it.trimEnd { c -> c.isDigit() }) } == parts.size) {
            val bits = (parts.size * wordlist.bitsPerWord).toInt()
            return Result(bits, levelFor(bits), if (parts.size < 5) "Add more words" else null)
        }

        var pool = 0
        if (s.any { it.isLowerCase() }) pool += 26
        if (s.any { it.isUpperCase() }) pool += 26
        if (s.any { it.isDigit() }) pool += 10
        if (s.any { !it.isLetterOrDigit() }) pool += 33
        if (s.any { it.code > 127 }) pool += 40
        var effectiveLen = s.length.toDouble()
        var warning: String? = null

        // Repeats like "aaaa" or "abcabc"
        val distinct = s.toSet().size
        if (distinct <= s.length / 3) { effectiveLen = distinct * 1.5; warning = "Too many repeated characters" }
        // Keyboard or alphabet runs of 4+
        for (row in ROWS) for (i in 0..row.length - 4) {
            val run = row.substring(i, i + 4)
            if (lower.contains(run) || lower.contains(run.reversed())) { effectiveLen -= 3; warning = warning ?: "Avoid keyboard patterns like '$run'" }
        }
        // Years and phone-number style tails
        if (Regex("(19|20)\\d{2}").containsMatchIn(s)) { effectiveLen -= 2; warning = warning ?: "Years are easy to guess" }
        if (Regex("\\d{10}").containsMatchIn(s)) { effectiveLen -= 6; warning = warning ?: "Phone numbers are easy to guess" }
        // Word + digits + symbol at the end (Name@123) is the #1 human pattern
        if (Regex("^[A-Za-z]+[@#!$]?\\d{1,4}[@#!$]?$").matches(s)) { effectiveLen = minOf(effectiveLen, 6.0); warning = warning ?: "Name@123-style passwords are guessed first" }

        val bits = (effectiveLen.coerceAtLeast(1.0) * log2(pool.coerceAtLeast(10).toDouble())).toInt().coerceAtLeast(1)
        if (s.length < 10) warning = warning ?: "Use at least 12 characters"
        return Result(bits, levelFor(bits), warning)
    }

    fun levelFor(bits: Int): Level = when {
        bits < 28 -> Level.VeryWeak
        bits < 45 -> Level.Weak
        bits < 60 -> Level.Fair
        bits < 80 -> Level.Strong
        else -> Level.VeryStrong
    }
}
