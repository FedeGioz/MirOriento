package com.federicogiordano.miroriento.data

/**
 * Expected object for platform-specific file system operations.
 */
expect object FileSystem {
    /**
     * Initializes the FileSystem with a platform-specific context if needed.
     * On Android, this should be called with an Application Context.
     * @param context Platform-specific context (e.g., Android Context).
     */
    fun initialize(context: Any? = null)

    /**
     * Reads the content of a file as a String.
     * The file is assumed to be in the application's private files directory.
     * @param fileName The name of the file to read.
     * @return The content of the file as a String, or null if the file doesn't exist or an error occurs.
     */
    fun readTextFromFile(fileName: String): String?

    /**
     * Writes content to a file.
     * The file is created in the application's private files directory.
     * If the file already exists, it is overwritten.
     * @param fileName The name of the file to write to.
     * @param content The String content to write to the file.
     * @return True if the write operation was successful, false otherwise.
     */
    fun writeTextToFile(fileName: String, content: String): Boolean
}