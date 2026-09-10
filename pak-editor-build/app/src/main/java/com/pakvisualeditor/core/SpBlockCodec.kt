package com.pakvisualeditor.core

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object SpBlockCodec {
    private val key = byteArrayOf(0xE5.toByte(), 0x5B, 0x4E, 0xD1.toByte())

    fun decryptSp(ciphertext: ByteArray): ByteArray {
        val out = ciphertext.copyOf(); val size = out.size
        if (size < 8) return out
        out[0] = (out[0].toInt() xor key[(size + 0) and 3].toInt()).toByte()
        out[1] = (out[1].toInt() xor key[(size + 1) and 3].toInt()).toByte()
        out[2] = (out[2].toInt() xor key[(size + 2) and 3].toInt()).toByte()
        out[3] = (out[3].toInt() xor key[(size - 1) and 3].toInt()).toByte()
        val words = size / 4
        for (i in 1 until words) putU32le(out, i * 4, getU32le(out, i * 4) xor getU32le(out, (i - 1) * 4))
        return out
    }

    fun encryptSp(plaintext: ByteArray): ByteArray {
        val size = plaintext.size; val out = plaintext.copyOf()
        if (size < 8) return out
        val words = size / 4
        for (i in words - 1 downTo 1) putU32le(out, i * 4, getU32le(plaintext, i * 4) xor getU32le(plaintext, (i - 1) * 4))
        out[0] = (plaintext[0].toInt() xor key[(size + 0) and 3].toInt()).toByte()
        out[1] = (plaintext[1].toInt() xor key[(size + 1) and 3].toInt()).toByte()
        out[2] = (plaintext[2].toInt() xor key[(size + 2) and 3].toInt()).toByte()
        out[3] = (plaintext[3].toInt() xor key[(size - 1) and 3].toInt()).toByte()
        return out
    }

    data class EncodedBlock(val encryptedPadded: ByteArray, val compressedSize: Int, val uncompressedSize: Int)

    fun compressZlibSp(input: ByteArray, level: Int = 9): EncodedBlock {
        val deflater = Deflater(level, false); deflater.setInput(input); deflater.finish()
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (!deflater.finished()) { val n=deflater.deflate(buffer); output.write(buffer,0,n) }
        deflater.end(); val compressed = output.toByteArray(); val padded = pkcs7Pad(compressed,16)
        return EncodedBlock(encryptSp(padded), compressed.size, input.size)
    }

    fun decryptSpZlib(encryptedPadded: ByteArray, compressedSize: Int, expectedSize: Int): ByteArray {
        require(compressedSize in 1..encryptedPadded.size)
        val decrypted = decryptSp(encryptedPadded); val compressed = decrypted.copyOf(compressedSize)
        val inflater = Inflater(false); inflater.setInput(compressed)
        val output = ByteArrayOutputStream(expectedSize.coerceAtLeast(1024)); val buffer = ByteArray(8192)
        while (!inflater.finished()) { val n=inflater.inflate(buffer); if (n==0 && inflater.needsInput()) break; output.write(buffer,0,n) }
        inflater.end(); val result = output.toByteArray(); require(result.size==expectedSize) { "Unexpected uncompressed size ${result.size}, expected $expectedSize" }; return result
    }

    private fun pkcs7Pad(data: ByteArray, blockSize: Int): ByteArray { val pad=blockSize-(data.size%blockSize); return ByteArray(data.size+pad).also { out -> data.copyInto(out); for(i in data.size until out.size) out[i]=pad.toByte() } }
    private fun getU32le(data: ByteArray, offset: Int): Int = (data[offset].toInt() and 0xFF) or ((data[offset+1].toInt() and 0xFF) shl 8) or ((data[offset+2].toInt() and 0xFF) shl 16) or ((data[offset+3].toInt() and 0xFF) shl 24)
    private fun putU32le(data: ByteArray, offset: Int, value: Int) { data[offset]=value.toByte(); data[offset+1]=(value ushr 8).toByte(); data[offset+2]=(value ushr 16).toByte(); data[offset+3]=(value ushr 24).toByte() }
}
