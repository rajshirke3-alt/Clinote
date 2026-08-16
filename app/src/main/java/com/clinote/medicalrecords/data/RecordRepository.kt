package com.clinote.medicalrecords.data

import android.content.Context
import com.clinote.medicalrecords.model.MedicalRecord
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight offline-first store. Data remains on the device unless the user exports it. */
class RecordRepository(context: Context) {
    private val preferences = context.getSharedPreferences("clinote_records", Context.MODE_PRIVATE)
    private val recordsKey = "records"
    private val termsKey = "medical_terms"

    fun records(): List<MedicalRecord> = decodeRecords()
        .sortedWith(compareByDescending<MedicalRecord> { it.savedAt }.thenByDescending { it.updatedAt })

    fun save(record: MedicalRecord) {
        val records = decodeRecords().toMutableList()
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) records[index] = record else records += record
        persist(records)
    }

    fun delete(id: String) = persist(decodeRecords().filterNot { it.id == id })

    fun dictionary(): MutableMap<String, String> {
        val raw = preferences.getString(termsKey, null) ?: return defaultDictionary().toMutableMap()
        return runCatching {
            JSONObject(raw).let { json ->
                json.keys().asSequence().associateWith { json.getString(it) }.toMutableMap().apply {
                    putAll(requiredConsultants())
                }
            }
        }.getOrElse { defaultDictionary().toMutableMap() }
    }

    fun saveDictionary(terms: Map<String, String>) {
        val json = JSONObject()
        (terms + requiredConsultants()).filter { it.key.isNotBlank() && it.value.isNotBlank() }.forEach { (heard, corrected) ->
            json.put(heard.trim(), corrected.trim())
        }
        preferences.edit().putString(termsKey, json.toString()).apply()
    }

    private fun persist(records: List<MedicalRecord>) {
        val json = JSONArray()
        records.forEach { record ->
            json.put(JSONObject().apply {
                put("id", record.id); put("bedNumber", record.bedNumber)
                put("patientName", record.patientName); put("primaryConsultant", record.primaryConsultant)
                put("details", record.details); put("savedAt", record.savedAt); put("updatedAt", record.updatedAt)
            })
        }
        preferences.edit().putString(recordsKey, json.toString()).apply()
    }

    private fun decodeRecords(): List<MedicalRecord> = runCatching {
        val json = JSONArray(preferences.getString(recordsKey, "[]"))
        (0 until json.length()).map { index ->
            val value = json.getJSONObject(index)
            MedicalRecord(
                id = value.getString("id"), bedNumber = value.optString("bedNumber"),
                patientName = value.optString("patientName"), primaryConsultant = value.optString("primaryConsultant"),
                details = value.optString("details"), savedAt = value.optLong("savedAt"),
                updatedAt = value.optLong("updatedAt", value.optLong("savedAt"))
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        val consultantNames = listOf(
            "Dr Yatin Sagwekar", "Dr Chaitanya", "Dr Bharat", "Dr Poonam",
            "Dr Sonali Gautam", "Dr Dipak Bhangale"
        )

        fun defaultDictionary(): Map<String, String> = linkedMapOf(
            *requiredConsultants().toList().toTypedArray(),
            "doctor yatin" to "Dr Yatin Sagwekar",
            "dr yatin sagvaykar" to "Dr Yatin Sagwekar",
            "doctor chaitanya" to "Dr Chaitanya",
            "doctor bharat" to "Dr Bharat",
            "doctor poonam" to "Dr Poonam",
            "doctor sonali gautam" to "Dr Sonali Gautam",
            "doctor dipak bhangale" to "Dr Dipak Bhangale",
            "saturation" to "SpO₂",
            "spo two" to "SpO₂",
            "spo2" to "SpO₂",
            "blood pressure" to "BP"
        )

        private fun requiredConsultants(): Map<String, String> = consultantNames.associateWith { it }
    }
}
