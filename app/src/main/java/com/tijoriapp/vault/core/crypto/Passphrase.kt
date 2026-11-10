package com.tijoriapp.vault.core.crypto

import kotlin.math.log2

/**
 * Generators backed by the EFF large wordlist (7,776 words, log2 = 12.92 bits per word).
 * Uses SecureRandom.nextInt(bound), which is unbiased.
 */
class Passphrase(private val words: List<String>) {
    init { require(words.size >= 2048) { "Wordlist too small: ${words.size}" } }

    val bitsPerWord: Double get() = log2(words.size.toDouble())

    fun words(count: Int = 6, separator: String = "-", capitalize: Boolean = false, addDigit: Boolean = false): String {
        val picked = (1..count).map { words[Bytes.rng.nextInt(words.size)] }
            .map { if (capitalize) it.replaceFirstChar(Char::uppercase) else it }
            .toMutableList()
        if (addDigit) {
            val i = Bytes.rng.nextInt(picked.size)
            picked[i] = picked[i] + Bytes.rng.nextInt(10)
        }
        return picked.joinToString(separator)
    }

    fun wordsEntropy(count: Int, addDigit: Boolean): Int =
        (count * bitsPerWord + if (addDigit) log2(10.0 * count) else 0.0).toInt()

    fun isWord(w: String): Boolean = w.lowercase() in wordSet
    private val wordSet: Set<String> by lazy { words.toHashSet() }

    companion object {
        private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
        private const val DIGITS = "23456789"
        private const val SYMBOLS = "!@#$%&*-_=+?"

        /** Random password with at least one char of each chosen class. Look-alikes (0/O, 1/l/I) removed. */
        fun random(length: Int = 20, digits: Boolean = true, symbols: Boolean = true): String {
            require(length in 8..64)
            val sets = buildList { add(UPPER); add(LOWER); if (digits) add(DIGITS); if (symbols) add(SYMBOLS) }
            val pool = sets.joinToString("")
            val chars = sets.map { it[Bytes.rng.nextInt(it.length)] }.toMutableList()
            while (chars.size < length) chars += pool[Bytes.rng.nextInt(pool.length)]
            // Fisher-Yates with SecureRandom
            for (i in chars.indices.reversed()) {
                val j = Bytes.rng.nextInt(i + 1)
                val t = chars[i]; chars[i] = chars[j]; chars[j] = t
            }
            return chars.joinToString("")
        }

        fun randomEntropy(length: Int, digits: Boolean, symbols: Boolean): Int {
            val pool = UPPER.length + LOWER.length + (if (digits) DIGITS.length else 0) + (if (symbols) SYMBOLS.length else 0)
            return (length * log2(pool.toDouble())).toInt()
        }

        /** Numeric PIN for things like a locker or a phone, never for bank cards (we store hints for those). */
        fun pin(length: Int = 6): String = (1..length).joinToString("") { Bytes.rng.nextInt(10).toString() }
    }
}
