package com.clinote.medicalrecords.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.CancellationSignal
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.clinote.medicalrecords.model.MedicalRecord
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class RecordExporter(private val context: Context) {
    fun createPdf(records: List<MedicalRecord>): File {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pageWidth = 842 // A4 landscape at 72 dpi
        val pageHeight = 595
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        fun header() {
            paint.textSize = 18f; paint.isFakeBoldText = true
            canvas.drawText("Clinote — Medical Record Register", 36f, 38f, paint)
            paint.textSize = 8f; paint.isFakeBoldText = true
            val columns = listOf(36f to "BED", 90f to "PATIENT", 240f to "CONSULTANT", 400f to "DETAILS", 650f to "SAVED")
            columns.forEach { (x, label) -> canvas.drawText(label, x, 62f, paint) }
            paint.isFakeBoldText = false; paint.textSize = 9f
        }
        header()
        var y = 80f
        records.forEach { record ->
            if (y > pageHeight - 45) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas; header(); y = 80f
            }
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(Date(record.savedAt))
            val values = listOf(record.bedNumber, record.patientName, record.primaryConsultant, record.details.replace("\\n", " "), date)
            val positions = listOf(36f, 90f, 240f, 400f, 650f)
            values.zip(positions).forEach { (value, x) -> canvas.drawText(ellipsize(value, 28), x, y, paint) }
            y += 18f
        }
        document.finishPage(page)
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(folder, "clinote-records-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use(document::writeTo)
        document.close()
        return file
    }

    fun print(records: List<MedicalRecord>) {
        val file = createPdf(records)
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("Clinote records", PdfFileAdapter(file), PrintAttributes.Builder().build())
    }

    fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Clinote PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun ellipsize(value: String, length: Int) = if (value.length <= length) value else value.take(length - 1) + "…"

    private class PdfFileAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes, newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?
        ) = callback.onLayoutFinished(
            PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true
        )

        override fun onWrite(
            pages: Array<android.print.PageRange>, destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal, callback: WriteResultCallback
        ) {
            runCatching { FileOutputStream(destination.fileDescriptor).use { output -> file.inputStream().copyTo(output) } }
                .onSuccess { callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }
}
