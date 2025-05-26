package com.federicogiordano.miroriento.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

actual object FileSystem {
    private val fileManager = NSFileManager.defaultManager

    private fun getDocumentsDirectory(): String? {
        return NSSearchPathForDirectoriesInDomains(
            directory = NSDocumentDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true
        ).firstOrNull() as? String
    }

    actual fun initialize(context: Any?) {
        println("FileSystem (iOS) initialized.")
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readTextFromFile(fileName: String): String? {
        val documentsDirectory = getDocumentsDirectory() ?: run {
            println("Error: Could not find documents directory on iOS.")
            return null
        }
        val filePath = "$documentsDirectory/$fileName"

        return try {
            if (fileManager.fileExistsAtPath(filePath)) {
                NSString.stringWithContentsOfFile(filePath, encoding = NSUTF8StringEncoding, error = null)
            } else {
                println("File not found on iOS: $filePath")
                null
            }
        } catch (e: Exception) {
            println("Error reading file $fileName on iOS: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeTextToFile(fileName: String, content: String): Boolean {
        val documentsDirectory = getDocumentsDirectory() ?: run {
            println("Error: Could not find documents directory on iOS.")
            return false
        }
        val filePath = "$documentsDirectory/$fileName"
        val nsStringContent = content as NSString

        return try {
            nsStringContent.writeToFile(filePath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            println("Successfully wrote to file on iOS: $filePath")
            true
        } catch (e: Exception) {
            println("Error writing file $fileName on iOS: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}