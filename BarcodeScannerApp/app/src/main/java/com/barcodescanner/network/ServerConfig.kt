package com.barcodescanner.network

import android.content.Context
import android.content.SharedPreferences

/**
 * 服务器配置管理 - 集中管理服务器地址
 * 支持扫码自动填入、本地持久化
 *
 * Fix #14: 修复 lateinit 未初始化崩溃风险
 * - 将 prefs 改为可空类型
 * - 所有访问方法做空安全检查
 */
object ServerConfig {

    private const val PREFS_NAME = "barcode_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_DEVICE_ID = "device_id"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== 服务器地址 ====================

    fun getServerUrl(): String = prefs?.getString(KEY_SERVER_URL, "") ?: ""

    fun setServerUrl(url: String) {
        prefs?.edit()?.putString(KEY_SERVER_URL, url)?.apply()
    }

    fun hasServerUrl(): Boolean = getServerUrl().isNotEmpty()

    // ==================== 用户信息 ====================

    fun getUserName(): String = prefs?.getString(KEY_USER_NAME, "") ?: ""

    fun setUserName(name: String) {
        prefs?.edit()?.putString(KEY_USER_NAME, name)?.apply()
    }

    fun getUserId(): Int = prefs?.getInt(KEY_USER_ID, 0) ?: 0

    fun setUserId(id: Int) {
        prefs?.edit()?.putInt(KEY_USER_ID, id)?.apply()
    }

    fun getThemeColor(): String = prefs?.getString(KEY_THEME_COLOR, "#FF8C42") ?: "#FF8C42"

    fun setThemeColor(color: String) {
        prefs?.edit()?.putString(KEY_THEME_COLOR, color)?.apply()
    }

    fun getDeviceId(): String = prefs?.getString(KEY_DEVICE_ID, "") ?: ""

    fun setDeviceId(id: String) {
        prefs?.edit()?.putString(KEY_DEVICE_ID, id)?.apply()
    }

    // ==================== 离线记录 ====================

    fun getOfflineRecords(): String = prefs?.getString("offline_records", "[]") ?: "[]"

    fun saveOfflineRecords(json: String) {
        prefs?.edit()?.putString("offline_records", json)?.apply()
    }

    fun clearOfflineRecords() {
        prefs?.edit()?.remove("offline_records")?.apply()
    }

    // ==================== 备份 ====================

    fun getBackupData(): String? = prefs?.getString("backup_data", null)

    fun saveBackupData(json: String) {
        prefs?.edit()?.putString("backup_data", json)?.apply()
    }

    // ==================== 清除 ====================

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
}
