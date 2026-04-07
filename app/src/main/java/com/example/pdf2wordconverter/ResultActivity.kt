package com.example.pdf2wordconverter

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileInputStream

class ResultActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var resultText: TextView
    private lateinit var outputFileNameText: TextView
    private lateinit var openButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var convertAnotherButton: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var outputUri: Uri? = null
    private var outputPath: String? = null
    private var inputFileName: String? = null
    private var outputFileName: String? = null
    private var conversionMode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        resultText = findViewById(R.id.resultText)
        outputFileNameText = findViewById(R.id.outputFileNameText)
        openButton = findViewById(R.id.openButton)
        downloadButton = findViewById(R.id.downloadButton)
        shareButton = findViewById(R.id.shareButton)
        convertAnotherButton = findViewById(R.id.convertAnotherButton)
        progressBar = findViewById(R.id.progressBar)

        val outputUriString = intent.getStringExtra("OUTPUT_URI")
        inputFileName = intent.getStringExtra("FILE_NAME") ?: "document"
        outputFileName = intent.getStringExtra("OUTPUT_FILE_NAME") ?: "converted_file"
        outputPath = intent.getStringExtra("OUTPUT_PATH")
        conversionMode = intent.getStringExtra("CONVERSION_MODE")

        outputUri = outputUriString?.let { Uri.parse(it) }

        resultText.text = inputFileName
        outputFileNameText.text = outputFileName

        setupClickListeners()
    }

    private fun setupClickListeners() {
        downloadButton.setOnClickListener {
            downloadFile()
        }

        openButton.setOnClickListener {
            outputPath?.let { path -> ConversionManager.openDocument(this, path) }
                ?: Toast.makeText(this, "File not available", Toast.LENGTH_SHORT).show()
        }

        shareButton.setOnClickListener {
            outputUri?.let { uri -> shareDocument(uri) }
                ?: Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
        }

        convertAnotherButton.setOnClickListener {
            finish()
        }
    }

    private fun downloadFile() {
        val path = outputPath
        val name = outputFileName

        if (path == null || name == null) {
            Toast.makeText(this, "No converted file available to download", Toast.LENGTH_SHORT).show()
            return
        }

        val sourceFile = File(path)
        if (!sourceFile.exists()) {
            Toast.makeText(this, "Converted file not found", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        downloadButton.isEnabled = false

        Thread {
            try {
                val mimeType = getMimeType(name)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDF2Word")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }

                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Could not create file in Downloads")

                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(output)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                } else {
                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "PDF2Word"
                    )
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()

                    val destFile = File(downloadsDir, name)
                    sourceFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                    Toast.makeText(
                        this,
                        "✅ Saved to Downloads/PDF2Word/$name",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun shareDocument(uri: Uri) {
        val name = outputFileName ?: "document"
        val mimeType = getMimeType(name)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Converted: $name")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.lowercase().endsWith(".pdf") -> "application/pdf"
            fileName.lowercase().endsWith(".docx") ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            fileName.lowercase().endsWith(".doc") -> "application/msword"
            else -> "application/octet-stream"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
