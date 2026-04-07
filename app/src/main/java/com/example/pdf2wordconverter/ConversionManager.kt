package com.example.pdf2wordconverter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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

    private var pdfBoxInitialized = false

    private fun initPdfBox(context: Context) {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxInitialized = true
        }
    }

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
                initPdfBox(context)

                val fileName = getFileName(context, inputUri)
                val outputFileName = fileName.removeSuffix(".pdf").removeSuffix(".PDF") + ".docx"
                val outputFile = File(context.filesDir, outputFileName)

                val inputStream = context.contentResolver.openInputStream(inputUri)
                    ?: throw Exception("Cannot open the selected file")

                val extractedText = inputStream.use { stream ->
                    val document = PDDocument.load(stream)
                    document.use { doc ->
                        if (doc.isEncrypted) {
                            throw Exception("Encrypted PDFs are not supported")
                        }
                        val stripper = PDFTextStripper()
                        stripper.getText(doc)
                    }
                }

                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted from this PDF. It may be a scanned/image-based PDF.")
                }

                val docxBytes = buildDocx(extractedText)
                outputFile.writeBytes(docxBytes)

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

                withContext(Dispatchers.Main) { onSuccess(outputUri) }

            } catch (e: Exception) {
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
                val fileName = getFileName(context, inputUri)
                val baseName = fileName.removeSuffix(".docx").removeSuffix(".DOCX")
                    .removeSuffix(".doc").removeSuffix(".DOC")
                val outputFileName = "$baseName.pdf"
                val outputFile = File(context.filesDir, outputFileName)

                val inputStream = context.contentResolver.openInputStream(inputUri)
                    ?: throw Exception("Cannot open the selected file")

                val extractedText = inputStream.use { stream ->
                    val ext = fileName.substringAfterLast('.').lowercase()
                    if (ext == "docx") {
                        extractTextFromDocx(stream.readBytes())
                    } else {
                        extractTextFromDoc(stream.readBytes())
                    }
                }

                if (extractedText.isBlank()) {
                    throw Exception("No text could be extracted from this Word document.")
                }

                val pdfBytes = buildPdf(extractedText)
                outputFile.writeBytes(pdfBytes)

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

                withContext(Dispatchers.Main) { onSuccess(outputUri) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Conversion failed")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Build a real DOCX (ZIP of XML files)
    // ─────────────────────────────────────────────

    private fun buildDocx(text: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->

            // [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // word/_rels/document.xml.rels
            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
    Target="styles.xml"/>
</Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // word/styles.xml  (minimal – Normal paragraph style)
            zip.putNextEntry(ZipEntry("word/styles.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:pPr>
      <w:spacing w:after="160" w:line="276" w:lineRule="auto"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:sz w:val="24"/>
    </w:rPr>
  </w:style>
</w:styles>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // word/document.xml
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(buildDocumentXml(text).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun buildDocumentXml(text: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
""")
        val lines = text.lines()
        for (line in lines) {
            sb.append("    <w:p>\n")
            sb.append("      <w:r>\n")
            sb.append("        <w:t xml:space=\"preserve\">")
            sb.append(xmlEscape(line))
            sb.append("</w:t>\n")
            sb.append("      </w:r>\n")
            sb.append("    </w:p>\n")
        }
        sb.append("    <w:sectPr/>\n")
        sb.append("  </w:body>\n")
        sb.append("</w:document>")
        return sb.toString()
    }

    // ─────────────────────────────────────────────
    // Build a real PDF from plain text
    // ─────────────────────────────────────────────

    private fun buildPdf(text: String): ByteArray {
        val pageWidth  = 595f   // A4 width in points
        val pageHeight = 842f   // A4 height in points
        val marginLeft = 60f
        val marginTop  = 60f
        val marginBottom = 60f
        val fontSize   = 12f
        val lineHeight = fontSize * 1.5f
        val charsPerLine = 90
        val linesPerPage = ((pageHeight - marginTop - marginBottom) / lineHeight).toInt()

        // Word-wrap every input line to fit within charsPerLine
        val wrappedLines = mutableListOf<String>()
        for (inputLine in text.lines()) {
            if (inputLine.length <= charsPerLine) {
                wrappedLines.add(inputLine)
                continue
            }
            var remaining = inputLine
            while (remaining.length > charsPerLine) {
                val breakAt = remaining.lastIndexOf(' ', charsPerLine).takeIf { it > 0 } ?: charsPerLine
                wrappedLines.add(remaining.substring(0, breakAt))
                remaining = remaining.substring(breakAt).trimStart()
            }
            if (remaining.isNotEmpty()) wrappedLines.add(remaining)
        }

        // Chunk into pages
        val pages: List<List<String>> = wrappedLines.chunked(linesPerPage)

        // ── Build every object as a byte array, tracking offsets ──
        val header      = "%PDF-1.4\n"
        val headerBytes = header.toByteArray(Charsets.ISO_8859_1)

        // We need IDs in advance so page dicts can reference content streams
        // IDs: 1=Catalog, 2=Pages, 3=Font, then per page: 4=content,5=page, 6=content,7=page …
        val catalogId = 1
        val pagesId   = 2
        val fontId    = 3

        data class PdfObject(val id: Int, val bytes: ByteArray)

        val allObjects = mutableListOf<PdfObject>()
        val pageIds    = mutableListOf<Int>()
        var nextId     = fontId + 1

        // Font object
        val fontObj = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        allObjects.add(PdfObject(fontId, "$fontId 0 obj\n$fontObj\nendobj\n".toByteArray(Charsets.ISO_8859_1)))

        // Page objects
        for (pageLines in pages) {
            val contentId = nextId++
            val pageId    = nextId++
            pageIds.add(pageId)

            // Content stream using Tm for absolute positioning
            val streamSb = StringBuilder()
            streamSb.append("BT\n/F1 ${"%.2f".format(fontSize)} Tf\n")
            pageLines.forEachIndexed { idx, line ->
                val x = marginLeft
                val y = pageHeight - marginTop - idx * lineHeight
                streamSb.append("1 0 0 1 ${"%.2f".format(x)} ${"%.2f".format(y)} Tm\n")
                streamSb.append("(${pdfStringEscape(line)}) Tj\n")
            }
            streamSb.append("ET\n")
            val streamData = streamSb.toString().toByteArray(Charsets.ISO_8859_1)

            val contentHeader = "$contentId 0 obj\n<< /Length ${streamData.size} >>\nstream\n"
            val contentFooter = "endstream\nendobj\n"
            val contentBytes  = contentHeader.toByteArray(Charsets.ISO_8859_1) +
                    streamData + contentFooter.toByteArray(Charsets.ISO_8859_1)
            allObjects.add(PdfObject(contentId, contentBytes))

            val pageDict = "$pageId 0 obj\n" +
                    "<< /Type /Page /Parent $pagesId 0 R\n" +
                    "   /MediaBox [0 0 ${"%.2f".format(pageWidth)} ${"%.2f".format(pageHeight)}]\n" +
                    "   /Contents $contentId 0 R\n" +
                    "   /Resources << /Font << /F1 $fontId 0 R >> >> >>\n" +
                    "endobj\n"
            allObjects.add(PdfObject(pageId, pageDict.toByteArray(Charsets.ISO_8859_1)))
        }

        val pageRefs = pageIds.joinToString(" ") { "$it 0 R" }

        val catalogStr = "$catalogId 0 obj\n<< /Type /Catalog /Pages $pagesId 0 R >>\nendobj\n"
        val pagesStr   = "$pagesId 0 obj\n" +
                "<< /Type /Pages /Kids [$pageRefs] /Count ${pageIds.size} >>\n" +
                "endobj\n"

        // Sort all objects by ID so xref is ordered
        val sortedObjects = (listOf(
            PdfObject(catalogId, catalogStr.toByteArray(Charsets.ISO_8859_1)),
            PdfObject(pagesId,   pagesStr.toByteArray(Charsets.ISO_8859_1))
        ) + allObjects).sortedBy { it.id }

        // Compute byte offsets from start-of-file
        val offsets = mutableMapOf<Int, Int>()
        var cursor = headerBytes.size
        for (obj in sortedObjects) {
            offsets[obj.id] = cursor
            cursor += obj.bytes.size
        }

        val xrefOffset = cursor
        val totalObjects = sortedObjects.size

        val xref = buildString {
            append("xref\n0 ${totalObjects + 1}\n")
            append("0000000000 65535 f \n")
            for (id in 1..totalObjects) {
                val off = offsets[id] ?: 0
                append(off.toString().padStart(10, '0'))
                append(" 00000 n \n")
            }
            append("trailer\n<< /Size ${totalObjects + 1} /Root $catalogId 0 R >>\n")
            append("startxref\n$xrefOffset\n%%EOF\n")
        }

        val baos = ByteArrayOutputStream()
        baos.write(headerBytes)
        for (obj in sortedObjects) baos.write(obj.bytes)
        baos.write(xref.toByteArray(Charsets.ISO_8859_1))
        return baos.toByteArray()
    }

    // ─────────────────────────────────────────────
    // Extract text from DOCX (ZIP + XML)
    // ─────────────────────────────────────────────

    private fun extractTextFromDocx(bytes: ByteArray): String {
        val sb = StringBuilder()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    sb.append(extractTextFromWordXml(xml))
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString()
    }

    private fun extractTextFromWordXml(xml: String): String {
        val sb = StringBuilder()
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(InputSource(StringReader(xml)))

            val paragraphs = doc.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p"
            )
            for (i in 0 until paragraphs.length) {
                val para = paragraphs.item(i)
                val runs = (para as org.w3c.dom.Element).getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t"
                )
                for (j in 0 until runs.length) {
                    sb.append(runs.item(j).textContent)
                }
                sb.append("\n")
            }
        } catch (e: Exception) {
            // Fallback: strip all XML tags
            sb.append(xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim())
        }
        return sb.toString().trim()
    }

    // ─────────────────────────────────────────────
    // Extract text from legacy .doc (best-effort)
    // ─────────────────────────────────────────────

    private fun extractTextFromDoc(bytes: ByteArray): String {
        // Legacy .doc is a binary OLE2 format; extract printable ASCII runs as best-effort
        val sb = StringBuilder()
        var i = 0
        val run = StringBuilder()
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x20..0x7E || b == 0x0A || b == 0x0D || b == 0x09) {
                run.append(b.toChar())
            } else {
                if (run.length > 4) sb.append(run)
                run.clear()
            }
            i++
        }
        if (run.length > 4) sb.append(run)
        val result = sb.toString().replace(Regex("[ \\t]{4,}"), "  ").trim()
        return result.ifBlank {
            throw Exception("Could not extract text from .doc file. Please convert to .docx first.")
        }
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun pdfStringEscape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '\\' -> sb.append("\\\\")
                '\r' -> sb.append("\\r")
                '\n' -> { }
                else -> if (c.code in 32..126) sb.append(c)
            }
        }
        return sb.toString()
    }

    fun getFileName(context: Context, uri: Uri): String {
        var fileName = "document"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex) ?: "document"
            }
        }
        return fileName
    }

    fun openDocument(context: Context, path: String) {
        try {
            val file = File(path)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val mimeType = when {
                path.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                path.endsWith(".docx", ignoreCase = true) ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
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
