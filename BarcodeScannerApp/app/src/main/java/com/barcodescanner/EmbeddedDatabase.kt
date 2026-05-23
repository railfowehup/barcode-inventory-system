package com.barcodescanner

import android.content.ContentValues
import android.content.Context
import com.barcodescanner.database.DatabaseHelper

/**
 * 手机端 SQLite 数据库 - 兼容层
 * 继承 DatabaseHelper 获得所有 CRUD 能力
 * 额外提供：同步相关方法（待同步记录、心跳、连接日志）
 */
class EmbeddedDatabase(context: Context) : DatabaseHelper(context) {

    companion object {
        private const val TAG = "EmbeddedDatabase"
    }

    // ==================== 同步相关 ====================

    fun getPendingSyncRecords(): List<Map<String, Any?>> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM records WHERE sync_status = 'pending' ORDER BY updated_at ASC", null
        )
        return cursor.use {
            val list = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) list.add(cursorToMap(it))
            list
        }
    }

    fun markSynced(barcode: String) {
        val db = writableDatabase
        val values = ContentValues().apply { put("sync_status", "synced") }
        db.update("records", values, "barcode = ?", arrayOf(barcode))
    }

    fun markAllSynced() {
        val db = writableDatabase
        val values = ContentValues().apply { put("sync_status", "synced") }
        db.update("records", values, "sync_status = 'pending'", null)
    }

    fun getPendingSyncCount(): Int {
        val db = readableDatabase
        return db.rawQuery("SELECT COUNT(*) as cnt FROM records WHERE sync_status = 'pending'", null).use {
            it.moveToFirst(); it.getInt(0)
        }
    }

    fun logSync(deviceId: String, userName: String, added: Int, skipped: Int, duplicates: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("device_id", deviceId); put("user_name", userName)
            put("added", added); put("skipped", skipped); put("duplicates", duplicates)
        }
        db.insert("sync_logs", null, values)
    }

    fun recordHeartbeat(deviceId: String, deviceName: String, ipAddress: String, userName: String) {
        val db = writableDatabase
        val now = getCurrentTime()
        val existing = db.rawQuery(
            "SELECT * FROM device_heartbeats WHERE device_id = ?", arrayOf(deviceId)
        ).use { it.moveToFirst() }

        if (existing) {
            val values = ContentValues().apply {
                put("device_name", deviceName); put("ip_address", ipAddress)
                put("user_name", userName); put("last_heartbeat", now)
            }
            db.update("device_heartbeats", values, "device_id = ?", arrayOf(deviceId))
        } else {
            val values = ContentValues().apply {
                put("device_id", deviceId); put("device_name", deviceName)
                put("ip_address", ipAddress); put("user_name", userName)
                put("last_heartbeat", now)
            }
            db.insert("device_heartbeats", null, values)
        }
    }

    fun logConnection(deviceId: String, deviceName: String, userName: String, ipAddress: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("device_id", deviceId); put("device_name", deviceName)
            put("user_name", userName); put("ip_address", ipAddress)
        }
        db.insert("connection_logs", null, values)
    }
}
