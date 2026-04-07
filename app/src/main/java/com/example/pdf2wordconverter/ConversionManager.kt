package com.example.pdf2wordconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

object ConversionManager {

    private const val TAG = "PDF2Word"

    // ─────────────────────────────────────────────
    // PDF → DOCX
    // ─────────────────────────────────────────────

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
                    throw Exception(
                        "No text could be extracted. This PDF may be image/scan-based " +
                        "(OCR not supported). Try a text-based PDF."
                    )
                }

                val docxBytes = buildDocx(extractedText, fileName)
                Log.d(TAG, "Built DOCX: ${docxBytes.size} bytes")

                outputFile.writeBytes(docxBytes)
                Log.d(TAG, "Written to: ${outputFile.absolutePath}")

                if (outputFile.length() == 0L) {
                    throw Exception("Output file is empty after write — storage issue?")
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

    // ─────────────────────────────────────────────
    // DOCX / DOC → PDF
    // ─────────────────────────────────────────────

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

                val ext = fileName.substringAfterLast('.').lowercase()
                val baseName = fileName.substringBeforeLast('.')
                val outputFileName = "$baseName.pdf"
                val outputFile = File(context.filesDir, outputFileName)
                Log.d(TAG, "Output path: ${outputFile.absolutePath}")

                val inputBytes = context.contentResolver.openInputStream(inputUri)?.use {
                    it.readBytes()
                } ?: throw Exception("Cannot open the selected file")

                Log.d(TAG, "Read ${inputBytes.size} bytes from input")

                val extractedText = when (ext) {
                    "docx" -> extractTextFromDocx(inputBytes)
                    else   -> extractTextFromDoc(inputBytes)
                }
                Log.d(TAG, "Extracted ${extractedText.length} chars of text")

                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted from this Word document.")
                }

                val pdfBytes = buildPdf(extractedText)
                Log.d(TAG, "Built PDF: ${pdfBytes.size} bytes")

                outputFile.writeBytes(pdfBytes)
                Log.d(TAG, "Written to: ${outputFile.absolutePath}")

                if (outputFile.length() == 0L) {
                    throw Exception("Output file is empty after write — storage issue?")
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

    // ─────────────────────────────────────────────
    // PDF text extraction (pdfbox-android)
    // ─────────────────────────────────────────────

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val doc = PDDocument.load(bytes)
        return doc.use { d ->
            if (d.isEncrypted) throw Exception("Encrypted PDFs are not supported")
            val stripper = PDFTextStripper()
            stripper.getText(d)
        }
    }

    // ─────────────────────────────────────────────
    // DOCX text extraction (ZIP + XML)
    // ─────────────────────────────────────────────

    private fun extractTextFromDocx(bytes: ByteArray): String {
        val sb = StringBuilder()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    sb.append(parseWordXml(xml))
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun parseWordXml(xml: String): String {
        val sb = StringBuilder()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
            val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            val paragraphs = doc.getElementsByTagNameNS(ns, "p")
            for (i in 0 until paragraphs.length) {
                val para = paragraphs.item(i) as org.w3c.dom.Element
                val runs = para.getElementsByTagNameNS(ns, "t")
                for (j in 0 until runs.length) {
                    sb.append(runs.item(j).textContent)
                }
                sb.append("\n")
            }
        } catch (e: Exception) {
            Log.w(TAG, "XML parse failed, using regex fallback: ${e.message}")
            sb.append(xml.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " "))
        }
        return sb.toString()
    }

    // ─────────────────────────────────────────────
    // Legacy .doc  (best-effort printable-ASCII scan)
    // ─────────────────────────────────────────────

    private fun extractTextFromDoc(bytes: ByteArray): String {
        val sb = StringBuilder()
        val run = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E || c == 0x0A || c == 0x0D || c == 0x09) {
                run.append(c.toChar())
            } else {
                if (run.length >= 5) sb.append(run)
                run.clear()
            }
        }
        if (run.length >= 5) sb.append(run)
        val result = sb.toString().replace(Regex("[ \\t]{4,}"), "  ").trim()
        if (result.isBlank()) {
            throw Exception(".doc extraction failed. Please resave as .docx and try again.")
        }
        return result
    }

    // ─────────────────────────────────────────────
    // Build DOCX (ZIP of XML files)
    // ─────────────────────────────────────────────

    private fun buildDocx(text: String, sourceFile: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->

            fun writeEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.trimIndent().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            writeEntry("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>
            """)

            writeEntry("_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="word/document.xml"/>
                </Relationships>
            """)

            writeEntry("word/_rels/document.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
                    Target="styles.xml"/>
                </Relationships>
            """)

            writeEntry("word/styles.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                    <w:name w:val="Normal"/>
                    <w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr>
                    <w:rPr>
                      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                      <w:sz w:val="24"/>
                    </w:rPr>
                  </w:style>
                </w:styles>
            """)

            // word/document.xml
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(buildDocumentXml(text, sourceFile).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun buildDocumentXml(text: String, sourceFile: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n")
        sb.append("  <w:body>\n")

        // Title paragraph
        sb.append("    <w:p>\n")
        sb.append("      <w:pPr><w:pStyle w:val=\"Normal\"/></w:pPr>\n")
        sb.append("      <w:r><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr>")
        sb.append("<w:t>${xmlEscape("Converted from: $sourceFile")}</w:t></w:r>\n")
        sb.append("    </w:p>\n")

        // Empty separator
        sb.append("    <w:p><w:r><w:t></w:t></w:r></w:p>\n")

        // Content paragraphs
        for (line in text.lines()) {
            sb.append("    <w:p>\n")
            sb.append("      <w:r>\n")
            sb.append("        <w:t xml:space=\"preserve\">${xmlEscape(sanitizeForXml(line))}</w:t>\n")
            sb.append("      </w:r>\n")
            sb.append("    </w:p>\n")
        }

        sb.append("    <w:sectPr/>\n")
        sb.append("  </w:body>\n")
        sb.append("</w:document>")
        return sb.toString()
    }

    // ─────────────────────────────────────────────
    // Build PDF from plain text
    // ─────────────────────────────────────────────

    private fun buildPdf(text: String): ByteArray {
        val pageWidth    = 595f
        val pageHeight   = 842f
        val marginLeft   = 60f
        val marginTop    = 60f
        val marginBottom = 60f
        val fontSize     = 12f
        val lineHeight   = fontSize * 1.5f
        val charsPerLine = 90
        val linesPerPage = ((pageHeight - marginTop - marginBottom) / lineHeight).toInt()

        // Word-wrap
        val wrappedLines = mutableListOf<String>()
        for (raw in text.lines()) {
            val line = sanitizeForPdf(raw)
            if (line.length <= charsPerLine) { wrappedLines.add(line); continue }
            var rem = line
            while (rem.length > charsPerLine) {
                val br = rem.lastIndexOf(' ', charsPerLine).takeIf { it > 0 } ?: charsPerLine
                wrappedLines.add(rem.substring(0, br))
                rem = rem.substring(br).trimStart()
            }
            if (rem.isNotEmpty()) wrappedLines.add(rem)
        }

        if (wrappedLines.isEmpty()) wrappedLines.add("(empty document)")

        val pages = wrappedLines.chunked(linesPerPage)

        // PDF object IDs: 1=Catalog 2=Pages 3=Font 4=content 5=page ...
        val catalogId = 1; val pagesId = 2; val fontId = 3
        var nextId = fontId + 1

        data class Obj(val id: Int, val data: ByteArray)
        val objects = mutableListOf<Obj>()
        val pageIds = mutableListOf<Int>()

        objects.add(Obj(fontId,
            "$fontId 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
                .toByteArray(Charsets.ISO_8859_1)))

        for (pageLines in pages) {
            val cId = nextId++
            val pId = nextId++
            pageIds.add(pId)

            val stream = buildStream(pageLines, marginLeft, pageHeight, marginTop, lineHeight, fontSize)
            val streamBytes = stream.toByteArray(Charsets.ISO_8859_1)

            val cHeader = "$cId 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n"
            val cFooter = "endstream\nendobj\n"
            objects.add(Obj(cId,
                cHeader.toByteArray(Charsets.ISO_8859_1) + streamBytes +
                cFooter.toByteArray(Charsets.ISO_8859_1)))

            val pageDict = "$pId 0 obj\n" +
                "<< /Type /Page /Parent $pagesId 0 R\n" +
                "   /MediaBox [0 0 595 842]\n" +
                "   /Contents $cId 0 R\n" +
                "   /Resources << /Font << /F1 $fontId 0 R >> >> >>\n" +
                "endobj\n"
            objects.add(Obj(pId, pageDict.toByteArray(Charsets.ISO_8859_1)))
        }

        val pageRefs  = pageIds.joinToString(" ") { "$it 0 R" }
        val catalogStr = "$catalogId 0 obj\n<< /Type /Catalog /Pages $pagesId 0 R >>\nendobj\n"
        val pagesStr   = "$pagesId 0 obj\n<< /Type /Pages /Kids [$pageRefs] /Count ${pageIds.size} >>\nendobj\n"

        val sorted = (listOf(
            Obj(catalogId, catalogStr.toByteArray(Charsets.ISO_8859_1)),
            Obj(pagesId,   pagesStr.toByteArray(Charsets.ISO_8859_1))
        ) + objects).sortedBy { it.id }

        val header = "%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1)
        val offsets = mutableMapOf<Int, Int>()
        var cursor = header.size
        for (obj in sorted) { offsets[obj.id] = cursor; cursor += obj.data.size }

        val xref = buildString {
            append("xref\n0 ${sorted.size + 1}\n")
            append("0000000000 65535 f \n")
            for (id in 1..sorted.size) {
                append((offsets[id] ?: 0).toString().padStart(10, '0'))
                append(" 00000 n \n")
            }
            append("trailer\n<< /Size ${sorted.size + 1} /Root $catalogId 0 R >>\n")
            append("startxref\n$cursor\n%%EOF\n")
        }

        val baos = ByteArrayOutputStream()
        baos.write(header)
        for (obj in sorted) baos.write(obj.data)
        baos.write(xref.toByteArray(Charsets.ISO_8859_1))
        return baos.toByteArray()
    }

    private fun buildStream(
        lines: List<String>, x0: Float, ph: Float, mt: Float, lh: Float, fs: Float
    ): String {
        val sb = StringBuilder()
        sb.append("BT\n/F1 ${fs.toInt()} Tf\n")
        lines.forEachIndexed { i, line ->
            val y = ph - mt - i * lh
            sb.append("1 0 0 1 ${"%.2f".format(x0)} ${"%.2f".format(y)} Tm\n")
            sb.append("(${pdfEscape(line)}) Tj\n")
        }
        sb.append("ET\n")
        return sb.toString()
    }

    // ─────────────────────────────────────────────
    // Sanitise / Escape helpers
    // ─────────────────────────────────────────────

    /** Remove characters invalid in XML 1.0 */
    private fun sanitizeForXml(s: String): String = s.replace(
        Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), ""
    )

    /** Keep only printable Latin-1 for PDF strings */
    private fun sanitizeForPdf(s: String): String = s.filter { c ->
        c.code in 0x20..0x7E || c == '\t'
    }

    private fun xmlEscape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun pdfEscape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '('  -> sb.append("\\(")
                ')'  -> sb.append("\\)")
                '\\' -> sb.append("\\\\")
                else -> if (c.code in 32..126) sb.append(c)
            }
        }
        return sb.toString()
    }

    // ─────────────────────────────────────────────
    // File utilities
    // ─────────────────────────────────────────────

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
            if (!file.exists()) { Log.w(TAG, "openDocument: file not found at $path"); return }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val mime = when (path.substringAfterLast('.').lowercase()) {
                "pdf"  -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else   -> "*/*"
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
