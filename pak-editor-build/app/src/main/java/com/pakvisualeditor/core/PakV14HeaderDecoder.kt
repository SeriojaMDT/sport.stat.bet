package com.pakvisualeditor.core

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PakV14HeaderDecoder {
    private val headerMaskWords = longArrayOf(
        0xA6D17AB4L, 0xD4783A41L, 0x1C3F5045L, 0x034F4301L,
        0x5AA0D38CL, 0xDE5364D3L, 0x59CAA8EDL, 0x5495C626L,
        0xE09B2584L, 0x96D67A0EL, 0x1FFBEE0AL, 0xB84D0C43L,
        0xD373283CL, 0x2513CE0AL, 0xC8CF93C4L, 0xD8CBD95DL
    )
    private val aesKey = hex("d21bf172e5821c51b7b27cd7100d050c6aab5e47000000000000000000000000")
    private val aesIv = hex("78c14502f29c0a600c42b3246799b78e")

    data class DecodedHeader(val indexOffset: Long, val indexSize: Long, val encryptedIndex: Boolean, val expectedIndexSha1: String)
    data class IndexValidation(val header: DecodedHeader, val decryptedIndexSize: Int, val actualIndexSha1: String, val sha1Matches: Boolean)

    fun decodeHeader(file: File): DecodedHeader {
        require(file.length() >= PakHeaderInspector.FOOTER_SIZE)
        val footer = ByteArray(PakHeaderInspector.FOOTER_SIZE)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length() - footer.size)
            raf.readFully(footer)
        }
        val rawEncrypted = footer[0].toInt() and 0xFF
        val rawIndexSize = u64le(footer, 29)
        val rawIndexOffset = u64le(footer, 37)
        val offsetMask = (headerMaskWords[0] shl 32) or headerMaskWords[1]
        val sizeMask = (headerMaskWords[10] shl 32) or headerMaskWords[11]
        val indexOffset = rawIndexOffset xor offsetMask
        val indexSize = rawIndexSize xor sizeMask
        val encrypted = (rawEncrypted xor (headerMaskWords[3].toInt() and 0xFF)) != 0
        val decodedHash = ByteArray(20)
        for (i in 0 until 5) putU32le(decodedHash, i * 4, u32le(footer, 9 + i * 4) xor headerMaskWords[4 + i])
        return DecodedHeader(indexOffset, indexSize, encrypted, decodedHash.toHex())
    }

    fun decryptAndValidateIndex(file: File): IndexValidation {
        val h = decodeHeader(file)
        require(h.indexOffset >= 0 && h.indexSize > 0)
        require(h.indexOffset + h.indexSize <= file.length()) { "Decoded index range is outside the file" }
        require(h.indexSize <= Int.MAX_VALUE)
        val encryptedBytes = ByteArray(h.indexSize.toInt())
        RandomAccessFile(file, "r").use { raf -> raf.seek(h.indexOffset); raf.readFully(encryptedBytes) }
        val decoded = if (h.encryptedIndex) {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(aesIv))
            cipher.doFinal(encryptedBytes)
        } else encryptedBytes
        val actual = MessageDigest.getInstance("SHA-1").digest(decoded).toHex()
        return IndexValidation(h, decoded.size, actual, actual.equals(h.expectedIndexSha1, true))
    }

    private fun u32le(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or ((data[offset + 3].toLong() and 0xFF) shl 24)
    private fun u64le(data: ByteArray, offset: Int): Long { var r=0L; for (i in 0 until 8) r = r or ((data[offset+i].toLong() and 0xFF) shl (8*i)); return r }
    private fun putU32le(dst: ByteArray, offset: Int, value: Long) { for (i in 0 until 4) dst[offset+i] = ((value ushr (8*i)) and 0xFF).toByte() }
    private fun hex(s: String): ByteArray = ByteArray(s.length/2) { i -> s.substring(i*2, i*2+2).toInt(16).toByte() }
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
