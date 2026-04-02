package com.example.pdf2wordconverter

data class ConversionHistory(
    val id: Long = 0,
    val fileName: String,
    val inputPath: String,
    val outputPath: String,
    val timestamp: Long,
    val status: String
)
