package com.roulettetactician.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * A Dialog whose content can be dragged around the screen.
 * The content is clamped so it can never be moved fully off-screen.
 */
@Composable
fun DraggableDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current

        // Screen size in pixels
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

        // Track the drag offset
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

        // Track the content size so we can clamp properly
        var contentSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .onSizeChanged { contentSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()

                            // Calculate new offset
                            val newX = offsetX + dragAmount.x
                            val newY = offsetY + dragAmount.y

                            // Clamp so the dialog stays on screen
                            // The dialog starts centered, so offset 0,0 = center of screen.
                            // Max offset = half screen size - some margin to keep part visible
                            val halfW = contentSize.width / 2f
                            val halfH = contentSize.height / 2f
                            val halfScreenW = screenWidthPx / 2f
                            val halfScreenH = screenHeightPx / 2f

                            // Keep at least 60px of the dialog visible on each edge
                            val margin = 60f
                            val maxX = halfScreenW - margin
                            val maxY = halfScreenH - margin

                            offsetX = newX.coerceIn(-maxX, maxX)
                            offsetY = newY.coerceIn(-maxY, maxY)
                        }
                    }
            ) {
                content()
            }
        }
    }
}
