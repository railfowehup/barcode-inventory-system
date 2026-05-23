package com.barcodescanner.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.barcodescanner.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 离线记录同步管理器
 * 统一管理 ScannerActivity 和 RecordsActivity 中的离线记录存储与同步
 *
 * Fix #15: 增加线程安全 - 所有读写操作使用 synchronized
 */
class SyncManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "barcode_prefs"
        private const val KEY_OFFLINE_RECORDS = "offline_records"
        private const val KEY_OFFLINE_OPERATIONS = "offline_operations"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    // ==================== 离线记录存储 ====================

    /**
     * 保存一条离线入库记录
     */
    fun saveOfflineRecord(barcode: String, address: String = "", weight: Double = 0.0) {
        synchronized(lock) {
            try {
                val existing = getOfflineRecords()
                val record = JSONObject().apply {
                    put("barcode", barcode)
                    put("address", address)
                    put("weight", weight)
                    put("timestamp", System.currentTimeMillis())
                }
                existing.put(record)
                prefs.edit().putString(KEY_OFFLINE_RECORDS, existing.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * 获取所有离线记录
     */
    fun getOfflineRecords(): JSONArray {
        synchronized(lock) {
            return try {
                val raw = prefs.getString(KEY_OFFLINE_RECORDS, "[]") ?: "[]"
                JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }
        }
    }

    /**
     * 获取离线记录数量
     */
    fun getOfflineRecordCount(): Int = synchronized(lock) { getOfflineRecords().length() }

    /**
     * 保存一条离线操作（分拣、出库、签收等）
     */
    fun saveOfflineOperation(operation: String, barcode: String, extra: Map<String, Any> = emptyMap()) {
        synchronized(lock) {
            try {
                val existing = getOfflineOperations()
                val record = JSONObject().apply {
                    put("operation", operation)
                    put("barcode", barcode)
                    put("timestamp", System.currentTimeMillis())
                    extra.forEach { (k, v) ->
                        when (v) {
                            is String -> put(k, v)
                            is Number -> put(k, v.toDouble())
                            is Boolean -> put(k, v)
                        }
                    }
                }
                existing.put(record)
                prefs.edit().putString(KEY_OFFLINE_OPERATIONS, existing.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    /**
     * 获取所有离线操作
     */
    fun getOfflineOperations(): JSONArray {
        synchronized(lock) {
            return try {
                val raw = prefs.getString(KEY_OFFLINE_OPERATIONS, "[]") ?: "[]"
                JSONArray(raw)
            } catch (_: Exception) {
                JSONArray()
            }
        }
    }

    /**
     * 获取离线操作数量
     */
    fun getOfflineOperationCount(): Int = synchronized(lock) { getOfflineOperations().length() }

    /**
     * 获取总离线数量
     */
    fun getTotalOfflineCount(): Int = synchronized(lock) { getOfflineRecordCount() + getOfflineOperationCount() }

    // ==================== 同步 ====================

    /**
     * 同步所有离线记录到服务器
     * @param userId 当前用户 ID
     * @param onComplete 同步完成回调 (successCount, failCount)
     */
    fun syncAll(userId: Int, onComplete: ((Int, Int) -> Unit)? = null) {
        var successCount = 0
        var failCount = 0

        // 同步离线入库记录
        val records = synchronized(lock) { getOfflineRecords() }
        if (records.length() > 0) {
            val recordList = mutableListOf<JSONObject>()
            for (i in 0 until records.length()) {
                recordList.add(records.getJSONObject(i))
            }

            ApiClient.syncOfflineRecords(recordList, userId, object : ApiClient.ApiCallback {
                override fun onSuccess(data: JSONObject?) {
                    synchronized(lock) {
                        successCount += recordList.size
                        prefs.edit().remove(KEY_OFFLINE_RECORDS).apply()
                    }
                    syncOperations(userId, successCount, failCount, onComplete)
                }
                override fun onError(error: String?) {
                    synchronized(lock) { failCount += recordList.size }
                    syncOperations(userId, successCount, failCount, onComplete)
                }
            })
        } else {
            syncOperations(userId, successCount, failCount, onComplete)
        }
    }

    private fun syncOperations(
        userId: Int, successCount: Int, failCount: Int,
        onComplete: ((Int, Int) -> Unit)?
    ) {
        val operations = synchronized(lock) { getOfflineOperations() }
        if (operations.length() == 0) {
            onComplete?.invoke(successCount, failCount)
            return
        }

        var opsSuccess = successCount
        var opsFail = failCount
        var completed = 0

        for (i in 0 until operations.length()) {
            val op = operations.getJSONObject(i)
            val operation = op.optString("operation", "")
            val barcode = op.optString("barcode", "")

            val callback = object : ApiClient.ApiCallback {
                override fun onSuccess(data: JSONObject?) {
                    synchronized(this) {
                        opsSuccess++
                        checkDone()
                    }
                }
                override fun onError(error: String?) {
                    synchronized(this) {
                        opsFail++
                        checkDone()
                    }
                }
                private fun checkDone() {
                    completed++
                    if (completed >= operations.length()) {
                        synchronized(lock) { prefs.edit().remove(KEY_OFFLINE_OPERATIONS).apply() }
                        onComplete?.invoke(opsSuccess, opsFail)
                    }
                }
            }

            when (operation) {
                "sort" -> ApiClient.sortBarcode(barcode, userId, "", callback)
                "ship" -> {
                    val logisticsNo = op.optString("logistics_no", "")
                    val recipient = op.optString("recipient", "")
                    ApiClient.shipBarcode(barcode, userId, logisticsNo, recipient, callback)
                }
                "sign" -> {
                    val signer = op.optString("signer", "")
                    val exceptionType = op.optString("exception_type", "")
                    ApiClient.signBarcode(barcode, userId, signer, exceptionType, callback)
                }
                else -> {
                    synchronized(this) {
                        completed++
                        if (completed >= operations.length()) {
                            synchronized(lock) { prefs.edit().remove(KEY_OFFLINE_OPERATIONS).apply() }
                            onComplete?.invoke(opsSuccess, opsFail)
                        }
                    }
                }
            }
        }
    }

    /**
     * 清除所有离线数据
     */
    fun clearAll() {
        synchronized(lock) {
            prefs.edit()
                .remove(KEY_OFFLINE_RECORDS)
                .remove(KEY_OFFLINE_OPERATIONS)
                .apply()
        }
    }
}
