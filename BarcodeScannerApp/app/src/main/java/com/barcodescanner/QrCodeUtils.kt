package com.barcodescanner

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码工具类 - 生成和解析二维码
 */
object QrCodeUtils {

    /**
     * 生成二维码 Bitmap
     * @param content 二维码内容（URL）
     * @param size 图片尺寸（像素）
     * @return 生成的二维码 Bitmap
     */
    fun generateQrCode(content: String, size: Int = 400): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
