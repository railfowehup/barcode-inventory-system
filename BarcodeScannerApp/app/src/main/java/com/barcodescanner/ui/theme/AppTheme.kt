package com.barcodescanner.ui.theme

import android.graphics.Color

/**
 * 应用主题配置 - 简洁温暖色彩
 * 所有颜色/尺寸/样式常量集中管理
 */
object AppTheme {
    // ==================== 主色调 ====================
    val primaryOrange = Color.parseColor("#FF8C42")       // 暖橙 - 主色
    val primaryOrangeLight = Color.parseColor("#FFB074")  // 浅橙
    val primaryOrangeDark = Color.parseColor("#E07020")   // 深橙

    val accentYellow = Color.parseColor("#FFD166")        // 暖黄 - 辅助
    val accentGreen = Color.parseColor("#06D6A0")         // 清新绿 - 强调
    val accentRed = Color.parseColor("#EF476F")           // 柔和红 - 异常

    // ==================== 背景色 ====================
    val bgCream = Color.parseColor("#FFF8F0")             // 奶油白 - 页面背景
    val bgCard = Color.WHITE                               // 卡片背景
    val bgDark = Color.parseColor("#2D3436")              // 深色背景

    // ==================== 文字色 ====================
    val textPrimary = Color.parseColor("#2D3436")         // 深灰 - 主文字
    val textSecondary = Color.parseColor("#636E72")       // 中灰 - 副文字
    val textHint = Color.parseColor("#B2BEC3")            // 浅灰 - 提示
    val textWhite = Color.WHITE

    // ==================== 状态标签色 ====================
    val statusInbound = Color.parseColor("#4CAF50")       // 入库 - 绿
    val statusSort = Color.parseColor("#FF9800")          // 分拣 - 橙
    val statusShip = Color.parseColor("#2196F3")          // 出库 - 蓝
    val statusSign = Color.parseColor("#9C27B0")          // 签收 - 紫
    val statusException = Color.parseColor("#F44336")     // 异常 - 红

    // ==================== 尺寸 ====================
    const val cornerRadiusSmall = 8f
    const val cornerRadiusMedium = 12f
    const val cornerRadiusLarge = 20f
    const val cardElevation = 4f
    const val buttonElevation = 2f

    // ==================== 状态标签映射 ====================
    fun getStatusTag(status: String): String = when (status) {
        "入库" -> "📦 入库"
        "分拣" -> "📤 分拣"
        "出库" -> "🚚 出库"
        "签收" -> "✅ 签收"
        "异常" -> "⚠️ 异常"
        else -> "📦 $status"
    }

    fun getStatusColor(status: String): Int = when (status) {
        "入库" -> statusInbound
        "分拣" -> statusSort
        "出库" -> statusShip
        "签收" -> statusSign
        "异常" -> statusException
        else -> textSecondary
    }
}
