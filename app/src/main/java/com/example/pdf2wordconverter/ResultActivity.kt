package com.example.pdf2wordconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ResultActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var resultText: TextView
    private lateinit var openButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var convertAnotherButton: MaterialButton

    private var outputUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Conversion Complete"

        resultText = findViewById(R.id.resultText)
        openButton = findViewById(R.id.openButton)
        shareButton = findViewById(R.id.shareButton)
        convertAnotherButton = findViewById(R.id.convertAnotherButton)

        val outputUriString = intent.getStringExtra("OUTPUT_URI")
        val fileName = intent.getStringExtra("FILE_NAME") ?: "document"

        outputUri = outputUriString?.let { Uri.parse(it) }

        resultText.text = "Successfully converted:\n$fileName"

        setupClickListeners()
    }

    private fun setupClickListeners() {
        openButton.setOnClickListener {
            outputUri?.let { uri ->
                ConversionManager.openDocument(this, uri.toString())
            }
        }

        shareButton.setOnClickListener {
            outputUri?.let { uri ->
                shareDocument(uri)
            }
        }

        convertAnotherButton.setOnClickListener {
            finish()
        }
    }

    private fun shareDocument(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Word Document"))
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
