package com.clinote.medicalrecords

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.clinote.medicalrecords.data.RecordRepository
import com.clinote.medicalrecords.export.RecordExporter
import com.clinote.medicalrecords.model.MedicalRecord
import com.clinote.medicalrecords.model.RecordDraft
import com.clinote.medicalrecords.voice.MedicalVoiceParser
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClinoteApp() }
    }
}

private val Ink = Color(0xFF102A43)
private val Teal = Color(0xFF0B7285)
private val Mist = Color(0xFFF5F8FA)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ClinoteApp() {
    val context = LocalContext.current
    val repository = remember { RecordRepository(context) }
    var records by remember { mutableStateOf(repository.records()) }
    var showDictionary by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<MedicalRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    val exporter = remember { RecordExporter(context) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Color(0xFF3D5A80), background = Mist, surface = Color.White, onSurface = Ink)) {
        Scaffold(
            containerColor = Mist,
            snackbarHost = { SnackbarHost(snackbars) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Clinote", fontWeight = FontWeight.Bold)
                            Text("Medical record register", style = MaterialTheme.typography.labelSmall, color = Teal)
                        }
                    },
                    actions = {
                        TextButton(onClick = { showDictionary = true }) { Text("Dictionary") }
                    }
                )
            }
        ) { padding ->
            RecordTable(
                records = records,
                onNew = { adding = true },
                onEdit = { editor = it },
                onDelete = { record ->
                    repository.delete(record.id)
                    records = repository.records()
                    scope.launch { snackbars.showSnackbar("Record deleted") }
                },
                onPdf = {
                    val file = exporter.createPdf(records)
                    exporter.sharePdf(file)
                    scope.launch { snackbars.showSnackbar("PDF created — choose where to share or save it") }
                },
                onPrint = { exporter.print(records) },
                modifier = Modifier.padding(padding)
            )
        }
    }
    if (adding) RecordEditor(
        initial = null,
        dictionary = repository.dictionary(),
        onDismiss = { adding = false },
        onSave = { draft ->
            repository.save(MedicalRecord(
                bedNumber = draft.bedNumber, patientName = draft.patientName,
                primaryConsultant = draft.primaryConsultant, details = draft.details
            ))
            records = repository.records(); adding = false
        }
    )
    editor?.let { existing -> RecordEditor(
        initial = existing,
        dictionary = repository.dictionary(),
        onDismiss = { editor = null },
        onSave = { draft ->
            repository.save(existing.copy(
                bedNumber = draft.bedNumber, patientName = draft.patientName,
                primaryConsultant = draft.primaryConsultant, details = draft.details,
                updatedAt = System.currentTimeMillis()
            ))
            records = repository.records(); editor = null
        }
    ) }
    if (showDictionary) DictionaryDialog(
        initial = repository.dictionary(),
        onDismiss = { showDictionary = false },
        onSave = { repository.saveDictionary(it); showDictionary = false }
    )
}

@Composable
private fun RecordTable(
    records: List<MedicalRecord>,
    onNew: () -> Unit,
    onEdit: (MedicalRecord) -> Unit,
    onDelete: (MedicalRecord) -> Unit,
    onPdf: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<MedicalRecord?>(null) }
    val visible = records.filter { record ->
        listOf(record.bedNumber, record.patientName, record.primaryConsultant, record.details)
            .any { it.contains(query, ignoreCase = true) }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Patient notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Newest saved records first · timestamps are assigned at save time", style = MaterialTheme.typography.bodySmall, color = Color(0xFF486581))
        OutlinedTextField(query, { query = it }, label = { Text("Search patient, bed, consultant or note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onNew, modifier = Modifier.weight(1f)) { Text("+ New record") }
            OutlinedButton(onClick = onPdf) { Text("PDF") }
            OutlinedButton(onClick = onPrint) { Text("Print") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().weight(1f)) {
            val horizontal = rememberScrollState()
            Column(Modifier.horizontalScroll(horizontal).widthIn(min = 720.dp)) {
                HeaderRow()
                Divider()
                LazyColumn {
                    if (visible.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(if (records.isEmpty()) "No records yet. Add your first patient note." else "No matching records.")
                        }
                    }
                    items(visible, key = { it.id }) { record ->
                        DataRow(record, { onEdit(record) }, { deleteCandidate = record })
                        Divider(color = Color(0xFFE6EEF2))
                    }
                }
            }
        }
    }
    deleteCandidate?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete record?") },
            text = { Text("This permanently removes the record for " + (record.patientName.ifBlank { "this patient" }) + ".") },
            confirmButton = { TextButton(onClick = { onDelete(record); deleteCandidate = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable private fun HeaderRow() = Row(Modifier.background(Color(0xFFE7F4F6)).padding(horizontal = 12.dp, vertical = 10.dp)) {
    TableCell("BED", 72.dp, true); TableCell("PATIENT NAME", 148.dp, true); TableCell("PRIMARY CONSULTANT", 165.dp, true)
    TableCell("DETAILS", 245.dp, true); TableCell("DATE & TIME SAVED", 145.dp, true); TableCell("", 100.dp, true)
}

@Composable private fun DataRow(record: MedicalRecord, onEdit: () -> Unit, onDelete: () -> Unit) = Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    TableCell(record.bedNumber, 72.dp); TableCell(record.patientName, 148.dp); TableCell(record.primaryConsultant, 165.dp)
    TableCell(record.details, 245.dp); TableCell(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.savedAt)), 145.dp)
    Row(Modifier.width(100.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFB42318)) }
    }
}

@Composable private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, header: Boolean = false) =
    Text(text, modifier = Modifier.width(width).padding(end = 8.dp), maxLines = if (header) 1 else 2, overflow = TextOverflow.Ellipsis,
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal)

@Composable
private fun RecordEditor(initial: MedicalRecord?, dictionary: Map<String, String>, onDismiss: () -> Unit, onSave: (RecordDraft) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial?.let { RecordDraft(it.bedNumber, it.patientName, it.primaryConsultant, it.details) } ?: RecordDraft()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New patient record" else "Edit patient record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date & time are securely assigned when the record is first saved.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF486581))
                OutlinedTextField(draft.bedNumber, { draft = draft.copy(bedNumber = it) }, label = { Text("Bed Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.patientName, { draft = draft.copy(patientName = it) }, label = { Text("Patient Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.primaryConsultant, { draft = draft.copy(primaryConsultant = it) }, label = { Text("Primary Consultant") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.details, { draft = draft.copy(details = it) }, label = { Text("Details") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                VoiceEntry(dictionary = dictionary, onMerged = { draft = it }, current = draft)
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Save record") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun VoiceEntry(dictionary: Map<String, String>, current: RecordDraft, onMerged: (RecordDraft) -> Unit) {
    val context = LocalContext.current
    val recognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val parser = remember(dictionary) { MedicalVoiceParser(dictionary) }
    val latestDraft by rememberUpdatedState(current)
    val listening = remember { AtomicBoolean(false) }
    var active by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
    fun begin() {
        listening.set(true); active = true
        recognizer.startListening(intent)
    }
    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) onMerged(parser.merge(latestDraft, spoken))
                transcript = ""
                if (listening.get()) handler.postDelayed({ if (listening.get()) recognizer.startListening(intent) }, 250)
            }
            override fun onError(error: Int) {
                if (listening.get()) handler.postDelayed({ if (listening.get()) recognizer.startListening(intent) }, 450)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { listening.set(false); recognizer.destroy() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) begin() }
    Column {
        FilledTonalButton(onClick = {
            if (active) { listening.set(false); recognizer.cancel(); active = false; transcript = "" }
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text(if (active) "■ Stop voice recording" else "● Start voice recording") }
        Text(if (active) ("Listening continuously… " + transcript) else "Voice fills labelled fields in any order. Say “Details” to dictate a note.",
            style = MaterialTheme.typography.bodySmall, color = if (active) Teal else Color(0xFF486581), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DictionaryDialog(initial: Map<String, String>, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var terms by remember { mutableStateOf(initial.map { it.key to it.value }.sortedBy { it.first }.toMutableList()) }
    var heard by remember { mutableStateOf("") }
    var correction by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Medical dictionary") },
        text = {
            Column {
                Text("Spoken phrase → saved spelling. Consultant names are preserved exactly.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.height(200.dp)) {
                    items(terms, key = { it.first }) { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.first + " → " + entry.second, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { terms = terms.filterNot { it.first == entry.first }.toMutableList() }) { Text("Remove") }
                        }
                    }
                }
                OutlinedTextField(heard, { heard = it }, label = { Text("Heard as") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(correction, { correction = it }, label = { Text("Save as") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    if (heard.isNotBlank() && correction.isNotBlank()) {
                        terms = (terms.filterNot { it.first.equals(heard.trim(), true) } + (heard.trim() to correction.trim())).toMutableList()
                        heard = ""; correction = ""
                    }
                }) { Text("+ Add term") }
            }
        },
        confirmButton = { Button(onClick = { onSave(terms.toMap()) }) { Text("Save dictionary") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
