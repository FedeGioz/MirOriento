package com.federicogiordano.miroriento.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun JoystickController(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    onVelocityChanged: (linear: Float, angular: Float) -> Unit
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var pointerOffset by remember { mutableStateOf(Offset.Zero) }
    val maxDistancePx = with(LocalDensity.current) { (size / 2).toPx() }

    Surface(
        modifier = modifier.size(size),
        elevation = 8.dp,
        shape = CircleShape,
        color = MaterialTheme.colors.surface.copy(alpha = 0.9f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = pointerOffset + dragAmount
                            val distance = sqrt(
                                newOffset.x * newOffset.x +
                                        newOffset.y * newOffset.y
                            )

                            pointerOffset = if (distance > maxDistancePx) {
                                val angle = atan2(newOffset.y, newOffset.x)
                                Offset(
                                    cos(angle) * maxDistancePx,
                                    sin(angle) * maxDistancePx
                                )
                            } else newOffset

                            onVelocityChanged(
                                -pointerOffset.y / maxDistancePx,
                                pointerOffset.x / maxDistancePx
                            )
                        },
                        onDragEnd = {
                            pointerOffset = Offset.Zero
                            onVelocityChanged(0f, 0f)
                        }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .size(size - 32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colors.background)
                    .onGloballyPositioned { coordinates: LayoutCoordinates ->
                        center = Offset(
                            coordinates.size.width / 2f,
                            coordinates.size.height / 2f
                        )
                    }
            ) {}

            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        translationX = pointerOffset.x
                        translationY = pointerOffset.y
                    },
                shape = CircleShape,
                color = MaterialTheme.colors.primary,
                elevation = 4.dp
            ) {}
        }
    }
}