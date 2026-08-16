package com.clinote.medicalrecords.voice

import com.clinote.medicalrecords.data.RecordRepository
import com.clinote.medicalrecords.model.RecordDraft
import java.util.Locale

/**
 * Deterministic on-device parser: named fields can arrive in any order. The speech recognizer
 * supplies text; no clinical recording or transcript is sent to a server by this component.
 */
class MedicalVoiceParser(private val dictionary: Map<String, String>) {
    private enum class Field { BED, PATIENT, CONSULTANT, DETAILS }
    private var activeField = Field.DETAILS
    private val labels = listOf(
        "bed number" to Field.BED, "bed" to Field.BED,
        "patient name" to Field.PATIENT, "patient" to Field.PATIENT, "name" to Field.PATIENT,
        "primary consultant" to Field.CONSULTANT, "consultant" to Field.CONSULTANT, "doctor" to Field.CONSULTANT,
        "details" to Field.DETAILS, "notes" to Field.DETAILS, "note" to Field.DETAILS
    )

    fun merge(current: RecordDraft, transcript: String): RecordDraft {
        val normalized = normalize(transcript)
        if (normalized.isBlank()) return current
        val candidates = labels.mapNotNull { (label, field) ->
            Regex("\\b${Regex.escape(label)}\\b", RegexOption.IGNORE_CASE).find(normalized)?.let { it.range.first to Pair(field, it.range) }
        }.sortedWith(compareBy<Pair<Int, Pair<Field, IntRange>>> { it.first }.thenByDescending { it.second.second.last - it.second.second.first })
        val matches = candidates.fold(mutableListOf<Pair<Int, Pair<Field, IntRange>>>()) { chosen, candidate ->
            val candidateRange = candidate.second.second
            if (chosen.none { selected ->
                    val selectedRange = selected.second.second
                    candidateRange.first <= selectedRange.last && selectedRange.first <= candidateRange.last
                }) chosen += candidate
            chosen
        }.sortedBy { it.first }

        var draft = current
        if (matches.isEmpty()) return apply(current, activeField, normalized)
        matches.forEachIndexed { index, (_, payload) ->
            val (field, range) = payload
            activeField = field
            val end = matches.getOrNull(index + 1)?.second?.second?.first ?: normalized.length
            val spoken = normalized.substring(range.last + 1, end).trim(' ', ',', ':', ';', '.')
            if (spoken.isBlank()) return@forEachIndexed
            draft = apply(draft, field, spoken)
        }
        return draft
    }

    private fun apply(draft: RecordDraft, field: Field, spoken: String) = when (field) {
        Field.BED -> draft.copy(bedNumber = extractBed(spoken))
        Field.PATIENT -> draft.copy(patientName = titleCase(spoken))
        Field.CONSULTANT -> draft.copy(primaryConsultant = identifyConsultant(spoken))
        Field.DETAILS -> draft.copy(details = append(draft.details, correctTerms(spoken)))
    }

    private fun normalize(input: String) = input.replace(Regex("\\s+"), " ").trim()
    private fun extractBed(value: String) = Regex("[A-Za-z]*\\s*[- ]?\\d+[A-Za-z]*|\\d+").find(value)?.value?.replace(" ", "") ?: value
    private fun append(previous: String, next: String) = listOf(previous.trim(), next.trim()).filter { it.isNotEmpty() }.joinToString("\n")

    private fun correctTerms(value: String): String = dictionary.entries.fold(value) { text, (heard, corrected) ->
        text.replace(Regex("\\b${Regex.escape(heard)}\\b", RegexOption.IGNORE_CASE), corrected)
    }

    private fun identifyConsultant(value: String): String {
        val corrected = correctTerms(value)
        val allNames = RecordRepository.consultantNames
        return allNames.firstOrNull { corrected.contains(it, ignoreCase = true) }
            ?: allNames.firstOrNull { name ->
                name.lowercase(Locale.ROOT).removePrefix("dr ").split(" ").any { token -> corrected.lowercase(Locale.ROOT).contains(token) }
            } ?: titleCase(corrected.removePrefix("Dr ").let { "Dr $it" })
    }

    private fun titleCase(value: String) = value.lowercase(Locale.ROOT).split(" ").joinToString(" ") {
        it.replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
    }
}
