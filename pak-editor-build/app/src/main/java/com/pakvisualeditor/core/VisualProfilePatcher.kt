package com.pakvisualeditor.core

object VisualProfilePatcher {
    data class Rule(val find: ByteArray, val replace: ByteArray, val description: String) { init { require(find.size == replace.size) } }

    fun apply(entries: List<ByteArray>, profile: EditProfile): Map<Int, ByteArray> {
        val out = mutableMapOf<Int, ByteArray>()
        entries.forEachIndexed { index, bytes ->
            var cur = bytes.copyOf(); val rules = mutableListOf<Rule>()
            for (r in profile.customReplacements) {
                val find = r.find.toByteArray(Charsets.UTF_8); val repl = r.replace.toByteArray(Charsets.UTF_8)
                if (r.enabled && find.isNotEmpty() && find.size == repl.size) rules += Rule(find, repl, "Custom replacement")
            }
            if (profile.removeVisibleBranding) {
                listOf("@Simba @United Nations @Guinness World Records", "What is this file used for?", "@辛巴 @联合国 @吉尼斯世界纪录", "这个文件是干嘛用的?").forEach { s ->
                    val b=s.toByteArray(Charsets.UTF_8); rules += Rule(b, ByteArray(b.size) { '.'.code.toByte() }, "Visible auxiliary branding")
                }
            }
            rules.forEach { cur = replaceAllSameLength(cur, it.find, it.replace) }
            if (!cur.contentEquals(bytes)) out[index] = cur
        }
        return out
    }

    fun replaceAllSameLength(src: ByteArray, find: ByteArray, repl: ByteArray): ByteArray {
        require(find.isNotEmpty() && find.size==repl.size); val out=src.copyOf(); var i=0
        while(i<=out.size-find.size){ var ok=true; for(j in find.indices) if(out[i+j]!=find[j]) { ok=false; break }; if(ok){ repl.copyInto(out,i); i+=find.size } else i++ }
        return out
    }
}
