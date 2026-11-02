package com.tijoriapp.vault.crypto

import java.security.SecureRandom

/**
 * Shamir's Secret Sharing over Galois Field GF(2^8) (Spec Page 7: "The Legacy Key is split with
 * Shamir's secret sharing into n shares; any k rebuild it. Default 2 of 3; options up to 3 of 5.
 * Small GF(256) implementation against public test vectors").
 */
object ShamirSecretSharing {

    // Irreducible polynomial 0x11B (x^8 + x^4 + x^3 + x + 1)
    private const val POLYNOMIAL = 0x11B
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = (x shl 1)
            if ((x and 0x100) != 0) {
                x = x xor POLYNOMIAL
            }
        }
        LOG[0] = 0 // convention
    }

    private fun gfAdd(a: Int, b: Int): Int = a xor b

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    private fun gfDiv(a: Int, b: Int): Int {
        require(b != 0) { "Division by zero in GF(256)" }
        if (a == 0) return 0
        return EXP[(LOG[a] - LOG[b] + 255) % 255]
    }

    data class Share(
        val index: Int, // x-coordinate (1..n, must not be 0)
        val data: ByteArray, // y-coordinates for each byte of the secret
    ) {
        /** Aliases used by the UI and tests: x-coordinate / y-coordinates. */
        val x: Int get() = index
        val y: ByteArray get() = data

        fun encodeWords(): String {
            // Encode as human-readable share card token with mnemonic checksum
            val hex = data.joinToString("") { "%02X".format(it) }
            return "SHARE-$index-$hex"
        }

        companion object {
            fun decodeWords(str: String): Share {
                val parts = str.trim().split("-")
                require(parts.size >= 3 && parts[0] == "SHARE") { "Invalid Nominee Share format" }
                val idx = parts[1].toInt()
                val hex = parts[2]
                val bytes = ByteArray(hex.length / 2) { i ->
                    hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                return Share(idx, bytes)
            }
        }
    }

    fun shareToMnemonic(share: Share): String = share.encodeWords()

    /** Returns null when the token is malformed. */
    fun shareFromMnemonic(token: String): Share? =
        try { Share.decodeWords(token) } catch (_: Exception) { null }

    fun split(secret: ByteArray, k: Int, n: Int, random: SecureRandom = SecureRandom()): List<Share> {
        require(k in 2..n && n <= 255) { "Threshold k must be in 2..n and n <= 255" }
        val sharesData = Array(n) { ByteArray(secret.size) }

        for (byteIdx in secret.indices) {
            val secretByte = secret[byteIdx].toInt() and 0xFF
            // Polynomial: P(x) = secret + c1*x + c2*x^2 + ... + c_{k-1}*x^{k-1}
            val coeffs = IntArray(k)
            coeffs[0] = secretByte
            for (c in 1 until k) {
                coeffs[c] = random.nextInt(256)
            }

            for (shareIdx in 1..n) {
                var y = coeffs[0]
                var xPower = shareIdx
                for (c in 1 until k) {
                    y = gfAdd(y, gfMul(coeffs[c], xPower))
                    xPower = gfMul(xPower, shareIdx)
                }
                sharesData[shareIdx - 1][byteIdx] = y.toByte()
            }
        }

        return (1..n).map { i -> Share(i, sharesData[i - 1]) }
    }

    fun combine(shares: List<Share>, k: Int): ByteArray {
        require(shares.size >= k) { "Need at least $k shares to reconstruct secret" }
        val usedShares = shares.distinctBy { it.index }.take(k)
        val secretLen = usedShares[0].data.size
        require(usedShares.all { it.data.size == secretLen }) { "Share data size mismatch" }
        val secret = ByteArray(secretLen)

        // Lagrange interpolation at x = 0: L_j(0) = product_{m != j} (x_m / (x_m xor x_j))
        val lagrangeWeights = IntArray(k)
        for (j in 0 until k) {
            val xj = usedShares[j].index
            var num = 1
            var den = 1
            for (m in 0 until k) {
                if (m == j) continue
                val xm = usedShares[m].index
                num = gfMul(num, xm)
                den = gfMul(den, gfAdd(xm, xj))
            }
            lagrangeWeights[j] = gfDiv(num, den)
        }

        for (byteIdx in 0 until secretLen) {
            var v = 0
            for (j in 0 until k) {
                val yj = usedShares[j].data[byteIdx].toInt() and 0xFF
                v = gfAdd(v, gfMul(yj, lagrangeWeights[j]))
            }
            secret[byteIdx] = v.toByte()
        }

        return secret
    }
}
