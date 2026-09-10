package com.pakvisualeditor.core

import java.security.MessageDigest

/**
 * Exact visual-profile support for the v4.6 PAK family used during development.
 * It deliberately refuses to replace CharacterBase.lua for unknown hashes.
 */
object KnownV46Profile {
    private val knownEntry0Sha1 = setOf(
        "0ce32acf185b86566664cfe8385576a91224ea47",
        "8bc3de8bbcb03d67badb181782f72bbdbc2e5683",
        "0136607df20973f0d279971a4451371536390b04",
        "6b79c8e1e2bf01a2835c12c133033886276e64f9",
        "e24b7303d31e6d78810bec914f99c138d9ca0c4d"
    )

    fun isKnownEntry0(bytes: ByteArray): Boolean = sha1(bytes) in knownEntry0Sha1

    fun applyTemplate(
        entries: List<ByteArray>,
        preset: VisualPreset,
        englishOriginalTemplate: ByteArray?,
        englishCleanRaisedTemplate: ByteArray?
    ): Map<Int, ByteArray> {
        if (preset == VisualPreset.KEEP_SOURCE || entries.isEmpty()) return emptyMap()
        val source = entries[0]
        require(isKnownEntry0(source)) {
            "The protected CharacterBase.lua does not match the tested v4.6 profile. Rebuild is still available with Keep source / plain-text patches."
        }
        val template = when (preset) {
            VisualPreset.KEEP_SOURCE -> return emptyMap()
            VisualPreset.ENGLISH_ORIGINAL_LAYOUT -> requireNotNull(englishOriginalTemplate) { "English template is missing" }
            VisualPreset.ENGLISH_CLEAN_RAISED_HUD -> requireNotNull(englishCleanRaisedTemplate) { "Clean raised-HUD template is missing" }
        }
        require(template.size == source.size) { "Template size mismatch" }
        return mapOf(0 to template.copyOf())
    }

    private fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
