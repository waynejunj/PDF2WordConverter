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

    private val pickPdfLauncher = registerForActivityResult(
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
            pickPdfLauncher.launch("application/pdf")
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
        statusText.text = "Converting PDF to Word..."
        convertButton.isEnabled = false
        selectFileButton.isEnabled = false

        ConversionManager.convertPdfToWord(
            this,
            uri,
            onSuccess = { outputUri ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "Conversion successful!"
                    Toast.makeText(this, "PDF converted successfully!", Toast.LENGTH_LONG).show()

                    convertButton.isEnabled = true
                    selectFileButton.isEnabled = true

                    val intent = Intent(this, ResultActivity::class.java).apply {
                        putExtra("OUTPUT_URI", outputUri.toString())
                        putExtra("FILE_NAME", getFileName(uri))
                    }
                    startActivity(intent)
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    statusText.text = "Conversion failed"
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
                    convertButton.isEnabled = true
                    selectFileButton.isEnabled = true
                }
            }
        )
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