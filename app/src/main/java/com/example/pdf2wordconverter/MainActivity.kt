package com.example.pdf2wordconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var cardPdfToWord: MaterialCardView
    private lateinit var cardWordToPdf: MaterialCardView
    private lateinit var fileCard: MaterialCardView
    private lateinit var noFileLayout: LinearLayout
    private lateinit var selectedFileLayout: LinearLayout
    private lateinit var selectedFileText: TextView
    private lateinit var selectFileButton: MaterialButton
    private lateinit var convertButton: MaterialButton
    private lateinit var historyButton: MaterialButton
    private lateinit var progressCard: MaterialCardView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var selectedFileUri: Uri? = null
    private var conversionMode: String = MODE_PDF_TO_WORD

    companion object {
        const val MODE_PDF_TO_WORD = "pdf_to_word"
        const val MODE_WORD_TO_PDF = "word_to_pdf"
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            val fileName = getFileName(it)
            if (!isValidFileForMode(fileName)) {
                val expected = if (conversionMode == MODE_PDF_TO_WORD) "PDF file (.pdf)" else "Word file (.doc or .docx)"
                Toast.makeText(this, "Please select a $expected", Toast.LENGTH_LONG).show()
                return@let
            }
            selectedFileText.text = fileName
            noFileLayout.visibility = View.GONE
            selectedFileLayout.visibility = View.VISIBLE
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
        selectMode(MODE_PDF_TO_WORD)
    }

    private fun initializeViews() {
        cardPdfToWord = findViewById(R.id.cardPdfToWord)
        cardWordToPdf = findViewById(R.id.cardWordToPdf)
        fileCard = findViewById(R.id.fileCard)
        noFileLayout = findViewById(R.id.noFileLayout)
        selectedFileLayout = findViewById(R.id.selectedFileLayout)
        selectedFileText = findViewById(R.id.selectedFileText)
        selectFileButton = findViewById(R.id.selectFileButton)
        convertButton = findViewById(R.id.convertButton)
        historyButton = findViewById(R.id.historyButton)
        progressCard = findViewById(R.id.progressCard)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
    }

    private fun setupClickListeners() {
        cardPdfToWord.setOnClickListener {
            selectMode(MODE_PDF_TO_WORD)
        }

        cardWordToPdf.setOnClickListener {
            selectMode(MODE_WORD_TO_PDF)
        }

        selectFileButton.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        convertButton.setOnClickListener {
            selectedFileUri?.let { uri -> startConversion(uri) }
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun selectMode(mode: String) {
        conversionMode = mode

        val selectedElevation = 6f
        val unselectedElevation = 2f

        if (mode == MODE_PDF_TO_WORD) {
            cardPdfToWord.cardElevation = selectedElevation
            cardPdfToWord.alpha = 1.0f
            cardWordToPdf.cardElevation = unselectedElevation
            cardWordToPdf.alpha = 0.55f
            convertButton.text = "Convert PDF → Word"
            selectFileButton.text = "Browse & Select PDF File"
        } else {
            cardWordToPdf.cardElevation = selectedElevation
            cardWordToPdf.alpha = 1.0f
            cardPdfToWord.cardElevation = unselectedElevation
            cardPdfToWord.alpha = 0.55f
            convertButton.text = "Convert Word → PDF"
            selectFileButton.text = "Browse & Select Word File"
        }

        selectedFileUri = null
        noFileLayout.visibility = View.VISIBLE
        selectedFileLayout.visibility = View.GONE
        convertButton.isEnabled = false
    }

    private fun isValidFileForMode(fileName: String): Boolean {
        return if (conversionMode == MODE_PDF_TO_WORD) {
            fileName.lowercase().endsWith(".pdf")
        } else {
            fileName.lowercase().endsWith(".doc") || fileName.lowercase().endsWith(".docx")
        }
    }

    private fun startConversion(uri: Uri) {
        progressCard.visibility = View.VISIBLE
        convertButton.isEnabled = false
        selectFileButton.isEnabled = false
        cardPdfToWord.isClickable = false
        cardWordToPdf.isClickable = false

        val fileName = getFileName(uri)

        when (conversionMode) {
            MODE_PDF_TO_WORD -> {
                statusText.text = "Converting PDF to Word..."
                ConversionManager.convertPdfToWord(
                    this, uri,
                    onSuccess = { outputUri -> handleConversionSuccess(outputUri, fileName) },
                    onError = { error -> handleConversionError(error) }
                )
            }
            MODE_WORD_TO_PDF -> {
                statusText.text = "Converting Word to PDF..."
                ConversionManager.convertWordToPdf(
                    this, uri,
                    onSuccess = { outputUri -> handleConversionSuccess(outputUri, fileName) },
                    onError = { error -> handleConversionError(error) }
                )
            }
        }
    }

    private fun handleConversionSuccess(outputUri: Uri, fileName: String) {
        runOnUiThread {
            progressCard.visibility = View.GONE
            convertButton.isEnabled = true
            selectFileButton.isEnabled = true
            cardPdfToWord.isClickable = true
            cardWordToPdf.isClickable = true

            val outputExtension = if (conversionMode == MODE_PDF_TO_WORD) "docx" else "pdf"
            val outputFileName = when {
                fileName.lowercase().endsWith(".pdf") -> fileName.dropLast(4) + ".docx"
                fileName.lowercase().endsWith(".docx") -> fileName.dropLast(5) + ".pdf"
                fileName.lowercase().endsWith(".doc") -> fileName.dropLast(4) + ".pdf"
                else -> "$fileName.$outputExtension"
            }

            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("OUTPUT_URI", outputUri.toString())
                putExtra("FILE_NAME", fileName)
                putExtra("OUTPUT_FILE_NAME", outputFileName)
                putExtra("OUTPUT_PATH", getOutputFilePath(fileName))
                putExtra("CONVERSION_MODE", conversionMode)
            }
            startActivity(intent)
        }
    }

    private fun handleConversionError(error: String) {
        runOnUiThread {
            progressCard.visibility = View.GONE
            convertButton.isEnabled = true
            selectFileButton.isEnabled = true
            cardPdfToWord.isClickable = true
            cardWordToPdf.isClickable = true
            Toast.makeText(this, "Conversion failed: $error", Toast.LENGTH_LONG).show()
        }
    }

    private fun getOutputFilePath(fileName: String): String {
        val outputFileName = when {
            fileName.lowercase().endsWith(".pdf") -> fileName.dropLast(4) + ".docx"
            fileName.lowercase().endsWith(".docx") -> fileName.dropLast(5) + ".pdf"
            fileName.lowercase().endsWith(".doc") -> fileName.dropLast(4) + ".pdf"
            else -> fileName
        }
        return "${filesDir}/$outputFileName"
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "document"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex) ?: "document"
            }
        }
        return fileName
    }
}
