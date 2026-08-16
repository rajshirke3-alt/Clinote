package com.clinote.medicalrecords.voice

import com.clinote.medicalrecords.data.RecordRepository
import com.clinote.medicalrecords.model.RecordDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicalVoiceParserTest {
    @Test
    fun mapsLabelledFieldsRegardlessOfSpeakingOrder() {
        val parser = MedicalVoiceParser(RecordRepository.defaultDictionary())
        val result = parser.merge(
            RecordDraft(),
            "Patient name Asha Sharma, bed number 12, consultant Dr Bharat, details fever with SpO2 94"
        )

        assertEquals("Asha Sharma", result.patientName)
        assertEquals("12", result.bedNumber)
        assertEquals("Dr Bharat", result.primaryConsultant)
        assertEquals("fever with SpO₂ 94", result.details)
    }

    @Test
    fun detailsModeContinuesAcrossSpeechSegments() {
        val parser = MedicalVoiceParser(RecordRepository.defaultDictionary())
        val start = parser.merge(RecordDraft(), "Details")
        val result = parser.merge(start, "Patient is stable overnight")

        assertEquals("Patient is stable overnight", result.details)
    }
}
