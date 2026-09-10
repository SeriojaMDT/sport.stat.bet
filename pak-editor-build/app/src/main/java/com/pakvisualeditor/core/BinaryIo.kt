package com.pakvisualeditor.core

import java.io.ByteArrayOutputStream

internal class ByteCursor(private val data: ByteArray, var pos: Int = 0) {
    fun u8(): Int = data[pos++].toInt() and 0xFF
    fun i32(): Int {
        val v = (data[pos].toInt() and 0xFF) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            ((data[pos + 2].toInt() and 0xFF) shl 16) or
            ((data[pos + 3].toInt() and 0xFF) shl 24)
        pos += 4
        return v
    }
    fun u32(): Long = i32().toLong() and 0xFFFF_FFFFL
    fun i64(): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 8
        return v
    }
    fun bytes(n: Int): ByteArray = data.copyOfRange(pos, pos + n).also { pos += n }
    fun skip(n: Int) { pos += n }
}

internal fun ByteArray.putI32LE(offset: Int, value: Int) {
    for (i in 0 until 4) this[offset + i] = (value ushr (8 * i)).toByte()
}
internal fun ByteArray.putU32LE(offset: Int, value: Long) = putI32LE(offset, value.toInt())
internal fun ByteArray.putI64LE(offset: Int, value: Long) {
    for (i in 0 until 8) this[offset + i] = (value ushr (8 * i)).toByte()
}
internal fun ByteArray.getU32LE(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)
internal fun ByteArray.getI64LE(offset: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = v or ((this[offset + i].toLong() and 0xFF) shl (8 * i))
    return v
}
internal fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }

internal class LeWriter {
    private val out = ByteArrayOutputStream()
    val size: Int get() = out.size()
    fun u8(v: Int) { out.write(v and 0xFF) }
    fun i32(v: Int) { repeat(4) { i -> out.write((v ushr (8 * i)) and 0xFF) } }
    fun i64(v: Long) { repeat(8) { i -> out.write(((v ushr (8 * i)) and 0xFF).toInt()) } }
    fun bytes(v: ByteArray) { out.write(v) }
    fun bytes(v: ByteArray, off: Int, len: Int) { out.write(v, off, len) }
    fun build(): ByteArray = out.toByteArray()
}
