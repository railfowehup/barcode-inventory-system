package com.barcodescanner.ui.sort

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.barcodescanner.R
import com.barcodescanner.base.BaseScannerActivity
import com.barcodescanner.network.ApiClient
import org.json.JSONObject

/**
 * 分拣扫码页面
 * 扫码后查询包裹信息，确认后执行分拣
 */
class SortActivity : BaseScannerActivity() {

    override fun getLayoutId(): Int = R.layout.activity_sort

    override fun getScanHintText(): String = "扫码查询包裹信息并分拣"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    override fun onBarcodeScanned(barcode: String) {
        queryAndSort(barcode)
    }

    /**
     * 查询包裹信息并执行分拣
     */
    private fun queryAndSort(barcode: String) {
        hintText.text = "⏳ 查询包裹信息..."
        hintText.setTextColor(Color.parseColor("#FFD600"))

        ApiClient.searchRecordByBarcode(barcode, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                val records = data?.optJSONArray("records")
                if (records != null && records.length() > 0) {
                    val record = records.getJSONObject(0)
                    runOnUiThread { showSortConfirmDialog(barcode, record) }
                } else {
                    runOnUiThread {
                        hintText.text = "⚠️ 该包裹未入库，请先入库"
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
     * 显示分拣确认对话框
     */
    private fun showSortConfirmDialog(barcode: String, record: JSONObject) {
        val address = record.optString("address", "未知")
        val weight = record.optDouble("weight", 0.0)
        val status = record.optString("status", "未知")
        val userName = record.optString("user_name", "未知")
        val createdAt = record.optString("created_at", "").take(19)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("📤 分拣确认")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        layout.addView(TextView(this).apply {
            text = "条码: $barcode"
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        layout.addView(TextView(this).apply {
            text = "目的地: $address"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 8, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "重量: ${weight} kg"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 4, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "当前状态: $status"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 4, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "入库人: $userName"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 4, 0, 0)
        })
        layout.addView(TextView(this).apply {
            text = "入库时间: $createdAt"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 4, 0, 0)
        })

        builder.setView(layout)

        builder.setPositiveButton("确认分拣") { _, _ ->
            performSort(barcode)
        }

        builder.setNegativeButton("取消") { _, _ ->
            resumeScanning()
        }

        builder.setCancelable(false)
        builder.show()
    }

    /**
     * 执行分拣操作
     */
    private fun performSort(barcode: String) {
        ApiClient.sortBarcode(barcode, userId, deviceId, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                runOnUiThread {
                    hintText.text = "📤 分拣成功"
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
