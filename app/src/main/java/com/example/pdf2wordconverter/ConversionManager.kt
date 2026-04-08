package com.example.pdf2wordconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

object ConversionManager {

    private const val TAG = "PDF2Word"

    fun convertPdfToWord(
        context: Context,
        inputUri: Uri,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting PDF → Word conversion")

                val fileName = getFileName(context, inputUri)
                Log.d(TAG, "Input file: $fileName")

                val baseName = fileName.substringBeforeLast('.')
                val outputFileName = "$baseName.docx"
                val outputFile = File(context.filesDir, outputFileName)
                Log.d(TAG, "Output path: ${outputFile.absolutePath}")

                val inputBytes = context.contentResolver.openInputStream(inputUri)?.use {
                    it.readBytes()
                } ?: throw Exception("Cannot open the selected file")

                Log.d(TAG, "Read ${inputBytes.size} bytes from input")

                val extractedText = extractTextFromPdf(inputBytes)
                Log.d(TAG, "Extracted ${extractedText.length} chars of text")

                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted. This PDF may be image/scan-based (OCR not supported). Try a text-based PDF.")
                }

                val docxBytes = buildDocxFromText(extractedText, fileName)
                Log.d(TAG, "Built DOCX: ${docxBytes.size} bytes")

                outputFile.writeBytes(docxBytes)
                Log.d(TAG, "Written to: ${outputFile.absolutePath}")

                if (outputFile.length() == 0L) {
                    throw Exception("Output file is empty after write")
                }

                val outputUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", outputFile
                )

                DatabaseHelper.getInstance(context).addConversion(
                    ConversionHistory(
                        fileName = fileName,
                        inputPath = inputUri.toString(),
                        outputPath = outputFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        status = "completed"
                    )
                )

                Log.d(TAG, "PDF → Word conversion complete")
                withContext(Dispatchers.Main) { onSuccess(outputUri) }

            } catch (e: Exception) {
                Log.e(TAG, "PDF → Word failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Conversion failed")
                }
            }
        }
    }

    fun convertWordToPdf(
        context: Context,
        inputUri: Uri,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting Word → PDF conversion")

                val fileName = getFileName(context, inputUri)
                Log.d(TAG, "Input file: $fileName")

                val baseName = fileName.substringBeforeLast('.')
                val outputFileName = "$baseName.pdf"
                val outputFile = File(context.filesDir, outputFileName)
                Log.d(TAG, "Output path: ${outputFile.absolutePath}")

                val inputBytes = context.contentResolver.openInputStream(inputUri)?.use {
                    it.readBytes()
                } ?: throw Exception("Cannot open the selected file")

                Log.d(TAG, "Read ${inputBytes.size} bytes from input")

                val extractedText = extractTextFromDocx(inputBytes)
                Log.d(TAG, "Extracted ${extractedText.length} chars of text")

                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted from this Word document.")
                }

                val pdfBytes = buildPdfFromText(extractedText)
                Log.d(TAG, "Built PDF: ${pdfBytes.size} bytes")

                outputFile.writeBytes(pdfBytes)
                Log.d(TAG, "Written to: ${outputFile.absolutePath}")

                if (outputFile.length() == 0L) {
                    throw Exception("Output file is empty after write")
                }

                val outputUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.provider", outputFile
                )

                DatabaseHelper.getInstance(context).addConversion(
                    ConversionHistory(
                        fileName = fileName,
                        inputPath = inputUri.toString(),
                        outputPath = outputFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        status = "completed"
                    )
                )

                Log.d(TAG, "Word → PDF conversion complete")
                withContext(Dispatchers.Main) { onSuccess(outputUri) }

            } catch (e: Exception) {
                Log.e(TAG, "Word → PDF failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Conversion failed")
                }
            }
        }
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            val pdfReader = PdfReader(bytes.inputStream())
            val pdfDocument = PdfDocument(pdfReader)
            val pageCount = pdfDocument.numberOfPages

            for (i in 1..pageCount) {
                val page = pdfDocument.getPage(i)
                val text = page.text
                sb.append(text).append("\n")
            }

            pdfDocument.close()
        } catch (e: Exception) {
            Log.w(TAG, "PDF text extraction failed: ${e.message}")
            throw Exception("Failed to extract text from PDF: ${e.message}")
        }
        return sb.toString()
    }

    private fun extractTextFromDocx(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            val document = XWPFDocument(bytes.inputStream())
            for (paragraph in document.paragraphs) {
                sb.append(paragraph.text).append("\n")
            }
            for (table in document.tables) {
                for (row in table.rows) {
                    for (cell in row.cells) {
                        sb.append(cell.text).append(" ")
                    }
                    sb.append("\n")
                }
            }
            document.close()
        } catch (e: Exception) {
            Log.w(TAG, "DOCX text extraction failed: ${e.message}")
            throw Exception("Failed to extract text from Word document: ${e.message}")
        }
        return sb.toString()
    }

    private fun buildDocxFromText(text: String, sourceFile: String): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            val document = XWPFDocument()

            val titlePara = document.createParagraph()
            titlePara.text = "Converted from: $sourceFile"
            val titleRun = titlePara.runs[0]
            titleRun.bold = true
            titleRun.fontSize = 14

            document.createParagraph().text = ""

            for (line in text.lines()) {
                if (line.isNotBlank()) {
                    val para = document.createParagraph()
                    para.text = line
                }
            }

            document.write(baos)
            document.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build DOCX: ${e.message}", e)
            throw Exception("Failed to create Word document: ${e.message}")
        }
        return baos.toByteArray()
    }

    private fun buildPdfFromText(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            val pdfWriter = PdfWriter(baos)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            for (line in text.lines()) {
                if (line.isNotBlank()) {
                    document.add(Paragraph(line))
                }
            }

            document.close()
            pdfDocument.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build PDF: ${e.message}", e)
            throw Exception("Failed to create PDF: ${e.message}")
        }
        return baos.toByteArray()
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "document"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && cursor.moveToFirst()) name = cursor.getString(col) ?: "document"
        }
        return name
    }

    fun openDocument(context: Context, path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Log.w(TAG, "openDocument: file not found at $path")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val mime = when (path.substringAfterLast('.').lowercase()) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else -> "*/*"
            }
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }, "Open with"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "openDocument failed: ${e.message}", e)
        }
    }
}
