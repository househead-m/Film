package com.example.filmcamera

import android.graphics.Bitmap

/**
 * 실시간 프리뷰는 GPU 셰이더(LutFilter)가 처리하지만,
 * 셔터를 눌러 저장하는 고화질 사진은 CPU에서 같은 로직으로 한 번 더 계산해서
 * 파일로 구워넣는다 (프리뷰는 화면 크기라 가볍지만, 저장용 사진은 원본 화질이라 별도 처리).
 */
object LutBaker {

    fun apply(source: Bitmap, lutTexture: Bitmap, lutSize: Int): Bitmap {
        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val lutWidth = lutTexture.width  // lutSize * lutSize
        val lutHeight = lutTexture.height // lutSize
        val lutPixels = IntArray(lutWidth * lutHeight)
        lutTexture.getPixels(lutPixels, 0, lutWidth, 0, 0, lutWidth, lutHeight)

        val maxIndex = lutSize - 1
        val out = IntArray(width * height)

        for (i in srcPixels.indices) {
            val px = srcPixels[i]
            val r = (px shr 16 and 0xFF)
            val g = (px shr 8 and 0xFF)
            val b = (px and 0xFF)
            val a = (px shr 24 and 0xFF)

            val rf = r / 255f * maxIndex
            val gf = g / 255f * maxIndex
            val bf = b / 255f * maxIndex

            val blue0 = bf.toInt().coerceIn(0, maxIndex)
            val blue1 = (blue0 + 1).coerceAtMost(maxIndex)
            val frac = bf - blue0

            val rx = rf.toInt().coerceIn(0, maxIndex)
            val gy = gf.toInt().coerceIn(0, maxIndex)

            val x0 = blue0 * lutSize + rx
            val x1 = blue1 * lutSize + rx

            val c0 = lutPixels[gy * lutWidth + x0]
            val c1 = lutPixels[gy * lutWidth + x1]

            val r0 = (c0 shr 16 and 0xFF); val g0 = (c0 shr 8 and 0xFF); val b0 = (c0 and 0xFF)
            val r1 = (c1 shr 16 and 0xFF); val g1 = (c1 shr 8 and 0xFF); val b1 = (c1 and 0xFF)

            val outR = (r0 + (r1 - r0) * frac).toInt().coerceIn(0, 255)
            val outG = (g0 + (g1 - g0) * frac).toInt().coerceIn(0, 255)
            val outB = (b0 + (b1 - b0) * frac).toInt().coerceIn(0, 255)

            out[i] = (a shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, width, 0, 0, width, height)
        return result
    }
}
