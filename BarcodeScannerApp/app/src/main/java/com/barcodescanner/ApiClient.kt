package com.barcodescanner

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP API 客户端 - 与后端服务器通信
 * v3: 新增增量同步、设备管理 API
 * Fix #8: 移除硬编码 IP，改为空字符串默认值，由调用方传入
 */
object ApiClient {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private const val TIMEOUT = 10L

    private var serverUrl = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setServerUrl(url: String) {
        serverUrl = url
    }

    fun getServerUrl(): String = serverUrl

    // ==================== 回调接口 ====================

    interface ApiCallback {
        fun onSuccess(data: JSONObject?)
        fun onError(error: String?)
    }

    // ==================== 登录 ====================

    fun login(name: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("name", name)
            }
            post("/api/login", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 扫码入库（带地址和重量） ====================

    fun scanBarcode(barcode: String, userId: Int, address: String, weight: Double, note: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("barcode", barcode)
                put("user_id", userId)
                put("address", address)
                put("weight", weight)
                put("note", note)
            }
            post("/api/scan", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 分拣 ====================

    fun sortBarcode(barcode: String, userId: Int, deviceId: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("barcode", barcode)
                put("user_id", userId)
                put("device_id", deviceId)
            }
            post("/api/sort", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 出库 ====================

    fun shipBarcode(barcode: String, userId: Int, logisticsNo: String, recipient: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("barcode", barcode)
                put("user_id", userId)
                put("logistics_no", logisticsNo)
                put("recipient", recipient)
            }
            post("/api/ship", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 签收 ====================

    fun signBarcode(barcode: String, userId: Int, signer: String, exceptionType: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("barcode", barcode)
                put("user_id", userId)
                put("signer", signer)
                put("exception_type", exceptionType)
            }
            post("/api/sign", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 改地址 ====================

    fun changeAddress(recordId: Int, address: String, userName: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("address", address)
                put("user_name", userName)
            }
            put("/api/records/$recordId/address", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 设备心跳上报 ====================

    fun sendLogout(deviceId: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
            }
            post("/api/device/logout", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("退出请求失败: ${e.message}")
        }
    }

    fun sendHeartbeat(deviceId: String, deviceName: String, ipAddress: String, userName: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("device_name", deviceName)
                put("ip_address", ipAddress)
                put("user_name", userName)
            }
            post("/api/device/heartbeat", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("心跳请求失败: ${e.message}")
        }
    }

    // ==================== 批量同步离线记录 ====================

    fun syncOfflineRecords(records: List<JSONObject>, userId: Int, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("records", JSONArray(records))
                put("user_id", userId)
            }
            post("/api/scan/batch", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 合并手机数据到服务器 ====================

    fun mergeToServer(users: JSONArray, records: JSONArray, deviceId: String, userName: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("users", users)
                put("records", records)
                put("device_id", deviceId)
                put("user_name", userName)
                put("mode", "merge")
            }
            post("/api/merge", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 从服务器拉取数据 ====================

    fun pullFromServer(mode: String, since: String?, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("mode", mode)
                if (!since.isNullOrEmpty()) {
                    put("since", since)
                }
            }
            post("/api/pull", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 备份 ====================

    fun backup(callback: ApiCallback) {
        get("/api/backup", callback)
    }

    // ==================== 恢复 ====================

    fun restore(data: JSONObject, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("data", data)
            }
            post("/api/restore", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("请求构建失败: ${e.message}")
        }
    }

    // ==================== 查询记录（可筛状态） ====================

    fun getRecords(userId: Int, page: Int, status: String? = null, callback: ApiCallback) {
        var url = "/api/records?user_id=$userId&page=$page&page_size=50"
        if (!status.isNullOrEmpty()) {
            url += "&status=${status}"
        }
        get(url, callback)
    }

    // ==================== 搜索记录 ====================

    fun searchRecords(query: String, status: String? = null, page: Int = 1, callback: ApiCallback) {
        var url = "/api/records/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page&page_size=50"
        if (!status.isNullOrEmpty()) {
            url += "&status=${status}"
        }
        get(url, callback)
    }

    // ==================== 按条码搜索单条记录 ====================

    fun searchRecordByBarcode(barcode: String, callback: ApiCallback) {
        val url = "/api/records/search?q=${java.net.URLEncoder.encode(barcode, "UTF-8")}&page_size=1"
        get(url, callback)
    }

    // ==================== 统计 ====================

    fun getStats(userId: Int, callback: ApiCallback) {
        get("/api/stats?user_id=$userId", callback)
    }

    // ==================== v3 增量同步 API ====================

    /**
     * 增量推送本地记录到服务器
     */
    fun syncPush(records: JSONArray, deviceId: String, userName: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                put("records", records)
                put("device_id", deviceId)
                put("user_name", userName)
            }
            post("/api/sync/push", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("增量推送请求失败: ${e.message}")
        }
    }

    /**
     * 增量拉取服务器记录
     */
    fun syncPull(since: String?, deviceId: String, callback: ApiCallback) {
        try {
            val body = JSONObject().apply {
                if (!since.isNullOrEmpty()) {
                    put("since", since)
                }
                put("device_id", deviceId)
            }
            post("/api/sync/pull", body.toString(), callback)
        } catch (e: Exception) {
            callback.onError("增量拉取请求失败: ${e.message}")
        }
    }

    /**
     * 获取同步状态
     */
    fun getSyncStatus(deviceId: String, callback: ApiCallback) {
        get("/api/sync/status?device_id=$deviceId", callback)
    }

    // ==================== HTTP 方法 ====================

    private fun get(path: String, callback: ApiCallback) {
        val request = Request.Builder()
            .url("$serverUrl$path")
            .get()
            .build()
        executeRequest(request, callback)
    }

    private fun post(path: String, jsonBody: String, callback: ApiCallback) {
        val body = jsonBody.toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$serverUrl$path")
            .post(body)
            .build()
        executeRequest(request, callback)
    }

    private fun put(path: String, jsonBody: String, callback: ApiCallback) {
        val body = jsonBody.toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$serverUrl$path")
            .put(body)
            .build()
        executeRequest(request, callback)
    }

    private fun executeRequest(request: Request, callback: ApiCallback) {
        Thread {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    // 统一解包：服务端返回 {success: true, data: {...}}
                    // 回调拿到的 data 参数直接是 data 字段的内容
                    val data = if (json.has("data") && !json.isNull("data")) {
                        json.get("data")
                    } else {
                        json
                    }
                    if (data is JSONObject) {
                        mainHandler.post { callback.onSuccess(data) }
                    } else {
                        mainHandler.post { callback.onSuccess(json) }
                    }
                } else {

                    var errorMsg = "请求失败: ${response.code}"
                    try {
                        val errJson = JSONObject(responseBody)
                        if (errJson.has("error")) {
                            errorMsg = errJson.getString("error")
                        }
                    } catch (_: Exception) {}
                    val msg = errorMsg
                    mainHandler.post { callback.onError(msg) }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError("网络错误: ${e.message}") }
            }
        }.start()
    }
}
