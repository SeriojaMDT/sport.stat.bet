package com.pakvisualeditor

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakvisualeditor.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.GZIPInputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PakEditorScreen(copyUriToCache = ::copyUriToCache, saveFileToUri = ::saveFileToUri, loadTemplate = ::loadTemplate)
                }
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val displayName = queryDisplayName(this, uri) ?: "input.pak"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.endsWith(".pak", true)) it else "$it.pak" }
        val out = File(cacheDir, safeName)
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun saveFileToUri(file: File, uri: Uri) {
        contentResolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output) { "Cannot create output file" }
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun loadTemplate(assetName: String): ByteArray = assets.open(assetName).use { raw -> GZIPInputStream(raw).use { it.readBytes() } }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}

@Composable
private fun PakEditorScreen(copyUriToCache: (Uri) -> File, saveFileToUri: (File, Uri) -> Unit, loadTemplate: (String) -> ByteArray) {
    var sourceFile by remember { mutableStateOf<File?>(null) }
    var inspection by remember { mutableStateOf<PakHeaderInspector.Result?>(null) }
    var indexValidation by remember { mutableStateOf<PakV14HeaderDecoder.IndexValidation?>(null) }
    var archiveValidation by remember { mutableStateOf<PakV14Archive.Validation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Open a PAK to begin") }
    var progress by remember { mutableIntStateOf(0) }
    var building by remember { mutableStateOf(false) }
    var lastBuilt by remember { mutableStateOf<File?>(null) }
    var lastBuiltSha1 by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    var preset by remember { mutableStateOf(VisualPreset.ENGLISH_CLEAN_RAISED_HUD) }
    var removeAuxBranding by remember { mutableStateOf(true) }
    var find1 by remember { mutableStateOf("") }
    var replace1 by remember { mutableStateOf("") }
    var find2 by remember { mutableStateOf("") }
    var replace2 by remember { mutableStateOf("") }
    var showFunctions by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            building = true; error = null; saveMessage = null; lastBuilt = null; progress = 5; status = "Copying PAK…"
            runCatching {
                val local = withContext(Dispatchers.IO) { copyUriToCache(uri) }
                progress = 20; status = "Inspecting header…"
                val info = withContext(Dispatchers.IO) { PakHeaderInspector.inspect(local) }
                val idx = if (info.isSupportedProfile) withContext(Dispatchers.IO) { PakV14HeaderDecoder.decryptAndValidateIndex(local) } else null
                progress = 55; status = "Validating archive…"
                val validation = if (info.isSupportedProfile) withContext(Dispatchers.IO) { PakV14Archive.validate(local) } else null
                sourceFile = local; inspection = info; indexValidation = idx; archiveValidation = validation; progress = 100
                status = if (validation?.valid == true) "PAK ready" else info.summary
            }.onFailure { error = it.message ?: it.toString(); status = "Open failed" }
            building = false
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) lastBuilt?.let { built -> scope.launch {
            runCatching { withContext(Dispatchers.IO) { saveFileToUri(built, uri) } }
                .onSuccess { saveMessage = "Saved successfully" }
                .onFailure { error = it.message ?: it.toString() }
        } }
    }

    val replacements = buildList {
        if (find1.isNotEmpty() || replace1.isNotEmpty()) add(TextReplacement(find1, replace1, true))
        if (find2.isNotEmpty() || replace2.isNotEmpty()) add(TextReplacement(find2, replace2, true))
    }
    val profile = EditProfile(visualPreset = preset, removeVisibleBranding = removeAuxBranding, customReplacements = replacements)
    val validReplacement1 = find1.toByteArray().size == replace1.toByteArray().size
    val validReplacement2 = find2.toByteArray().size == replace2.toByteArray().size
    val canBuild = inspection?.isSupportedProfile == true && archiveValidation?.valid == true && validReplacement1 && validReplacement2 && !building

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PAK Visual Editor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Offline v4.6 test editor", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { openLauncher.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !building) { Text("Open PAK") }
        if (building || progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        inspection?.let { info -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.fileName, fontWeight = FontWeight.SemiBold); Text(info.summary); Text("Version ${info.version} • ${info.magicHex}"); Text("${info.fileSize} bytes"); Text("SHA-1 ${info.sha1}", style = MaterialTheme.typography.bodySmall)
        } } }
        indexValidation?.let { idx -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Index", fontWeight = FontWeight.SemiBold); Text("Offset 0x${idx.header.indexOffset.toString(16)} • ${idx.header.indexSize} bytes")
            Text(if (idx.sha1Matches) "AES index SHA-1: VALID" else "AES index SHA-1: INVALID", color = if (idx.sha1Matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        } } }
        archiveValidation?.let { v -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Full validation", fontWeight = FontWeight.SemiBold); Text(v.message); Text("Entries: ${v.entries}")
        } } }

        HorizontalDivider(); Text("Visual profile", style = MaterialTheme.typography.titleLarge)
        PresetRow(preset == VisualPreset.KEEP_SOURCE, "Keep source", "Do not replace the protected CharacterBase; only byte-safe plain-text edits are applied.") { preset = VisualPreset.KEEP_SOURCE }
        PresetRow(preset == VisualPreset.ENGLISH_ORIGINAL_LAYOUT, "English — original layout", "Uses the tested English menu while keeping the original overlay layout.") { preset = VisualPreset.ENGLISH_ORIGINAL_LAYOUT }
        PresetRow(preset == VisualPreset.ENGLISH_CLEAN_RAISED_HUD, "English — clean + raised HUD", "Tested profile: English menu, visible Renji/TG branding removed, original AddDebugText renderer, Alive/Players/Bots raised.") { preset = VisualPreset.ENGLISH_CLEAN_RAISED_HUD }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = removeAuxBranding, onCheckedChange = { removeAuxBranding = it }); Spacer(Modifier.width(10.dp))
            Column { Text("Neutralize auxiliary branding"); Text("Replaces known auxiliary credit strings with dots without changing byte length.", style = MaterialTheme.typography.bodySmall) }
        }

        ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("HUD preview", fontWeight = FontWeight.SemiBold); Text("Alive:56   Players:34   Bots:22")
            if (preset == VisualPreset.ENGLISH_CLEAN_RAISED_HUD) Text("Tested Z values: Alive 211 • Players/Bots 219", style = MaterialTheme.typography.bodySmall)
            else if (preset == VisualPreset.ENGLISH_ORIGINAL_LAYOUT) Text("Original Z values: Alive 28 • Players/Bots 36", style = MaterialTheme.typography.bodySmall)
        } }

        HorizontalDivider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Advanced plain-text patches", style = MaterialTheme.typography.titleMedium); TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "Hide" else "Show") }
        }
        AnimatedVisibility(showAdvanced) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Replacement must have exactly the same UTF-8 byte length. This is useful for plain text; protected menu strings use the tested presets above.", style = MaterialTheme.typography.bodySmall)
            ReplacementRow("Patch 1", find1, { find1 = it }, replace1, { replace1 = it }, validReplacement1)
            ReplacementRow("Patch 2", find2, { find2 = it }, replace2, { replace2 = it }, validReplacement2)
        } }

        HorizontalDivider(); Text("Build", style = MaterialTheme.typography.titleLarge)
        Button(onClick = {
            val src = sourceFile ?: return@Button
            scope.launch {
                building = true; error = null; saveMessage = null; lastBuilt = null; lastBuiltSha1 = null; progress = 1; status = "Preparing build…"
                runCatching {
                    val out = File(requireNotNull(src.parentFile), src.nameWithoutExtension + "_edited.pak")
                    val backend = PakV14BackendImpl(
                        englishOriginalTemplateProvider = { loadTemplate("v46_en_original_entry0.gz") },
                        englishCleanRaisedTemplateProvider = { loadTemplate("v46_en_clean_raised_entry0.gz") }
                    )
                    val result = withContext(Dispatchers.IO) { backend.build(src, out, profile) { p -> progress = p.percent; status = p.stage } }
                    lastBuilt = result.output; lastBuiltSha1 = result.sha1; progress = 100; status = "Build & validation complete"
                }.onFailure { error = it.message ?: it.toString(); status = "Build failed" }
                building = false
            }
        }, enabled = canBuild, modifier = Modifier.fillMaxWidth()) { Text(if (building) "Building…" else "Build & Validate PAK") }

        lastBuilt?.let { built -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Output ready", fontWeight = FontWeight.SemiBold); Text(built.name); lastBuiltSha1?.let { Text("SHA-1 $it", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { saveLauncher.launch(built.name) }, modifier = Modifier.fillMaxWidth()) { Text("Save PAK to phone") }
        } } }

        HorizontalDivider(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Function reference", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { showFunctions = !showFunctions }) { Text(if (showFunctions) "Hide" else "Show") }
        }
        AnimatedVisibility(showFunctions) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            FunctionReference.items.forEach { item -> Column { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.meaning, style = MaterialTheme.typography.bodySmall) } }
        } }

        HorizontalDivider(); Text("Profile JSON", style = MaterialTheme.typography.titleMedium); Text(profile.toJson(), style = MaterialTheme.typography.bodySmall)
        Text("This editor does not include anti-cheat bypass or detection-evasion features.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PresetRow(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { RadioButton(selected = selected, onClick = onClick); Spacer(Modifier.width(6.dp)); Column(Modifier.padding(top = 8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun ReplacementRow(title: String, find: String, onFind: (String) -> Unit, replace: String, onReplace: (String) -> Unit, valid: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(find, onFind, label = { Text("Find") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(replace, onReplace, label = { Text("Replace") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        val a = find.toByteArray().size; val b = replace.toByteArray().size
        Text("UTF-8 bytes: $a → $b", color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
