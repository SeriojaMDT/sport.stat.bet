package com.pakvisualeditor.core

data class TextReplacement(
    val find: String = "",
    val replace: String = "",
    val enabled: Boolean = true
)

enum class VisualPreset {
    KEEP_SOURCE,
    ENGLISH_ORIGINAL_LAYOUT,
    ENGLISH_CLEAN_RAISED_HUD
}

/** Editing profile stored by the UI. */
data class EditProfile(
    val visualPreset: VisualPreset = VisualPreset.ENGLISH_CLEAN_RAISED_HUD,
    val removeVisibleBranding: Boolean = true,
    val keepOriginalDebugTextRenderer: Boolean = true,
    val aliveLabel: String = "Alive",
    val playersLabel: String = "Players",
    val botsLabel: String = "Bots",
    val aliveZ: Int = 211,
    val playersBotsZ: Int = 219,
    val aliveScale: Float = 1.0125f,
    val playersBotsScale: Float = 1.08f,
    val textRed: Int = 173,
    val textGreen: Int = 216,
    val textBlue: Int = 230,
    val customReplacements: List<TextReplacement> = emptyList()
) {
    fun preview(alive: Int = 56, players: Int = 34, bots: Int = 22): String =
        "$aliveLabel:$alive | $playersLabel:$players | $botsLabel:$bots"

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"visualPreset\": \"${visualPreset.name}\",")
        appendLine("  \"removeVisibleBranding\": $removeVisibleBranding,")
        appendLine("  \"keepOriginalDebugTextRenderer\": $keepOriginalDebugTextRenderer,")
        appendLine("  \"aliveLabel\": \"${aliveLabel.escapeJson()}\",")
        appendLine("  \"playersLabel\": \"${playersLabel.escapeJson()}\",")
        appendLine("  \"botsLabel\": \"${botsLabel.escapeJson()}\",")
        appendLine("  \"aliveZ\": $aliveZ,")
        appendLine("  \"playersBotsZ\": $playersBotsZ,")
        appendLine("  \"aliveScale\": $aliveScale,")
        appendLine("  \"playersBotsScale\": $playersBotsScale,")
        appendLine("  \"textColor\": [$textRed, $textGreen, $textBlue],")
        appendLine("  \"customReplacements\": [")
        customReplacements.forEachIndexed { i, r ->
            append("    {\"enabled\":${r.enabled},\"find\":\"${r.find.escapeJson()}\",\"replace\":\"${r.replace.escapeJson()}\"}")
            if (i != customReplacements.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun String.escapeJson(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}
