package com.barcodescanner.model

/**
 * 用户信息数据模型
 */
data class UserInfo(
    val id: Int = 0,
    val name: String = "",
    val themeColor: String = "#FF8C42",
    val role: String = "operator",
    val createdAt: String = ""
)
