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
            println("FileSystem (Android) inizializzato con contesto Android.")
        } else if (this.appContext != null) {
            println("FileSystem (Android) già inizializzato.")
        } else if (context == null) {
            println("Avviso: FileSystem (Android) initialize chiamato con contesto null, e non ancora inizializzato. Le operazioni sui file potrebbero fallire.")
        } else {
            println("Avviso: FileSystem (Android) initialize chiamato con non-Context (${context::class.simpleName}), e non ancora inizializzato.")
        }
    }

    private fun getFilesDir(): File? {
        if (appContext == null) {
            println("Errore: FileSystem (Android per i file interni) non inizializzato. Chiamare prima initialize(context).")
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
                println("File non trovato su Android: ${file.absolutePath}")
                null
            }
        } catch (e: IOException) {
            println("Errore durante la lettura del file $fileName su Android: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    actual fun writeTextToFile(fileName: String, content: String): Boolean {
        val filesDir = getFilesDir() ?: return false
        val file = File(filesDir, fileName)
        return try {
            file.writeText(content)
            println("Scrittura del file completata con successo su Android: ${file.absolutePath}")
            true
        } catch (e: IOException) {
            println("Errore durante la scrittura del file $fileName su Android: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}