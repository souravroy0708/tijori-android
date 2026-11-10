package com.tijoriapp.vault.core.crypto

import java.security.SecureRandom

/**
 * Shamir's Secret Sharing over GF(2^8) (AES field, polynomial 0x11B, generator 3).
 * Any k shares rebuild the secret; k-1 shares reveal nothing about it.
 */
object Shamir {
    private const val POLY = 0x11B
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = x xor (x shl 1) // multiply by 3 (x + 1), a generator for 0x11B
            if (x and 0x100 != 0) x = x xor POLY
        }
    }

    private fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]
    private fun div(a: Int, b: Int): Int {
        require(b != 0)
        return if (a == 0) 0 else EXP[(LOG[a] - LOG[b] + 255) % 255]
    }

    class Share(val index: Int, val data: ByteArray)

    fun split(secret: ByteArray, k: Int, n: Int, rng: SecureRandom = Bytes.rng): List<Share> {
        require(k in 2..n && n in 2..16) { "Need 2 <= k <= n <= 16" }
        val out = Array(n) { ByteArray(secret.size) }
        val coeffs = IntArray(k)
        for (pos in secret.indices) {
            coeffs[0] = secret[pos].toInt() and 0xFF
            for (c in 1 until k) coeffs[c] = rng.nextInt(256)
            for (x in 1..n) {
                // Horner's rule: evaluate the polynomial at x
                var y = 0
                for (c in k - 1 downTo 0) y = mul(y, x) xor coeffs[c]
                out[x - 1][pos] = y.toByte()
            }
        }
        coeffs.fill(0)
        return (1..n).map { Share(it, out[it - 1]) }
    }

    fun combine(shares: List<Share>): ByteArray {
        val used = shares.distinctBy { it.index }
        require(used.size >= 2) { "Need at least two different shares" }
        val len = used[0].data.size
        require(used.all { it.data.size == len && it.index in 1..255 }) { "Shares don't match" }
        val secret = ByteArray(len)
        val weights = IntArray(used.size) { j ->
            var num = 1
            var den = 1
            for (m in used.indices) if (m != j) {
                num = mul(num, used[m].index)
                den = mul(den, used[m].index xor used[j].index)
            }
            div(num, den)
        }
        for (pos in 0 until len) {
            var v = 0
            for (j in used.indices) v = v xor mul(used[j].data[pos].toInt() and 0xFF, weights[j])
            secret[pos] = v.toByte()
        }
        return secret
    }
}

/**
 * A printable nominee card code: "TJN-XXXXXX-XXXXXX-…".
 * Layout: version(1) | setId(4) | k(1) | index(1) | n(1) | data | checksum(2).
 * The set id stops someone from mixing cards from two different card sets,
 * and the checksum catches typing mistakes card by card.
 */
data class NomineeCard(val setId: ByteArray, val k: Int, val n: Int, val share: Shamir.Share) {
    fun code(): String {
        val body = byteArrayOf(VERSION) + setId + byteArrayOf(k.toByte(), share.index.toByte(), n.toByte()) + share.data
        val withCheck = body + Bytes.sha256("tijori-share".toByteArray(), body).copyOf(2)
        return PREFIX + "-" + Base32.Crockford.encode(withCheck).chunked(6).joinToString("-")
    }

    val setLabel: String get() = Base32.Crockford.encode(setId).take(6)

    companion object {
        const val PREFIX = "TJN"
        private const val VERSION: Byte = 1

        sealed interface Parsed {
            data class Ok(val card: NomineeCard) : Parsed
            data object Malformed : Parsed
            data object Typo : Parsed
        }

        fun parse(input: String): Parsed {
            var clean = Base32.Crockford.normalize(input)
            if (clean.startsWith(PREFIX)) clean = clean.removePrefix(PREFIX)
            if (clean.length < 20) return Parsed.Malformed
            val bytes = Base32.Crockford.decode(clean, clean.length * 5 / 8) ?: return Parsed.Malformed
            if (bytes.size < 10 || bytes[0] != VERSION) return Parsed.Malformed
            val body = bytes.copyOf(bytes.size - 2)
            val check = bytes.copyOfRange(bytes.size - 2, bytes.size)
            if (!Bytes.equal(check, Bytes.sha256("tijori-share".toByteArray(), body).copyOf(2))) return Parsed.Typo
            val setId = body.copyOfRange(1, 5)
            val k = body[5].toInt() and 0xFF
            val index = body[6].toInt() and 0xFF
            val n = body[7].toInt() and 0xFF
            if (k < 2 || index < 1 || n < k) return Parsed.Malformed
            return Parsed.Ok(NomineeCard(setId, k, n, Shamir.Share(index, body.copyOfRange(8, body.size))))
        }

        fun makeSet(secret: ByteArray, k: Int, n: Int): List<NomineeCard> {
            val setId = Bytes.random(4)
            return Shamir.split(secret, k, n).map { NomineeCard(setId, k, n, it) }
        }
    }
}
