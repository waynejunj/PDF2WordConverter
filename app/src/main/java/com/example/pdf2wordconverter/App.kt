package com.example.pdf2wordconverter

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseHelper.getInstance(this)
    }
}
