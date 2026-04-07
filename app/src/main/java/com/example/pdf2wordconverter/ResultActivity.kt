package com.example.pdf2wordconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File

class ResultActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var resultText: TextView
    private lateinit var openButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var convertReverseButton: MaterialButton
    private lateinit var convertAnotherButton: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var outputUri: Uri? = null
    private var outputPath: String? = null
    private var fileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Conversion Complete"

        resultText = findViewById(R.id.resultText)
        openButton = findViewById(R.id.openButton)
        downloadButton = findViewById(R.id.downloadButton)
        shareButton = findViewById(R.id.shareButton)
        convertReverseButton = findViewById(R.id.convertReverseButton)
        convertAnotherButton = findViewById(R.id.convertAnotherButton)
        progressBar = findViewById(R.id.progressBar)

        val outputUriString = intent.getStringExtra("OUTPUT_URI")
        fileName = intent.getStringExtra("FILE_NAME") ?: "document"
        outputPath = intent.getStringExtra("OUTPUT_PATH")

        outputUri = outputUriString?.let { Uri.parse(it) }

        resultText.text = "Successfully converted:\n$fileName"

        determineConvertReverseButtonText()
        setupClickListeners()
    }

    private fun determineConvertReverseButtonText() {
        val buttonText = when {
            fileName?.endsWith(".pdf") == true -> "Convert to Word"
            fileName?.endsWith(".docx") == true || fileName?.endsWith(".doc") == true -> "Convert to PDF"
            else -> "Convert Back"
        }
        convertReverseButton.text = buttonText
    }

    private fun setupClickListeners() {
        openButton.setOnClickListener {
            outputPath?.let { path ->
                ConversionManager.openDocument(this, path)
            }
        }

        downloadButton.setOnClickListener {
            downloadFile()
        }

        shareButton.setOnClickListener {
            outputUri?.let { uri ->
                shareDocument(uri)
            }
        }

        convertReverseButton.setOnClickListener {
            performReverseConversion()
        }

        convertAnotherButton.setOnClickListener {
            finish()
        }
    }

    private fun downloadFile() {
        if (outputPath == null) {
            Toast.makeText(this, "No file to download", Toast.LENGTH_SHORT).show()
            return
        }

        val sourceFile = File(outputPath!!)
        val downloadFileName = sourceFile.name

        progressBar.visibility = View.VISIBLE
        downloadButton.isEnabled = false

        ConversionManager.downloadFile(
            this,
            sourceFile,
            downloadFileName,
            onSuccess = {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                    Toast.makeText(this, "File downloaded to Downloads/PDFConverter", Toast.LENGTH_LONG).show()
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    downloadButton.isEnabled = true
                    Toast.makeText(this, "Download failed: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun performReverseConversion() {
        outputUri?.let { uri ->
            progressBar.visibility = View.VISIBLE
            convertReverseButton.isEnabled = false

            if (fileName?.endsWith(".pdf") == true) {
                Toast.makeText(this, "PDF to Word reverse conversion not yet supported", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                convertReverseButton.isEnabled = true
            } else if (fileName?.endsWith(".docx") == true || fileName?.endsWith(".doc") == true) {
                ConversionManager.convertWordToPdf(
                    this,
                    uri,
                    onSuccess = { newOutputUri ->
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            convertReverseButton.isEnabled = true
                            Toast.makeText(this, "Converted to PDF successfully!", Toast.LENGTH_LONG).show()

                            val intent = Intent(this, ResultActivity::class.java).apply {
                                putExtra("OUTPUT_URI", newOutputUri.toString())
                                putExtra("FILE_NAME", fileName?.replace(".docx", ".pdf")?.replace(".doc", ".pdf"))
                                putExtra("OUTPUT_PATH", intent.getStringExtra("OUTPUT_PATH"))
                            }
                            startActivity(intent)
                            finish()
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            convertReverseButton.isEnabled = true
                            Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }

    private fun shareDocument(uri: Uri) {
        val mimeType = when {
            fileName?.endsWith(".pdf") == true -> "application/pdf"
            else -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Document"))
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
