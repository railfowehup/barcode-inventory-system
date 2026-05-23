package com.barcodescanner.scanner

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.barcodescanner.ApiClient
import com.barcodescanner.R
import com.barcodescanner.network.ServerConfig
import org.json.JSONObject

/**
 * 扫码后的操作对话框（入库、分拣、出库、签收、改地址）
 * 由 ScannerActivity 调用，回调结果通过 ScanActionCallback 返回
 */
class ScanActions(
    private val activity: android.app.Activity,
    private val callback: ScanActionCallback
) {

    interface ScanActionCallback {
        fun resumeScanning()
        fun showHint(text: String, color: Int)
        fun isOffline(): Boolean
        fun userId(): Int
        fun deviceId(): String
        fun userName(): String
        fun saveOfflineRecord(barcode: String, address: String, weight: Double)
    }

    /**
     * 显示操作选择对话框
     */
    fun showOperationDialog(barcode: String, formatName: String) {
        val options = arrayOf("📦 入库", "📤 分拣", "🚚 出库", "✅ 签收", "✏️ 改地址")
        val actions: List<() -> Unit> = listOf(
            { showInboundDialog(barcode) },
            { showSortDialog(barcode) },
            { showShipDialog(barcode) },
            { showSignDialog(barcode) },
            { showChangeAddressDialog(barcode) }
        )

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("✅ 条码: $barcode")
        builder.setMessage("格式: $formatName\n请选择操作:")

        val listView = android.widget.ListView(activity)
        val adapter = object : android.widget.ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                (view as? android.widget.TextView)?.apply {
                    setTextColor(Color.parseColor("#FFFFFF"))
                    textSize = 16f
                    setPadding(40, 20, 40, 20)
                }
                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, which, _ -> actions[which]() }
        listView.setBackgroundColor(Color.parseColor("#333333"))

        builder.setView(listView)
        builder.setCancelable(true)
        builder.setOnCancelListener { callback.resumeScanning() }
        builder.show()
    }

    // ==================== 入库 ====================

    private fun showInboundDialog(barcode: String) {
        val weight = calculateWeight(barcode)

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("📦 包裹入库确认")

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        layout.addView(TextView(activity).apply {
            text = "条码: $barcode"
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        layout.addView(TextView(activity).apply {
            text = "重量: ${weight} kg（由条码自动生成）"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 12, 0, 12)
        })
        layout.addView(TextView(activity).apply {
            text = "选择目的地地址:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })

        val radioGroup = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
        }
        val addresses = arrayOf("北京", "上海", "吉林")
        for (addr in addresses) {
            val rb = RadioButton(activity).apply {
                text = addr
                setTextColor(Color.WHITE)
                id = View.generateViewId()
            }
            radioGroup.addView(rb)
        }
        radioGroup.check(radioGroup.getChildAt(0).id)
        layout.addView(radioGroup)

        builder.setView(layout)

        builder.setPositiveButton("确认入库") { _, _ ->
            val selectedId = radioGroup.checkedRadioButtonId
            val selectedRadio = radioGroup.findViewById<RadioButton>(selectedId)
            val address = selectedRadio?.text?.toString() ?: "北京"
            performInbound(barcode, address, weight)
        }
        builder.setNegativeButton("取消") { _, _ -> callback.resumeScanning() }
        builder.setCancelable(false)
        builder.show()
    }

    private fun performInbound(barcode: String, address: String, weight: Double) {
        if (callback.isOffline() || callback.userId() == 0) {
            callback.saveOfflineRecord(barcode, address, weight)
            callback.showHint("📴 离线已保存（连网后自动同步）", Color.parseColor("#FFD600"))
            callback.resumeScanning()
            return
        }

        ApiClient.scanBarcode(barcode, callback.userId(), address, weight, "", object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                callback.showHint("✅ 入库成功 ($address, ${weight}kg)", Color.GREEN)
                callback.resumeScanning()
            }
            override fun onError(error: String?) {
                if (error?.contains("已入库") == true) {
                    callback.showHint("⚠️ 该包裹已入库，不可重复入库", Color.parseColor("#FF5252"))
                    callback.resumeScanning()
                } else {
                    callback.saveOfflineRecord(barcode, address, weight)
                    callback.showHint("✅ 已缓存，待同步", Color.parseColor("#FFD600"))
                    callback.resumeScanning()
                }
            }
        })
    }

    // ==================== 分拣 ====================

    private fun showSortDialog(barcode: String) {
        if (callback.isOffline() || callback.userId() == 0) {
            callback.showHint("⚠️ 分拣需要联网", Color.parseColor("#FF5252"))
            callback.resumeScanning()
            return
        }

        callback.showHint("⏳ 查询包裹信息...", Color.parseColor("#FFD600"))

        ApiClient.searchRecordByBarcode(barcode, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                val records = data?.optJSONArray("records")
                if (records != null && records.length() > 0) {
                    val record = records.getJSONObject(0)
                    activity.runOnUiThread { showSortConfirmDialog(barcode, record) }
                } else {
                    callback.showHint("⚠️ 该包裹未入库，请先入库", Color.parseColor("#FF5252"))
                    callback.resumeScanning()
                }
            }
            override fun onError(error: String?) {
                callback.showHint("⚠️ 查询失败: $error", Color.parseColor("#FF5252"))
                callback.resumeScanning()
            }
        })
    }

    private fun showSortConfirmDialog(barcode: String, record: JSONObject) {
        val address = record.optString("address", "未知")
        val weight = record.optDouble("weight", 0.0)
        val status = record.optString("status", "未知")
        val userName = record.optString("user_name", "未知")
        val createdAt = record.optString("created_at", "").take(19)

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("📤 分拣确认")

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        layout.addView(TextView(activity).apply {
            text = "条码: $barcode"; setTextColor(Color.WHITE); textSize = 16f
        })
        layout.addView(TextView(activity).apply {
            text = "目的地: $address"; setTextColor(Color.parseColor("#FFD600")); textSize = 14f; setPadding(0, 8, 0, 0)
        })
        layout.addView(TextView(activity).apply {
            text = "重量: ${weight} kg"; setTextColor(Color.parseColor("#FFD600")); textSize = 14f; setPadding(0, 4, 0, 0)
        })
        layout.addView(TextView(activity).apply {
            text = "当前状态: $status"; setTextColor(Color.parseColor("#FFD600")); textSize = 14f; setPadding(0, 4, 0, 0)
        })
        layout.addView(TextView(activity).apply {
            text = "入库人: $userName"; setTextColor(Color.parseColor("#FFD600")); textSize = 14f; setPadding(0, 4, 0, 0)
        })
        layout.addView(TextView(activity).apply {
            text = "入库时间: $createdAt"; setTextColor(Color.parseColor("#FFD600")); textSize = 14f; setPadding(0, 4, 0, 0)
        })

        builder.setView(layout)
        builder.setPositiveButton("确认分拣") { _, _ -> performSort(barcode) }
        builder.setNegativeButton("取消") { _, _ -> callback.resumeScanning() }
        builder.setCancelable(false)
        builder.show()
    }

    private fun performSort(barcode: String) {
        ApiClient.sortBarcode(barcode, callback.userId(), callback.deviceId(), object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                callback.showHint("📤 分拣成功", Color.parseColor("#4CAF50"))
                callback.resumeScanning()
            }
            override fun onError(error: String?) {
                callback.showHint("⚠️ $error", Color.parseColor("#FF5252"))
                callback.resumeScanning()
            }
        })
    }

    // ==================== 出库 ====================

    private fun showShipDialog(barcode: String) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("🚚 出库")
        builder.setMessage("条码: $barcode")

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        layout.addView(TextView(activity).apply {
            text = "物流单号:"; setTextColor(Color.WHITE); textSize = 14f
        })
        val logisticsInput = android.widget.EditText(activity).apply {
            hint = "输入物流单号"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.edittext_bg)
        }
        layout.addView(logisticsInput)

        layout.addView(TextView(activity).apply {
            text = "收件人:"; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 12, 0, 0)
        })
        val recipientInput = android.widget.EditText(activity).apply {
            hint = "输入收件人姓名"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.edittext_bg)
        }
        layout.addView(recipientInput)

        builder.setView(layout)
        builder.setPositiveButton("确认出库") { _, _ ->
            val logisticsNo = logisticsInput.text.toString().trim()
            val recipient = recipientInput.text.toString().trim()
            performShip(barcode, logisticsNo, recipient)
        }
        builder.setNegativeButton("取消") { _, _ -> callback.resumeScanning() }
        builder.setCancelable(false)
        builder.show()
    }

    private fun performShip(barcode: String, logisticsNo: String, recipient: String) {
        if (callback.isOffline() || callback.userId() == 0) {
            callback.showHint("⚠️ 出库需要联网", Color.parseColor("#FF5252"))
            callback.resumeScanning()
            return
        }

        ApiClient.shipBarcode(barcode, callback.userId(), logisticsNo, recipient, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                callback.showHint("🚚 出库成功", Color.parseColor("#4CAF50"))
                callback.resumeScanning()
            }
            override fun onError(error: String?) {
                callback.showHint("⚠️ $error", Color.parseColor("#FF5252"))
                callback.resumeScanning()
            }
        })
    }

    // ==================== 签收 ====================

    private fun showSignDialog(barcode: String) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("✅ 签收")
        builder.setMessage("条码: $barcode")

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        layout.addView(TextView(activity).apply {
            text = "签收人:"; setTextColor(Color.WHITE); textSize = 14f
        })
        val signerInput = android.widget.EditText(activity).apply {
            hint = "输入签收人姓名"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.edittext_bg)
        }
        layout.addView(signerInput)

        layout.addView(TextView(activity).apply {
            text = "异常类型（无异常留空）:"; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 12, 0, 0)
        })
        val exceptionInput = android.widget.EditText(activity).apply {
            hint = "如：破损、丢失、拒收"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.edittext_bg)
        }
        layout.addView(exceptionInput)

        builder.setView(layout)
        builder.setPositiveButton("确认签收") { _, _ ->
            val signer = signerInput.text.toString().trim()
            val exceptionType = exceptionInput.text.toString().trim()
            performSign(barcode, signer, exceptionType)
        }
        builder.setNegativeButton("取消") { _, _ -> callback.resumeScanning() }
        builder.setCancelable(false)
        builder.show()
    }

    private fun performSign(barcode: String, signer: String, exceptionType: String) {
        if (callback.isOffline() || callback.userId() == 0) {
            callback.showHint("⚠️ 签收需要联网", Color.parseColor("#FF5252"))
            callback.resumeScanning()
            return
        }

        ApiClient.signBarcode(barcode, callback.userId(), signer, exceptionType, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                val msg = if (exceptionType.isNotEmpty()) "⚠️ 已标记异常: $exceptionType" else "✅ 签收成功"
                val color = if (exceptionType.isNotEmpty()) Color.parseColor("#FF9800") else Color.parseColor("#4CAF50")
                callback.showHint(msg, color)
                callback.resumeScanning()
            }
            override fun onError(error: String?) {
                callback.showHint("⚠️ $error", Color.parseColor("#FF5252"))
                callback.resumeScanning()
            }
        })
    }

    // ==================== 改地址 ====================

    private fun showChangeAddressDialog(barcode: String) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("✏️ 改地址")
        builder.setMessage("条码: $barcode")

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        layout.addView(TextView(activity).apply {
            text = "选择新地址:"; setTextColor(Color.WHITE); textSize = 14f
        })

        val radioGroup = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
        }
        val addresses = arrayOf("北京", "上海", "吉林")
        for (addr in addresses) {
            val rb = RadioButton(activity).apply {
                text = addr; setTextColor(Color.WHITE); id = View.generateViewId()
            }
            radioGroup.addView(rb)
        }
        radioGroup.check(radioGroup.getChildAt(0).id)
        layout.addView(radioGroup)

        builder.setView(layout)
        builder.setPositiveButton("确认改地址") { _, _ ->
            val selectedId = radioGroup.checkedRadioButtonId
            val selectedRadio = radioGroup.findViewById<RadioButton>(selectedId)
            val address = selectedRadio?.text?.toString() ?: "北京"
            performChangeAddress(barcode, address)
        }
        builder.setNegativeButton("取消") { _, _ -> callback.resumeScanning() }
        builder.setCancelable(false)
        builder.show()
    }

    private fun performChangeAddress(barcode: String, address: String) {
        if (callback.isOffline() || callback.userId() == 0) {
            callback.showHint("⚠️ 改地址需要联网", Color.parseColor("#FF5252"))
            callback.resumeScanning()
            return
        }

        ApiClient.searchRecordByBarcode(barcode, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                try {
                    val records = data?.optJSONArray("records")
                    if (records != null && records.length() > 0) {
                        val r = records.getJSONObject(0)
                        val recordId = r.optInt("id", 0)
                        if (recordId > 0) {
                            ApiClient.changeAddress(recordId, address, callback.userName(), object : ApiClient.ApiCallback {
                                override fun onSuccess(data: JSONObject?) {
                                    callback.showHint("✏️ 地址已改为: $address", Color.parseColor("#4CAF50"))
                                    callback.resumeScanning()
                                }
                                override fun onError(error: String?) {
                                    callback.showHint("⚠️ $error", Color.parseColor("#FF5252"))
                                    callback.resumeScanning()
                                }
                            })
                            return@onSuccess
                        }
                    }
                    callback.showHint("⚠️ 未找到该包裹记录", Color.parseColor("#FF5252"))
                    callback.resumeScanning()
                } catch (e: Exception) {
                    callback.showHint("⚠️ 查询失败: ${e.message}", Color.parseColor("#FF5252"))
                    callback.resumeScanning()
                }
            }
            override fun onError(error: String?) {
                callback.showHint("⚠️ $error", Color.parseColor("#FF5252"))
                callback.resumeScanning()
            }
        })
    }

    // ==================== 工具 ====================

    /**
     * 计算重量：取条码最后2位数字
     */
    private fun calculateWeight(barcode: String): Double {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length < 2) return 1.0
        val lastTwo = digits.substring(digits.length - 2)
        return lastTwo.toDoubleOrNull() ?: 1.0
    }
}
