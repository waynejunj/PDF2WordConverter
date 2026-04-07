package com.example.pdf2wordconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ConversionManager {

    fun convertPdfToWord(
        context: Context,
        inputUri: Uri,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.contentResolver.openInputStream(inputUri)
                    ?: throw Exception("Cannot open input file")

                val fileName = getFileName(context, inputUri)
                val outputFileName = fileName.replace(".pdf", ".docx")
                val outputFile = File(context.filesDir, outputFileName)

                inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val pdfData = input.readBytes()
                        val wordData = simulateConversion(pdfData)
                        output.write(wordData)
                    }
                }

                val outputUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    outputFile
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

                withContext(Dispatchers.Main) {
                    onSuccess(outputUri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error occurred")
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
                val inputStream = context.contentResolver.openInputStream(inputUri)
                    ?: throw Exception("Cannot open input file")

                val fileName = getFileName(context, inputUri)
                val outputFileName = fileName.replace(".docx", ".pdf").replace(".doc", ".pdf")
                val outputFile = File(context.filesDir, outputFileName)

                inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val wordData = input.readBytes()
                        val pdfData = simulateReverseConversion(wordData)
                        output.write(pdfData)
                    }
                }

                val outputUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    outputFile
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

                withContext(Dispatchers.Main) {
                    onSuccess(outputUri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error occurred")
                }
            }
        }
    }

    private fun simulateConversion(pdfData: ByteArray): ByteArray {
        Thread.sleep(2000)
        return createMinimalDocx()
    }

    private fun simulateReverseConversion(wordData: ByteArray): ByteArray {
        Thread.sleep(2000)
        return createMinimalPdf()
    }

    private fun createMinimalDocx(): ByteArray {
        return ByteArray(0)
    }

    private fun createMinimalPdf(): ByteArray {
        return ByteArray(0)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = "document.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    fun downloadFile(context: Context, sourceFile: File, fileName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PDFConverter")
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val destinationFile = File(downloadsDir, fileName)
                sourceFile.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Download failed")
                }
            }
        }
    }

    fun openDocument(context: Context, path: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val mimeType = when {
                path.endsWith(".pdf") -> "application/pdf"
                path.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
