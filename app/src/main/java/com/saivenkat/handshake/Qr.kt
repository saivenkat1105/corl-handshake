package com.saivenkat.handshake

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object Qr {
    /** Crisp black-on-white QR with a 2-module quiet zone; each module is [module] px. */
    fun bitmap(text: String, module: Int = 16): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val n = m.width
        val size = n * module
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (m.get(x, y)) {
                    canvas.drawRect(
                        (x * module).toFloat(), (y * module).toFloat(),
                        ((x + 1) * module).toFloat(), ((y + 1) * module).toFloat(), paint
                    )
                }
            }
        }
        return bmp
    }
}
