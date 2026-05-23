package com.barcodescanner.utils

/**
 * 重量计算器 - 从条码中提取重量信息
 * 规则：取条码最后2位数字作为重量（kg）
 * 例如 6920152480168 → 最后2位 68 → 68 kg
 */
object WeightCalculator {

    fun calculate(barcode: String): Double {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length < 2) return 1.0
        val lastTwo = digits.substring(digits.length - 2)
        return lastTwo.toDoubleOrNull() ?: 1.0
    }
}
