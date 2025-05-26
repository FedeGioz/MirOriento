package com.federicogiordano.miroriento.data

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.io.IOException

@SuppressLint("StaticFieldLeak")
actual object FileSystem {
    private var appContext: Context? = null

    actual fun initialize(context: Any?) {
        if (this.appContext == null && context is Context) {
            this.appContext = context.applicationContext
            println("FileSystem (Android) initialized with Android context.")
        } else if (this.appContext != null) {
            println("FileSystem (Android) already initialized.")
        } else if (context == null) {
            println("Warning: FileSystem (Android) initialize called with null context, and not yet initialized. File operations may fail.")
        } else {
            println("Warning: FileSystem (Android) initialize called with non-Context (${context::class.simpleName}), and not yet initialized.")
        }
    }

    private fun getFilesDir(): File? {
        if (appContext == null) {
            println("Error: FileSystem (Android for internal files) not initialized. Call initialize(context) first.")
            return null
        }
        return appContext?.filesDir
    }

    actual fun readTextFromFile(fileName: String): String? {
        val filesDir = getFilesDir() ?: return null
        val file = File(filesDir, fileName)
        return try {
            if (file.exists()) {
                file.readText()
            } else {
                println("File not found on Android: ${file.absolutePath}")
                null
            }
        } catch (e: IOException) {
            println("Error reading file $fileName on Android: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    actual fun writeTextToFile(fileName: String, content: String): Boolean {
        val filesDir = getFilesDir() ?: return false
        val file = File(filesDir, fileName)
        return try {
            file.writeText(content)
            println("Successfully wrote to file on Android: ${file.absolutePath}")
            true
        } catch (e: IOException) {
            println("Error writing file $fileName on Android: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}