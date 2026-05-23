package com.barcodescanner.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 手机端 SQLite 数据库核心操作
 * 负责：建表、用户管理、记录 CRUD、统计
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, "barcode_server.db", null, 3
) {
    companion object {
        private const val TAG = "DatabaseHelper"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                theme_color TEXT NOT NULL DEFAULT '#2196F3',
                role TEXT NOT NULL DEFAULT 'operator',
                created_at TEXT DEFAULT (datetime('now','localtime'))
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                barcode TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT '入库',
                address TEXT DEFAULT '',
                weight REAL DEFAULT 0,
                device_id TEXT DEFAULT '',
                note TEXT DEFAULT '',
                recipient TEXT DEFAULT '',
                logistics_no TEXT DEFAULT '',
                signer TEXT DEFAULT '',
                sign_time TEXT,
                exception_type TEXT DEFAULT '',
                is_duplicate INTEGER DEFAULT 0,
                is_merged INTEGER DEFAULT 0,
                address_history TEXT DEFAULT '[]',
                created_at TEXT DEFAULT (datetime('now','localtime')),
                sort_at TEXT,
                updated_at TEXT DEFAULT (datetime('now','localtime')),
                version INTEGER DEFAULT 1,
                sync_status TEXT DEFAULT 'synced',
                FOREIGN KEY (user_id) REFERENCES users(id)
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                user_name TEXT DEFAULT '',
                sync_time TEXT DEFAULT (datetime('now','localtime')),
                added INTEGER DEFAULT 0,
                skipped INTEGER DEFAULT 0,
                duplicates INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS device_heartbeats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                device_name TEXT DEFAULT '',
                ip_address TEXT DEFAULT '',
                user_name TEXT DEFAULT '',
                device_group TEXT DEFAULT '',
                last_heartbeat TEXT DEFAULT (datetime('now','localtime'))
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS connection_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                device_name TEXT DEFAULT '',
                user_name TEXT DEFAULT '',
                ip_address TEXT DEFAULT '',
                connected_at TEXT DEFAULT (datetime('now','localtime')),
                operation_count INTEGER DEFAULT 0
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_user ON records(user_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_date ON records(created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_status ON records(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_barcode ON records(barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_updated ON records(updated_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_heartbeat_device ON device_heartbeats(device_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE records ADD COLUMN recipient TEXT DEFAULT ''") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN logistics_no TEXT DEFAULT ''") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN signer TEXT DEFAULT ''") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN sign_time TEXT") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN exception_type TEXT DEFAULT ''") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN is_duplicate INTEGER DEFAULT 0") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN is_merged INTEGER DEFAULT 0") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN address_history TEXT DEFAULT '[]'") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN sort_at TEXT") } catch (_: Exception) {}
        }
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE records ADD COLUMN updated_at TEXT DEFAULT (datetime('now','localtime'))") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN version INTEGER DEFAULT 1") } catch (_: Exception) {}
            try { db.execSQL("ALTER TABLE records ADD COLUMN sync_status TEXT DEFAULT 'synced'") } catch (_: Exception) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS sync_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL, user_name TEXT DEFAULT '', sync_time TEXT DEFAULT (datetime('now','localtime')), added INTEGER DEFAULT 0, skipped INTEGER DEFAULT 0, duplicates INTEGER DEFAULT 0)") } catch (_: Exception) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS device_heartbeats (id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL, device_name TEXT DEFAULT '', ip_address TEXT DEFAULT '', user_name TEXT DEFAULT '', device_group TEXT DEFAULT '', last_heartbeat TEXT DEFAULT (datetime('now','localtime')))") } catch (_: Exception) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS connection_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL, device_name TEXT DEFAULT '', user_name TEXT DEFAULT '', ip_address TEXT DEFAULT '', connected_at TEXT DEFAULT (datetime('now','localtime')), operation_count INTEGER DEFAULT 0)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_updated ON records(updated_at)") } catch (_: Exception) {}
            try { db.execSQL("CREATE INDEX IF NOT EXISTS idx_heartbeat_device ON device_heartbeats(device_id)") } catch (_: Exception) {}
        }
    }

    // ==================== 用户 ====================

    fun loginUser(name: String): Map<String, Any?> {
        val db = writableDatabase
        var user = findUserByName(name)
        if (user == null) {
            val colors = listOf(
                "#2196F3", "#4CAF50", "#F44336", "#FF9800",
                "#9C27B0", "#00BCD4", "#795548", "#607D8B",
                "#E91E63", "#3F51B5"
            )
            val count = db.rawQuery("SELECT COUNT(*) as cnt FROM users", null).use {
                it.moveToFirst(); it.getInt(0)
            }
            val color = colors[count % colors.size]
            val values = ContentValues().apply {
                put("name", name); put("theme_color", color); put("role", "operator")
            }
            val id = db.insert("users", null, values)
            user = findUserById(id)
        }
        return user ?: mapOf("error" to "创建用户失败")
    }

    private fun findUserByName(name: String): Map<String, Any?>? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE name = ?", arrayOf(name))
        return cursor.use { if (it.moveToFirst()) cursorToMap(it) else null }
    }

    private fun findUserById(id: Long): Map<String, Any?>? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) cursorToMap(it) else null }
    }

    fun getAllUsers(): List<Map<String, Any?>> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, name, theme_color, role, created_at FROM users ORDER BY id", null)
        return cursor.use {
            val list = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) list.add(cursorToMap(it))
            list
        }
    }

    // ==================== 记录 CRUD ====================

    fun findRecordByBarcode(barcode: String): Map<String, Any?>? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM records WHERE barcode = ?", arrayOf(barcode))
        return cursor.use { if (it.moveToFirst()) cursorToMap(it) else null }
    }

    fun findRecordById(id: Long): Map<String, Any?>? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM records WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { if (it.moveToFirst()) cursorToMap(it) else null }
    }

    fun insertRecord(barcode: String, userId: Int, address: String, weight: Double, note: String): Map<String, Any?> {
        val db = writableDatabase
        val existing = findRecordByBarcode(barcode)
        if (existing != null && existing["status"] == "入库") {
            return mapOf("error" to "该包裹已入库", "record" to existing)
        }
        val now = getCurrentTime()
        val values = ContentValues().apply {
            put("barcode", barcode); put("user_id", userId); put("status", "入库")
            put("address", address); put("weight", weight); put("note", note)
            put("updated_at", now); put("version", 1); put("sync_status", "pending")
        }
        val id = db.insertOrThrow("records", null, values)
        return findRecordById(id) ?: mapOf("error" to "插入失败")
    }

    fun sortRecord(barcode: String, deviceId: String): Map<String, Any?> {
        val db = writableDatabase
        val now = getCurrentTime()
        val values = ContentValues().apply {
            put("status", "分拣"); put("device_id", deviceId); put("sort_at", now)
            put("updated_at", now); put("sync_status", "pending")
        }
        db.execSQL("UPDATE records SET version = version + 1 WHERE barcode = ?", arrayOf(barcode))
        db.update("records", values, "barcode = ?", arrayOf(barcode))
        return findRecordByBarcode(barcode) ?: mapOf("error" to "更新失败")
    }

    fun shipRecord(barcode: String, logisticsNo: String, recipient: String): Map<String, Any?> {
        val db = writableDatabase
        val now = getCurrentTime()
        val values = ContentValues().apply {
            put("status", "出库"); put("logistics_no", logisticsNo); put("recipient", recipient)
            put("sort_at", now); put("updated_at", now); put("sync_status", "pending")
        }
        db.execSQL("UPDATE records SET version = version + 1 WHERE barcode = ?", arrayOf(barcode))
        db.update("records", values, "barcode = ?", arrayOf(barcode))
        return findRecordByBarcode(barcode) ?: mapOf("error" to "更新失败")
    }

    fun signRecord(barcode: String, signer: String, exceptionType: String): Map<String, Any?> {
        val db = writableDatabase
        val now = getCurrentTime()
        val values = ContentValues().apply {
            put("status", if (exceptionType.isNotEmpty()) "异常" else "签收")
            put("signer", signer); put("sign_time", now); put("exception_type", exceptionType)
            put("updated_at", now); put("sync_status", "pending")
        }
        db.execSQL("UPDATE records SET version = version + 1 WHERE barcode = ?", arrayOf(barcode))
        db.update("records", values, "barcode = ?", arrayOf(barcode))
        return findRecordByBarcode(barcode) ?: mapOf("error" to "更新失败")
    }

    fun changeAddress(recordId: Int, address: String, userName: String): Map<String, Any?> {
        val db = writableDatabase
        val record = findRecordById(recordId.toLong())
        if (record == null) return mapOf("error" to "记录不存在")

        var history = mutableListOf<Map<String, String>>()
        try {
            val raw = record["address_history"] as? String ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                history.add(mapOf(
                    "from" to obj.optString("from", ""), "to" to obj.optString("to", ""),
                    "changed_by" to obj.optString("changed_by", ""), "time" to obj.optString("time", "")
                ))
            }
        } catch (_: Exception) {}

        history.add(mapOf(
            "from" to (record["address"] as? String ?: ""),
            "to" to address, "changed_by" to userName, "time" to getCurrentTime()
        ))

        val now = getCurrentTime()
        val values = ContentValues().apply {
            put("address", address)
            put("address_history", JSONArray(history.map { JSONObject(it) }).toString())
            put("updated_at", now); put("sync_status", "pending")
        }
        db.execSQL("UPDATE records SET version = version + 1 WHERE id = ?", arrayOf(recordId.toString()))
        db.update("records", values, "id = ?", arrayOf(recordId.toString()))
        return findRecordById(recordId.toLong()) ?: mapOf("error" to "更新失败")
    }

    fun updateRecord(recordId: Int, address: String?, weight: Double?, note: String?): Map<String, Any?> {
        val db = writableDatabase
        val now = getCurrentTime()
        val values = ContentValues()
        if (address != null) values.put("address", address)
        if (weight != null) values.put("weight", weight)
        if (note != null) values.put("note", note)
        if (values.size() > 0) {
            values.put("updated_at", now); values.put("sync_status", "pending")
            db.execSQL("UPDATE records SET version = version + 1 WHERE id = ?", arrayOf(recordId.toString()))
            db.update("records", values, "id = ?", arrayOf(recordId.toString()))
        }
        return findRecordById(recordId.toLong()) ?: mapOf("error" to "更新失败")
    }

    fun deleteRecord(recordId: Int): Boolean {
        val db = writableDatabase
        return db.delete("records", "id = ?", arrayOf(recordId.toString())) > 0
    }

    fun getAllRecords(): List<Map<String, Any?>> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM records ORDER BY created_at DESC", null)
        return cursor.use {
            val list = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) list.add(cursorToMap(it))
            list
        }
    }

    fun getRecords(userId: Int?, page: Int, pageSize: Int, status: String?): Map<String, Any?> {
        val db = readableDatabase
        val where = mutableListOf<String>()
        val params = mutableListOf<String>()
        if (userId != null) { where.add("r.user_id = ?"); params.add(userId.toString()) }
        if (!status.isNullOrEmpty()) { where.add("r.status = ?"); params.add(status) }
        val whereClause = if (where.isNotEmpty()) "WHERE ${where.joinToString(" AND ")}" else ""
        val offset = (page - 1) * pageSize

        val total = db.rawQuery("SELECT COUNT(*) as cnt FROM records r $whereClause", params.toTypedArray()).use {
            it.moveToFirst(); it.getInt(0)
        }
        val queryParams = params.toMutableList().apply { add(pageSize.toString()); add(offset.toString()) }
        val cursor = db.rawQuery("""
            SELECT r.*, u.name as user_name, u.theme_color
            FROM records r JOIN users u ON r.user_id = u.id
            $whereClause ORDER BY r.created_at DESC LIMIT ? OFFSET ?
        """, queryParams.toTypedArray())
        val records = cursor.use {
            val list = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) list.add(cursorToMap(it))
            list
        }
        return mapOf("total" to total, "page" to page, "page_size" to pageSize, "records" to records)
    }

    fun searchRecords(query: String, status: String?, page: Int, pageSize: Int): Map<String, Any?> {
        val db = readableDatabase
        val where = mutableListOf<String>()
        val params = mutableListOf<String>()
        where.add("(r.barcode LIKE ? OR r.note LIKE ?)")
        params.add("%$query%"); params.add("%$query%")
        if (!status.isNullOrEmpty()) { where.add("r.status = ?"); params.add(status) }
        val whereClause = "WHERE ${where.joinToString(" AND ")}"
        val offset = (page - 1) * pageSize

        val total = db.rawQuery("SELECT COUNT(*) as cnt FROM records r $whereClause", params.toTypedArray()).use {
            it.moveToFirst(); it.getInt(0)
        }
        val queryParams = params.toMutableList().apply { add(pageSize.toString()); add(offset.toString()) }
        val cursor = db.rawQuery("""
            SELECT r.*, u.name as user_name, u.theme_color
            FROM records r JOIN users u ON r.user_id = u.id
            $whereClause ORDER BY r.created_at DESC LIMIT ? OFFSET ?
        """, queryParams.toTypedArray())
        val records = cursor.use {
            val list = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) list.add(cursorToMap(it))
            list
        }
        return mapOf("total" to total, "page" to page, "page_size" to pageSize, "records" to records)
    }

    fun getStats(userId: Int?): Map<String, Any?> {
        val db = readableDatabase
        fun countSql(extraWhere: String = ""): Int {
            val where = if (userId != null) "WHERE user_id = ?" else ""
            val params = if (userId != null) arrayOf(userId.toString()) else emptyArray()
            val sql = "SELECT COUNT(*) as cnt FROM records $where $extraWhere"
            return db.rawQuery(sql.trim(), params).use { it.moveToFirst(); it.getInt(0) }
        }
        return mapOf(
            "total" to countSql(), "today" to countSql("AND date(created_at) = date('now','localtime')"),
            "in_count" to countSql("AND status = '入库'"), "sort_count" to countSql("AND status = '分拣'"),
            "ship_count" to countSql("AND status = '出库'"), "sign_count" to countSql("AND status = '签收'"),
            "duplicate_count" to countSql("AND status = '异常'")
        )
    }

    // ==================== 工具 ====================

    fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun cursorToMap(cursor: android.database.Cursor): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            map[name] = when (cursor.getType(i)) {
                android.database.Cursor.FIELD_TYPE_NULL -> null
                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(i)
                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                else -> cursor.getString(i)
            }
        }
        return map
    }
}
