package com.barcodescanner.ui.changeaddress

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.barcodescanner.R
import com.barcodescanner.base.BaseScannerActivity
import com.barcodescanner.ApiClient
import org.json.JSONObject

/**
 * 改地址扫码页面
 * 扫码后查询包裹信息，填写新地址确认修改
 */
class ChangeAddressActivity : BaseScannerActivity() {

    override fun getLayoutId(): Int = R.layout.activity_change_address

    override fun getScanHintText(): String = "扫码查询包裹并修改地址"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    override fun onBarcodeScanned(barcode: String) {
        queryAndChangeAddress(barcode)
    }

    /**
     * 查询包裹信息并修改地址
     */
    private fun queryAndChangeAddress(barcode: String) {
        hintText.text = "⏳ 查询包裹信息..."
        hintText.setTextColor(Color.parseColor("#FFD600"))

        ApiClient.searchRecordByBarcode(barcode, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                val records = data?.optJSONArray("records")
                if (records != null && records.length() > 0) {
                    val record = records.getJSONObject(0)
                    runOnUiThread { showChangeAddressDialog(barcode, record) }
                } else {
                    runOnUiThread {
                        hintText.text = "⚠️ 该包裹未入库"
                        hintText.setTextColor(Color.parseColor("#FF5252"))
                        resumeScanning()
                    }
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    hintText.text = "⚠️ 查询失败: $error"
                    hintText.setTextColor(Color.parseColor("#FF5252"))
                    resumeScanning()
                }
            }
        })
    }

    /**
     * 显示改地址对话框
     */
    private fun showChangeAddressDialog(barcode: String, record: JSONObject) {
        val recordId = record.optInt("id", 0)
        val currentAddress = record.optString("address", "未知")
        val weight = record.optDouble("weight", 0.0)
        val status = record.optString("status", "未知")

        val builder = AlertDialog.Builder(this)
        builder.setTitle("✏️ 修改地址")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        // 包裹信息
        layout.addView(TextView(this).apply {
            text = "条码: $barcode"
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        layout.addView(TextView(this).apply {
            text = "当前地址: $currentAddress"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 8, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "重量: ${weight} kg | 状态: $status"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 4, 0, 0)
        })

        // 新地址输入
        layout.addView(TextView(this).apply {
            text = "新地址:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 16, 0, 4)
        })
        val addressInput = EditText(this).apply {
            hint = "请输入新地址"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            setPadding(12, 8, 12, 8)
        }
        layout.addView(addressInput)

        builder.setView(layout)

        builder.setPositiveButton("确认修改") { _, _ ->
            val newAddress = addressInput.text.toString().trim()
            if (newAddress.isEmpty()) {
                runOnUiThread {
                    hintText.text = "⚠️ 请输入新地址"
                    hintText.setTextColor(Color.parseColor("#FF5252"))
                    resumeScanning()
                }
                return@setPositiveButton
            }
            performChangeAddress(recordId, newAddress)
        }

        builder.setNegativeButton("取消") { _, _ ->
            resumeScanning()
        }

        builder.setCancelable(false)
        builder.show()
    }

    /**
     * 执行改地址操作
     */
    private fun performChangeAddress(recordId: Int, newAddress: String) {
        ApiClient.changeAddress(recordId, newAddress, userName, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                runOnUiThread {
                    hintText.text = "✏️ 地址修改成功: $newAddress"
                    hintText.setTextColor(Color.parseColor("#4CAF50"))
                    resumeScanning()
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    hintText.text = "⚠️ $error"
                    hintText.setTextColor(Color.parseColor("#FF5252"))
                    resumeScanning()
                }
            }
        })
    }
}
