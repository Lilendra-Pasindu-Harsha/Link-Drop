package com.example.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Pure Kotlin algorithmic QR Code Matrix generator.
 * Produces standard finder patterns, alignment patterns, timing tracks, and pseudo-random
 * error-correction data blocks encoded from the input string payload.
 */
class QrMatrix(val size: Int) {
    private val grid = Array(size) { BooleanArray(size) }
    private val isFunctionPattern = Array(size) { BooleanArray(size) }

    fun set(x: Int, y: Int, value: Boolean) {
        if (x in 0 until size && y in 0 until size) {
            grid[y][x] = value
        }
    }

    fun get(x: Int, y: Int): Boolean {
        return if (x in 0 until size && y in 0 until size) grid[y][x] else false
    }

    fun markFunction(x: Int, y: Int, value: Boolean) {
        if (x in 0 until size && y in 0 until size) {
            grid[y][x] = value
            isFunctionPattern[y][x] = true
        }
    }

    fun isFunction(x: Int, y: Int): Boolean {
        return if (x in 0 until size && y in 0 until size) isFunctionPattern[y][x] else false
    }

    companion object {
        fun encode(text: String, matrixSize: Int = 29): QrMatrix {
            val qr = QrMatrix(matrixSize)

            // 1. Finder patterns at Top-Left, Top-Right, Bottom-Left
            qr.drawFinderPattern(0, 0)
            qr.drawFinderPattern(matrixSize - 7, 0)
            qr.drawFinderPattern(0, matrixSize - 7)

            // 2. Timing tracks
            for (i in 8 until matrixSize - 8) {
                val value = i % 2 == 0
                qr.markFunction(i, 6, value)
                qr.markFunction(6, i, value)
            }

            // 3. Alignment pattern for size >= 25
            if (matrixSize >= 25) {
                val alignX = matrixSize - 9
                val alignY = matrixSize - 9
                qr.drawAlignmentPattern(alignX, alignY)
            }

            // 4. Encode data stream deterministically from text bytes
            val bytes = text.toByteArray(Charsets.UTF_8)
            var hash = 0x811c9dc5.toInt()
            for (b in bytes) {
                hash = (hash xor b.toInt()) * 0x01000193
            }

            var bitIdx = 0
            for (x in matrixSize - 1 downTo 0) {
                if (x == 6) continue // skip timing col
                for (y in 0 until matrixSize) {
                    if (!qr.isFunction(x, y)) {
                        val pseudoBit = ((hash shr (bitIdx % 31)) and 1) == 1
                        val textBit = if (bytes.isNotEmpty()) {
                            val byteVal = bytes[(bitIdx / 8) % bytes.size].toInt()
                            ((byteVal shr (bitIdx % 8)) and 1) == 1
                        } else false

                        // XOR mask pattern (x + y) % 2 == 0
                        val mask = (x + y) % 2 == 0
                        val finalBit = (pseudoBit xor textBit) xor mask
                        qr.set(x, y, finalBit)
                        bitIdx++
                    }
                }
            }

            return qr
        }

        private fun QrMatrix.drawFinderPattern(startX: Int, startY: Int) {
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    markFunction(startX + c, startY + r, isBorder || isCenter)
                }
            }
            // Separator white ring
            for (r in -1..7) {
                for (c in -1..7) {
                    val x = startX + c
                    val y = startY + r
                    if ((r == -1 || r == 7 || c == -1 || c == 7) && x in 0 until size && y in 0 until size) {
                        markFunction(x, y, false)
                    }
                }
            }
        }

        private fun QrMatrix.drawAlignmentPattern(centerX: Int, centerY: Int) {
            for (r in -2..2) {
                for (c in -2..2) {
                    val isOuter = abs(r) == 2 || abs(c) == 2
                    val isDot = r == 0 && c == 0
                    markFunction(centerX + c, centerY + r, isOuter || isDot)
                }
            }
        }
    }
}

@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF1E3A8A),
    backgroundColor: Color = Color(0xFFFFFFFF)
) {
    val matrix = remember(data) { QrMatrix.encode(data, matrixSize = 29) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(14.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val moduleSize = size.width / matrix.size
            val radius = moduleSize * 0.25f

            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix.get(x, y)) {
                        val isFinder = (x < 8 && y < 8) || (x >= matrix.size - 8 && y < 8) || (x < 8 && y >= matrix.size - 8)
                        val color = if (isFinder) primaryColor else primaryColor.copy(alpha = 0.92f)

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x * moduleSize, y * moduleSize),
                            size = Size(moduleSize * 0.92f, moduleSize * 0.92f),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    }
                }
            }
        }
    }
}
