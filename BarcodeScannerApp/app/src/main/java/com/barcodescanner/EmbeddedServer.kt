package com.barcodescanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.barcodescanner.server.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Ktor 嵌入式服务器 - 在 Android 手机上运行
 * 提供与 Node.js 服务端一致的 API
 * v3: 新增心跳、设备管理、增量同步 API
 */
class EmbeddedServer : Service() {

    companion object {
        private const val TAG = "EmbeddedServer"
        private const val PORT = 3000
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "barcode_server_channel"

        private var serverInstance: ApplicationEngine? = null
        private var isRunning = false

        fun isServerRunning(): Boolean = isRunning

        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取IP失败", e)
            }
            return "127.0.0.1"
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        prettyPrint = false
    }

    private val dbHelper by lazy { EmbeddedDatabase(this) }

    // ==================== 服务生命周期 ====================

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        if (!isRunning) startKtorServer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopKtorServer()
        super.onDestroy()
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "包裹系统服务器",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "包裹入库管理系统后台服务器" }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val ip = getLocalIpAddress()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("📦 包裹系统服务器运行中")
            .setContentText("http://$ip:$PORT")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .build()
    }

    // ==================== Ktor 服务器 ====================

    private fun startKtorServer() {
        Thread {
            try {
                val ip = getLocalIpAddress()
                Log.i(TAG, "启动服务器: http://$ip:$PORT")

                serverInstance = embeddedServer(CIO, port = PORT, host = "0.0.0.0") {
                    install(CORS) {
                        anyHost()
                        allowMethod(HttpMethod.Get)
                        allowMethod(HttpMethod.Post)
                        allowMethod(HttpMethod.Put)
                        allowMethod(HttpMethod.Delete)
                        allowHeader(HttpHeaders.ContentType)
                    }

                    install(ContentNegotiation) { json(json) }

                    routing {
                        // 健康检查
                        get("/api/health") {
                            call.respond(mapOf("status" to "ok", "time" to getCurrentTime()))
                        }

                        // 登录
                        post("/api/login") {
                            try {
                                val body = safeReceive<LoginRequest>(call)
                                val name = body.name?.trim()
                                if (name.isNullOrEmpty()) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请输入姓名"))
                                    return@post
                                }
                                call.respond(dbHelper.loginUser(name))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "登录请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误，需要JSON对象 {\"name\":\"姓名\"}"))
                            } catch (e: Exception) {
                                Log.e(TAG, "登录失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "登录失败")))
                            }
                        }

                        // 扫码入库
                        post("/api/scan") {
                            try {
                                val body = safeReceive<ScanRequest>(call)
                                if (body.barcode.isNullOrEmpty() || body.user_id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少条码或用户信息"))
                                    return@post
                                }
                                val existing = dbHelper.findRecordByBarcode(body.barcode.trim())
                                if (existing != null) {
                                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "该包裹已入库", "record" to existing))
                                    return@post
                                }
                                val record = dbHelper.insertRecord(
                                    barcode = body.barcode.trim(), userId = body.user_id,
                                    address = body.address ?: "", weight = body.weight ?: 0.0, note = body.note ?: ""
                                )
                                call.respond(mapOf("success" to true, "record" to record))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "扫码入库请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "入库失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "入库失败")))
                            }
                        }

                        // 分拣
                        post("/api/sort") {
                            try {
                                val body = safeReceive<SortRequest>(call)
                                if (body.barcode.isNullOrEmpty() || body.user_id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少条码或用户信息"))
                                    return@post
                                }
                                val record = dbHelper.findRecordByBarcode(body.barcode.trim())
                                if (record == null) {
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "该包裹未入库，请先入库"))
                                    return@post
                                }
                                if (record["status"] == "分拣") {
                                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "该包裹已被分拣", "record" to record))
                                    return@post
                                }
                                val updated = dbHelper.sortRecord(body.barcode.trim(), body.device_id ?: "")
                                call.respond(mapOf("success" to true, "record" to updated))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "分拣请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "分拣失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "分拣失败")))
                            }
                        }

                        // 出库
                        post("/api/ship") {
                            try {
                                val body = safeReceive<ShipRequest>(call)
                                if (body.barcode.isNullOrEmpty() || body.user_id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少条码或用户信息"))
                                    return@post
                                }
                                val record = dbHelper.findRecordByBarcode(body.barcode.trim())
                                if (record == null) {
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "该包裹未入库"))
                                    return@post
                                }
                                if (record["status"] == "出库") {
                                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "该包裹已出库", "record" to record))
                                    return@post
                                }
                                val updated = dbHelper.shipRecord(body.barcode.trim(), body.logistics_no ?: "", body.recipient ?: "")
                                call.respond(mapOf("success" to true, "record" to updated))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "出库请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "出库失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "出库失败")))
                            }
                        }

                        // 签收
                        post("/api/sign") {
                            try {
                                val body = safeReceive<SignRequest>(call)
                                if (body.barcode.isNullOrEmpty() || body.user_id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少条码或用户信息"))
                                    return@post
                                }
                                val record = dbHelper.findRecordByBarcode(body.barcode.trim())
                                if (record == null) {
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "该包裹未入库"))
                                    return@post
                                }
                                if (record["status"] == "签收" || record["status"] == "异常") {
                                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "该包裹已签收", "record" to record))
                                    return@post
                                }
                                val updated = dbHelper.signRecord(body.barcode.trim(), body.signer ?: "", body.exception_type ?: "")
                                call.respond(mapOf("success" to true, "record" to updated))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "签收请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "签收失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "签收失败")))
                            }
                        }

                        // 改地址
                        put("/api/records/{id}/address") {
                            try {
                                val id = call.parameters["id"]?.toIntOrNull()
                                if (id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少记录ID"))
                                    return@put
                                }
                                val body = safeReceive<AddressRequest>(call)
                                if (body.address.isNullOrEmpty()) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少地址"))
                                    return@put
                                }
                                val updated = dbHelper.changeAddress(id, body.address.trim(), body.user_name ?: "")
                                if (updated.containsKey("error")) {
                                    call.respond(HttpStatusCode.NotFound, updated)
                                    return@put
                                }
                                call.respond(mapOf("success" to true, "record" to updated))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "改地址请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "改地址失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "改地址失败")))
                            }
                        }

                        // 编辑包裹
                        put("/api/records/{id}") {
                            try {
                                val id = call.parameters["id"]?.toIntOrNull()
                                if (id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少记录ID"))
                                    return@put
                                }
                                val body = safeReceive<EditRequest>(call)
                                val updated = dbHelper.updateRecord(id, body.address, body.weight, body.note)
                                if (updated.containsKey("error")) {
                                    call.respond(HttpStatusCode.NotFound, updated)
                                    return@put
                                }
                                call.respond(mapOf("success" to true, "record" to updated))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "编辑请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "编辑失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "编辑失败")))
                            }
                        }

                        // 删除包裹
                        delete("/api/records/{id}") {
                            try {
                                val id = call.parameters["id"]?.toIntOrNull()
                                if (id == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少记录ID"))
                                    return@delete
                                }
                                val success = dbHelper.deleteRecord(id)
                                if (!success) {
                                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "记录不存在"))
                                    return@delete
                                }
                                call.respond(mapOf("success" to true, "message" to "已删除"))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "删除失败")))
                            }
                        }

                        // 查询记录
                        get("/api/records") {
                            try {
                                val userId = call.request.queryParameters["user_id"]?.toIntOrNull()
                                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                                val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 50
                                val status = call.request.queryParameters["status"]
                                call.respond(dbHelper.getRecords(userId, page, pageSize, status))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "查询失败")))
                            }
                        }

                        // 搜索记录
                        get("/api/records/search") {
                            try {
                                val q = call.request.queryParameters["q"] ?: ""
                                val status = call.request.queryParameters["status"]
                                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                                val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 50
                                call.respond(dbHelper.searchRecords(q, status, page, pageSize))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "搜索失败")))
                            }
                        }

                        // 统计
                        get("/api/stats") {
                            try {
                                val userId = call.request.queryParameters["user_id"]?.toIntOrNull()
                                call.respond(dbHelper.getStats(userId))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "统计失败")))
                            }
                        }

                        // 用户列表
                        get("/api/users") {
                            try {
                                call.respond(dbHelper.getAllUsers())
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "获取用户失败")))
                            }
                        }

                        // 合并手机数据
                        post("/api/merge") {
                            try {
                                val body = safeReceive<MergeRequest>(call)
                                val records = body.records ?: emptyList()
                                var created = 0
                                var duplicates = 0
                                for (r in records) {
                                    val existing = dbHelper.findRecordByBarcode(r.barcode ?: "")
                                    if (existing != null) {
                                        duplicates++
                                    } else {
                                        dbHelper.insertRecord(
                                            barcode = r.barcode ?: "", userId = r.user_id ?: 1,
                                            address = r.address ?: "", weight = r.weight ?: 0.0, note = r.note ?: ""
                                        )
                                        created++
                                    }
                                }
                                call.respond(mapOf("success" to true, "records_created" to created, "duplicates" to duplicates))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "合并请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "合并失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "合并失败")))
                            }
                        }

                        // 拉取数据
                        post("/api/pull") {
                            try {
                                val body = safeReceive<PullRequest>(call)
                                val users = dbHelper.getAllUsers()
                                val records = dbHelper.getAllRecords()
                                call.respond(mapOf("success" to true, "users" to users, "records" to records, "total_records" to records.size))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "拉取请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "拉取失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "拉取失败")))
                            }
                        }

                        // 备份
                        get("/api/backup") {
                            try {
                                val users = dbHelper.getAllUsers()
                                val records = dbHelper.getAllRecords()
                                call.respond(mapOf("version" to "1.0", "backup_time" to getCurrentTime(), "users" to users, "records" to records))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "备份失败")))
                            }
                        }

                        // 恢复
                        post("/api/restore") {
                            try {
                                val body = safeReceive<RestoreRequest>(call)
                                val data = body.data
                                if (data == null) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "备份数据格式错误"))
                                    return@post
                                }
                                var usersRestored = 0
                                var recordsRestored = 0
                                val users = data["users"] as? List<*> ?: emptyList<Any>()
                                for (u in users) {
                                    if (u is Map<*, *>) {
                                        val name = u["name"] as? String ?: continue
                                        dbHelper.loginUser(name)
                                        usersRestored++
                                    }
                                }
                                val records = data["records"] as? List<*> ?: emptyList<Any>()
                                for (r in records) {
                                    if (r is Map<*, *>) {
                                        val barcode = r["barcode"] as? String ?: continue
                                        val existing = dbHelper.findRecordByBarcode(barcode)
                                        if (existing == null) {
                                            dbHelper.insertRecord(
                                                barcode = barcode, userId = (r["user_id"] as? Number)?.toInt() ?: 1,
                                                address = r["address"] as? String ?: "", weight = (r["weight"] as? Number)?.toDouble() ?: 0.0,
                                                note = r["note"] as? String ?: ""
                                            )
                                            recordsRestored++
                                        }
                                    }
                                }
                                call.respond(mapOf("success" to true, "users_restored" to usersRestored, "records_restored" to recordsRestored))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "恢复请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "恢复失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "恢复失败")))
                            }
                        }

                        // 批量同步离线记录
                        post("/api/scan/batch") {
                            try {
                                val body = safeReceive<BatchScanRequest>(call)
                                val records = body.records ?: emptyList()
                                var inserted = 0
                                for (r in records) {
                                    val existing = dbHelper.findRecordByBarcode(r.barcode ?: "")
                                    if (existing == null) {
                                        dbHelper.insertRecord(
                                            barcode = r.barcode ?: "", userId = body.user_id ?: 1,
                                            address = r.address ?: "", weight = r.weight ?: 0.0, note = r.note ?: ""
                                        )
                                        inserted++
                                    }
                                }
                                call.respond(mapOf("success" to true, "inserted" to inserted))
                            } catch (e: kotlinx.serialization.SerializationException) {
                                Log.e(TAG, "批量入库请求JSON解析失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "请求格式错误"))
                            } catch (e: Exception) {
                                Log.e(TAG, "批量入库失败", e)
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "批量入库失败")))
                            }
                        }

                        // ==================== v3 新增 API ====================

                        // 设备心跳
                        post("/api/device/heartbeat") {
                            try {
                                val body = safeReceive<HeartbeatRequest>(call)
                                if (body.device_id.isNullOrEmpty()) {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "缺少 device_id"))
                                    return@post
                                }
                                dbHelper.recordHeartbeat(body.device_id, body.device_name ?: "", body.ip_address ?: "", body.user_name ?: "")
                                call.respond(mapOf("success" to true, "time" to getCurrentTime()))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "心跳失败")))
                            }
                        }

                        // 获取在线设备
                        get("/api/devices/online") {
                            try {
                                val timeout = call.request.queryParameters["timeout"]?.toIntOrNull() ?: 30
                                call.respond(mapOf(
                                    "success" to true, "online_count" to 1,
                                    "devices" to listOf(mapOf(
                                        "device_id" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                                        "device_name" to Build.MODEL, "ip_address" to getLocalIpAddress(),
                                        "user_name" to "", "last_heartbeat" to getCurrentTime()
                                    ))
                                ))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "获取在线设备失败")))
                            }
                        }

                        // 获取所有设备历史
                        get("/api/devices/history") {
                            try {
                                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                                call.respond(mapOf(
                                    "success" to true,
                                    "devices" to listOf(mapOf(
                                        "device_id" to Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                                        "device_name" to Build.MODEL, "ip_address" to getLocalIpAddress(),
                                        "user_name" to "", "device_group" to "", "last_heartbeat" to getCurrentTime()
                                    ))
                                ))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "获取设备历史失败")))
                            }
                        }

                        // 增量推送
                        post("/api/sync/push") {
                            try {
                                val body = safeReceive<SyncPushRequest>(call)
                                val records = body.records ?: emptyList()
                                var inserted = 0
                                var updated = 0
                                val conflicts = mutableListOf<Map<String, Any?>>()
                                for (r in records) {
                                    val barcode = r.barcode?.trim() ?: continue
                                    val existing = dbHelper.findRecordByBarcode(barcode)
                                    if (existing != null) {
                                        val phoneVersion = r.version ?: 1
                                        val serverVersion = (existing["version"] as? Number)?.toInt() ?: 1
                                        if (phoneVersion > serverVersion) {
                                            dbHelper.updateRecord(
                                                recordId = (existing["id"] as? Number)?.toInt() ?: 0,
                                                address = r.address, weight = r.weight, note = r.note
                                            )
                                            updated++
                                        } else if (phoneVersion == serverVersion) {
                                            conflicts.add(mapOf("barcode" to barcode, "server_record" to existing, "phone_record" to r))
                                        }
                                    } else {
                                        dbHelper.insertRecord(
                                            barcode = barcode, userId = r.user_id ?: 1,
                                            address = r.address ?: "", weight = r.weight ?: 0.0, note = r.note ?: ""
                                        )
                                        inserted++
                                    }
                                }
                                call.respond(mapOf("success" to true, "inserted" to inserted, "updated" to updated, "conflicts" to conflicts, "conflict_count" to conflicts.size))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "增量推送失败")))
                            }
                        }

                        // 增量拉取
                        post("/api/sync/pull") {
                            try {
                                val body = safeReceive<SyncPullRequest>(call)
                                val users = dbHelper.getAllUsers()
                                val records = dbHelper.getAllRecords()
                                call.respond(mapOf("success" to true, "users" to users, "records" to records, "total_records" to records.size, "server_time" to getCurrentTime()))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "增量拉取失败")))
                            }
                        }

                        // 同步状态
                        get("/api/sync/status") {
                            try {
                                val deviceId = call.request.queryParameters["device_id"] ?: ""
                                val stats = dbHelper.getStats(null)
                                call.respond(mapOf("success" to true, "total_records" to (stats["total"] ?: 0), "server_time" to getCurrentTime(), "device_id" to deviceId))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "获取同步状态失败")))
                            }
                        }

                        // 同步日志
                        get("/api/sync-logs") {
                            try {
                                call.respond(listOf<Map<String, Any?>>())
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "获取同步日志失败")))
                            }
                        }

                        // 离线检查
                        get("/api/devices/check-offline") {
                            try {
                                call.respond(mapOf("success" to true, "online_count" to 1, "offline_count" to 0, "offline_devices" to emptyList<Any>()))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "离线检查失败")))
                            }
                        }

                        // 设备分组
                        put("/api/devices/group") {
                            try {
                                val body = safeReceive<DeviceGroupRequest>(call)
                                call.respond(mapOf("success" to true))
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "设备分组失败")))
                            }
                        }
                    }
                }.start(wait = false)

                isRunning = true
                Log.i(TAG, "服务器启动成功: http://$ip:$PORT")
            } catch (e: Exception) {
                Log.e(TAG, "服务器启动失败", e)
                isRunning = false
            }
        }.start()
    }

    private fun stopKtorServer() {
        try {
            serverInstance?.stop(1000, 2000)
            serverInstance = null
            isRunning = false
            Log.i(TAG, "服务器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止服务器失败", e)
        }
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private suspend inline fun <reified T : Any> safeReceive(call: io.ktor.server.application.ApplicationCall): T {
        val rawBody = call.receive<String>()
        Log.d(TAG, "收到请求 body: $rawBody")
        return json.decodeFromString<T>(rawBody)
    }
}
