// 二维码生成工具类，使用ZXing库将vCard字符串编码为QR Code位图
package com.example.digitalcard.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.charset.StandardCharsets

/**
 * QR 码生成工具类
 *
 * 关键设计说明（中文编码兼容性）：
 * vCard 中包含中文（UTF-8 多字节字符），ZXing 默认的 QR 码编码不包含
 * ECI 编码标记，导致微信/支付宝扫码时使用 ISO-8859-1 解码 UTF-8 字节，
 * 中文显示为 ??? 乱码。
 *
 * 解决方案：在 encode 时显式设置 CHARACTER_SET=UTF-8 hint，
 * ZXing 会在 QR 码数据前插入 ECI 标记（0010），
 * 兼容扫码器（微信/支付宝等）按 UTF-8 解码，正确显示中文。
 */
object QrCodeGenerator {

    /**
     * 根据文本内容生成二维码 Bitmap
     * @param content 要编码的内容（vCard 格式字符串，含中文 UTF-8 文本）
     * @param size 二维码大小（像素），默认 512
     * @return 生成的二维码 Bitmap
     */
    fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val writer = QRCodeWriter()

            // 显式设置 UTF-8 编码提示，使 ZXing 在 QR 码中包含 ECI 标记
            // 避免微信/支付宝扫码时用 ISO-8859-1 解码中文导致 ??? 乱码
            val hints = mutableMapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name()
            )

            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}