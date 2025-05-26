package com.federicogiordano.miroriento.utils

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

@Composable
actual fun rememberBase64ImageBitmap(base64String: String?): ImageBitmap? {
    return remember(base64String) {
        if (base64String.isNullOrEmpty()) {
            null
        } else {
            try {
                val pureBase64 = if (base64String.contains(',')) {
                    base64String.substringAfter(',')
                } else {
                    base64String
                }
                val imageBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
            } catch (e: IllegalArgumentException) {
                println("Error decoding Base64 image (IllegalArgumentException): ${e.message}")
                null
            } catch (e: Exception) {
                println("Error decoding Base64 image (Exception): ${e.message}")
                null
            }
        }
    }
}