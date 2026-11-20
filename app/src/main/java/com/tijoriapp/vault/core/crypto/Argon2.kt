package com.tijoriapp.vault.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id (RFC 9106) via Bouncy Castle's audited implementation.
 * We never roll our own primitive: the unit tests check it against the RFC 9106 test vector.
 */
object Argon2 {
    data class Params(
        val memoryKiB: Int = 65_536, // 64 MiB: OWASP 2024 baseline is >= 19 MiB / t=2
        val iterations: Int = 3,
        val parallelism: Int = 1,
    ) {
        init {
            require(memoryKiB >= 8 * parallelism) { "memory too small" }
            require(iterations >= 1 && parallelism >= 1)
        }
    }

    fun derive(
        password: ByteArray,
        salt: ByteArray,
        params: Params,
        outLength: Int = 32,
        secret: ByteArray? = null,
        additional: ByteArray? = null,
    ): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .withSalt(salt)
        if (secret != null) builder.withSecret(secret)
        if (additional != null) builder.withAdditional(additional)
        val gen = Argon2BytesGenerator()
        gen.init(builder.build())
        val out = ByteArray(outLength)
        gen.generateBytes(password, out)
        return out
    }
}
