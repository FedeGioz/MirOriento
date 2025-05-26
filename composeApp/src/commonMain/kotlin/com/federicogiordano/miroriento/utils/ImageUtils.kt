package com.federicogiordano.miroriento.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun rememberBase64ImageBitmap(base64String: String?): ImageBitmap?