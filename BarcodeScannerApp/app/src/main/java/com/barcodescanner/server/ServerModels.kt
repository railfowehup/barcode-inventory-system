package com.barcodescanner.server

import kotlinx.serialization.Serializable

/**
 * 嵌入式服务器 API 请求体数据类
 */
@Serializable
data class LoginRequest(val name: String? = null)

@Serializable
data class ScanRequest(
    val barcode: String? = null,
    val user_id: Int? = null,
    val address: String? = null,
    val weight: Double? = null,
    val note: String? = null
)

@Serializable
data class SortRequest(
    val barcode: String? = null,
    val user_id: Int? = null,
    val device_id: String? = null
)

@Serializable
data class ShipRequest(
    val barcode: String? = null,
    val user_id: Int? = null,
    val logistics_no: String? = null,
    val recipient: String? = null
)

@Serializable
data class SignRequest(
    val barcode: String? = null,
    val user_id: Int? = null,
    val signer: String? = null,
    val exception_type: String? = null
)

@Serializable
data class AddressRequest(
    val address: String? = null,
    val user_name: String? = null
)

@Serializable
data class EditRequest(
    val address: String? = null,
    val weight: Double? = null,
    val note: String? = null
)

@Serializable
data class MergeRecord(
    val barcode: String? = null,
    val user_id: Int? = null,
    val address: String? = null,
    val weight: Double? = null,
    val note: String? = null,
    val status: String? = null,
    val device_id: String? = null
)

@Serializable
data class MergeRequest(
    val records: List<MergeRecord>? = null,
    val users: List<Map<String, String>>? = null,
    val device_id: String? = null,
    val user_name: String? = null,
    val mode: String? = null
)

@Serializable
data class PullRequest(
    val mode: String? = null,
    val since: String? = null
)

@Serializable
data class RestoreRequest(
    val data: Map<String, List<Map<String, kotlinx.serialization.json.JsonElement>>>? = null
)

@Serializable
data class BatchRecord(
    val barcode: String? = null,
    val address: String? = null,
    val weight: Double? = null,
    val note: String? = null,
    val timestamp: Long? = null
)

@Serializable
data class BatchScanRequest(
    val records: List<BatchRecord>? = null,
    val user_id: Int? = null
)

// v3 新增
@Serializable
data class HeartbeatRequest(
    val device_id: String? = null,
    val device_name: String? = null,
    val ip_address: String? = null,
    val user_name: String? = null
)

@Serializable
data class SyncRecord(
    val barcode: String? = null,
    val user_id: Int? = null,
    val address: String? = null,
    val weight: Double? = null,
    val note: String? = null,
    val status: String? = null,
    val version: Int? = null,
    val updated_at: String? = null
)

@Serializable
data class SyncPushRequest(
    val records: List<SyncRecord>? = null,
    val device_id: String? = null,
    val user_name: String? = null
)

@Serializable
data class SyncPullRequest(
    val since: String? = null,
    val device_id: String? = null
)

@Serializable
data class DeviceGroupRequest(
    val device_id: String? = null,
    val group: String? = null
)

@Serializable
data class SortCheckRequest(
    val barcode: String? = null
)

@Serializable
data class SignCheckRequest(
    val barcode: String? = null
)

@Serializable
data class CheckResponse(
    val allowed: Boolean = false,
    val message: String = "",
    val record: Map<String, Any?>? = null
)
