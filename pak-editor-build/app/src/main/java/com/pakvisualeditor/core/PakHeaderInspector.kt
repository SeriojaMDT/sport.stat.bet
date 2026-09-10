package com.pakvisualeditor.core

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object PakHeaderInspector {
    const val FOOTER_SIZE = 45
    const val PUBGM_GLOBAL_MAGIC = 0x506E0406L

    data class Result(
        val fileName: String,
        val fileSize: Long,
        val sha1: String,
        val magic: Long,
        val version: Long,
        val encryptedIndexRaw: Int,
        val isPubgGlobal: Boolean,
        val isSupportedProfile: Boolean
    ) {
        val magicHex: String get() = "0x%08X".format(magic)
        val summary: String
            get() = when {
                !isPubgGlobal -> "Unsupported PAK magic $magicHex"
                version != 14L -> "PUBG Mobile PAK detected, but version $version is not in the v14 profile"
                else -> "PUBG Mobile Global PAK v14 detected"
            }
    }

    fun inspect(file: File): Result {
        require(file.isFile) { "File not found: ${file.absolutePath}" }
        require(file.length() >= FOOTER_SIZE) { "File is too small to be a supported PAK" }
        val footer = ByteArray(FOOTER_SIZE)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length() - FOOTER_SIZE)
            raf.readFully(footer)
        }
        val encryptedIndexRaw = footer[0].toInt() and 0xFF
        val magic = u32le(footer, 1)
        val version = u32le(footer, 5)
        val isGlobal = magic == PUBGM_GLOBAL_MAGIC
        return Result(file.name, file.length(), sha1(file), magic, version, encryptedIndexRaw, isGlobal, isGlobal && version == 14L)
    }

    private fun u32le(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
