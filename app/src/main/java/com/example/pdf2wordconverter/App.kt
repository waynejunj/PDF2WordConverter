package com.example.pdf2wordconverter

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            PDFBoxResourceLoader.init(applicationContext)
            Log.d("PDF2Word", "PDFBox initialized successfully")
        } catch (e: Exception) {
            Log.e("PDF2Word", "PDFBox init failed: ${e.message}", e)
        }
    }
}
