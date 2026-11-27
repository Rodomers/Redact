package com.rds.mews.core.parser

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import androidx.core.graphics.scale

object PerceptualHashUtils {

    @WorkerThread
    fun calculateDHash(bitmap: Bitmap): Long {
        val resized = bitmap.scale(9, 8)
        var hash = 0L

        val pixels = IntArray(72)
        resized.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (resized != bitmap) {
            resized.recycle()
        }

        var bitIndex = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = pixels[(y * 9) + x]
                val rightPixel = pixels[(y * 9) + x + 1]

                val leftLuma = getLuminance(leftPixel)
                val rightLuma = getLuminance(rightPixel)

                if (leftLuma > rightLuma) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }

        return hash
    }

    private fun getLuminance(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    fun isVisuallySimilar(hash1: Long, hash2: Long, threshold: Int = 8): Boolean {
        return hammingDistance(hash1, hash2) <= threshold
    }
}
