package com.apkforge.utils

import java.io.File

/**
 * APK Forge — Native File Operations Bridge (Kotlin → C++)
 */
object NativeFileOps {

    init {
        System.loadLibrary("apkforge_native")
    }

    external fun fileExists(path: String): Boolean
    external fun getFileSizeNative(path: String): Long
    external fun readApkHeader(path: String): ByteArray?
    external fun isValidApk(path: String): Boolean
    external fun formatBuildLog(rawLog: String): String

    // Kotlin convenience
    fun validateApk(file: File): Boolean =
        file.exists() && isValidApk(file.absolutePath)

    fun getApkSize(file: File): Long =
        if (file.exists()) getFileSizeNative(file.absolutePath) else -1L
}
