package com.apkforge.security

/**
 * APK Forge — Native Crypto Bridge (Kotlin → C++)
 * JNI wrapper exposing C++ cryptographic functions
 */
object NativeCrypto {

    init {
        System.loadLibrary("apkforge_native")
    }

    /** SHA-256 hash via C++ (hardware-optimized) */
    external fun computeSha256(input: String): String

    /** XOR-obfuscate a token to byte array */
    external fun obfuscateToken(token: String, key: Int): ByteArray

    /** Reverse XOR-obfuscation */
    external fun deobfuscateToken(data: ByteArray, key: Int): String

    /** Verify data integrity against expected hash */
    external fun verifyIntegrity(content: String, expectedHash: String): Boolean

    /** Get native library version string */
    external fun getNativeVersion(): String

    /** CRC32 checksum */
    external fun computeCrc32(data: ByteArray): Long

    /** Adler32 checksum */
    external fun computeAdler32(data: ByteArray): Long

    // ── Kotlin convenience wrappers ────────────────────────────────────────

    private const val DEFAULT_KEY = 0x42

    fun encryptToken(token: String): ByteArray =
        obfuscateToken(token, DEFAULT_KEY)

    fun decryptToken(data: ByteArray): String =
        deobfuscateToken(data, DEFAULT_KEY)

    fun hashAndVerify(data: String, hash: String): Boolean =
        verifyIntegrity(data, hash)

    fun generateFingerprint(vararg parts: String): String =
        computeSha256(parts.joinToString("|"))
}
