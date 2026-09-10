package com.pakvisualeditor.core

import java.io.File

interface PakV14Backend {
    data class BuildProgress(val stage: String, val percent: Int)
    data class BuildResult(val output: File, val sha1: String)

    fun build(
        source: File,
        output: File,
        profile: EditProfile,
        onProgress: (BuildProgress) -> Unit = {}
    ): BuildResult
}
