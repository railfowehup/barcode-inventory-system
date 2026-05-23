package com.barcodescanner.model

/**
 * 包裹记录数据模型
 */
data class RecordItem(
    val id: Int = 0,
    val barcode: String = "",
    val status: String = "入库",
    val address: String = "",
    val weight: Double = 0.0,
    val deviceId: String = "",
    val note: String = "",
    val recipient: String = "",
    val logisticsNo: String = "",
    val signer: String = "",
    val exceptionType: String = "",
    val createdAt: String = "",
    val sortAt: String = "",
    val userName: String = "",
    val themeColor: String = ""
)
