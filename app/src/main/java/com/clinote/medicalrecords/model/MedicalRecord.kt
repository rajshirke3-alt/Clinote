package com.clinote.medicalrecords.model

import java.util.UUID

data class MedicalRecord(
    val id: String = UUID.randomUUID().toString(),
    val bedNumber: String,
    val patientName: String,
    val primaryConsultant: String,
    val details: String,
    /** Immutable creation time. Never parsed from speech; it is set when the record is saved. */
    val savedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = savedAt
)

data class RecordDraft(
    val bedNumber: String = "",
    val patientName: String = "",
    val primaryConsultant: String = "",
    val details: String = ""
)
