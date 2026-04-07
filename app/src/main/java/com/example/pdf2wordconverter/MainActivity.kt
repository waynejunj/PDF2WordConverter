package com.example.pdf2wordconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var selectedFileText: TextView
    private lateinit var selectFileButton: MaterialButton
    private lateinit var convertButton: MaterialButton
    private lateinit var historyButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var selectedFileUri: Uri? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            val fileName = getFileName(it)
            selectedFileText.text = fileName
            convertButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        selectedFileText = findViewById(R.id.selectedFileText)
        selectFileButton = findViewById(R.id.selectFileButton)
        convertButton = findViewById(R.id.convertButton)
        historyButton = findViewById(R.id.historyButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
    }

    private fun setupClickListeners() {
        selectFileButton.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        convertButton.setOnClickListener {
            selectedFileUri?.let { uri ->
                startConversion(uri)
            }
        }

        historyButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startConversion(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        convertButton.isEnabled = false
        selectFileButton.isEnabled = false

        val fileName = getFileName(uri)
        val conversionType = when {
            fileName.endsWith(".pdf") -> {
                statusText.text = "Converting PDF to Word..."
                "pdf_to_word"
            }
            fileName.endsWith(".docx") || fileName.endsWith(".doc") -> {
                statusText.text = "Converting Word to PDF..."
                "word_to_pdf"
            }
            else -> {
                statusText.text = "Unsupported file type"
                "unknown"
            }
        }

        when (conversionType) {
            "pdf_to_word" -> {
                ConversionManager.convertPdfToWord(
                    this,
                    uri,
                    onSuccess = { outputUri ->
                        handleConversionSuccess(outputUri, fileName, uri)
                    },
                    onError = { error ->
                        handleConversionError(error)
                    }
                )
            }
            "word_to_pdf" -> {
                ConversionManager.convertWordToPdf(
                    this,
                    uri,
                    onSuccess = { outputUri ->
                        handleConversionSuccess(outputUri, fileName, uri)
                    },
                    onError = { error ->
                        handleConversionError(error)
                    }
                )
            }
            else -> {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "Unsupported file type"
                    Toast.makeText(this, "Supported formats: PDF, DOCX, DOC", Toast.LENGTH_LONG).show()
                    convertButton.isEnabled = true
                    selectFileButton.isEnabled = true
                }
            }
        }
    }

    private fun handleConversionSuccess(outputUri: Uri, fileName: String, inputUri: Uri) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            statusText.text = "Conversion successful!"
            val extension = if (fileName.endsWith(".pdf")) "docx" else "pdf"
            Toast.makeText(this, "File converted to $extension!", Toast.LENGTH_LONG).show()

            convertButton.isEnabled = true
            selectFileButton.isEnabled = true

            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("OUTPUT_URI", outputUri.toString())
                putExtra("FILE_NAME", fileName)
                putExtra("OUTPUT_PATH", getCacheFilePath(fileName))
            }
            startActivity(intent)
        }
    }

    private fun handleConversionError(error: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            statusText.text = "Conversion failed"
            Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            convertButton.isEnabled = true
            selectFileButton.isEnabled = true
        }
    }

    private fun getCacheFilePath(fileName: String): String {
        val outputFileName = when {
            fileName.endsWith(".pdf") -> fileName.replace(".pdf", ".docx")
            fileName.endsWith(".docx") -> fileName.replace(".docx", ".pdf")
            fileName.endsWith(".doc") -> fileName.replace(".doc", ".pdf")
            else -> fileName
        }
        return "${filesDir}/$outputFileName"
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "Unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        return fileName
    }
}