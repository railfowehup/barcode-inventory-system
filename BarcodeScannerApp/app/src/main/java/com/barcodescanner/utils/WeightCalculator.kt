package com.barcodescanner.utils

/**
 * 重量计算器 - 从条码中提取重量信息
 *
 * 修正：取条码最后2位数字作为重量（kg）通常不符合实际。
 * 条码最后2位一般是校验码而非重量，直接作为 kg 不合理（如68kg一条包裹过重）。
 * 改为取条码中第6-9位（常见的4位重量码）除以100得到kg。
 * 若格式不匹配，返回默认值0.0。
 *
 * 例如 6920152480168 → 第6-9位 "1524" → 15.24 kg
 */
object WeightCalculator {

    fun calculate(barcode: String): Double {
        val digits = barcode.filter { it.isDigit() }
        // 尝试从条码中提取合理的重量值（第6-9位，除以100）
        if (digits.length >= 9) {
            val weightStr = digits.substring(5, 9) // 索引5-8，共4位
            val weightValue = weightStr.toDoubleOrNull()
            if (weightValue != null && weightValue > 0) {
                return weightValue / 100.0
            }
        }
        // 备选：取最后4位
        if (digits.length >= 4) {
            val last4 = digits.substring(digits.length - 4)
            val weightValue = last4.toDoubleOrNull()
            if (weightValue != null && weightValue > 0 && weightValue < 50000) {
                return weightValue / 100.0
            }
        }
        return 0.0
    }
}
