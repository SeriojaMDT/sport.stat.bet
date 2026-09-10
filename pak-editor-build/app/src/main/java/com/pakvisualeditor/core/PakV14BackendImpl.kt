package com.pakvisualeditor.core

import java.io.File
import java.security.MessageDigest

class PakV14BackendImpl(
    private val englishOriginalTemplateProvider: (() -> ByteArray)? = null,
    private val englishCleanRaisedTemplateProvider: (() -> ByteArray)? = null
) : PakV14Backend {
    override fun build(source: File, output: File, profile: EditProfile, onProgress: (PakV14Backend.BuildProgress) -> Unit): PakV14Backend.BuildResult {
        onProgress(PakV14Backend.BuildProgress("Reading and validating PAK", 8))
        val parsed = PakV14Archive.parse(source)
        onProgress(PakV14Backend.BuildProgress("Extracting entries", 24))
        val entries = PakV14Archive.extractAll(parsed)

        onProgress(PakV14Backend.BuildProgress("Applying visual profile", 42))
        val replacements = mutableMapOf<Int, ByteArray>()
        replacements.putAll(KnownV46Profile.applyTemplate(
            entries,
            profile.visualPreset,
            if (profile.visualPreset == VisualPreset.ENGLISH_ORIGINAL_LAYOUT) englishOriginalTemplateProvider?.invoke() else null,
            if (profile.visualPreset == VisualPreset.ENGLISH_CLEAN_RAISED_HUD) englishCleanRaisedTemplateProvider?.invoke() else null
        ))
        val afterTemplate = entries.mapIndexed { index, bytes -> replacements[index] ?: bytes }
        replacements.putAll(VisualProfilePatcher.apply(afterTemplate, profile))

        onProgress(PakV14Backend.BuildProgress("Compressing and rebuilding index", 62))
        PakV14Archive.rebuildSameLayout(parsed, replacements, output)
        onProgress(PakV14Backend.BuildProgress("Round-trip validation", 88))
        val validation = PakV14Archive.validate(output)
        require(validation.valid && validation.indexSha1Valid && validation.extractedHashesValid) { validation.message }
        onProgress(PakV14Backend.BuildProgress("Done", 100))
        return PakV14Backend.BuildResult(output, sha1(output))
    }

    private fun sha1(file: File): String {
        val d = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val b = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(b)
                if (n <= 0) break
                d.update(b, 0, n)
            }
        }
        return d.digest().toHexLower()
    }
}
