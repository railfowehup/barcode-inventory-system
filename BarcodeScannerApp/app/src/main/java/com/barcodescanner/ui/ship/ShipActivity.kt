package com.barcodescanner.ui.ship

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
 * 出库扫码页面
 * 扫码后填写物流单号和收件人，确认后出库
 */
class ShipActivity : BaseScannerActivity() {

    override fun getLayoutId(): Int = R.layout.activity_ship

    override fun getScanHintText(): String = "扫码填写物流信息出库"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    override fun onBarcodeScanned(barcode: String) {
        showShipDialog(barcode)
    }

    /**
     * 显示出库信息填写对话框
     */
    private fun showShipDialog(barcode: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🚚 出库信息")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        // 条码
        layout.addView(TextView(this).apply {
            text = "条码: $barcode"
            setTextColor(Color.WHITE)
            textSize = 16f
        })

        // 物流单号
        layout.addView(TextView(this).apply {
            text = "物流单号:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 16, 0, 4)
        })
        val logisticsInput = EditText(this).apply {
            hint = "请输入物流单号"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            setPadding(12, 8, 12, 8)
        }
        layout.addView(logisticsInput)

        // 收件人
        layout.addView(TextView(this).apply {
            text = "收件人:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 12, 0, 4)
        })
        val recipientInput = EditText(this).apply {
            hint = "请输入收件人姓名"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            setPadding(12, 8, 12, 8)
        }
        layout.addView(recipientInput)

        builder.setView(layout)

        builder.setPositiveButton("确认出库") { _, _ ->
            val logisticsNo = logisticsInput.text.toString().trim()
            val recipient = recipientInput.text.toString().trim()

            if (logisticsNo.isEmpty() || recipient.isEmpty()) {
                runOnUiThread {
                    hintText.text = "⚠️ 请填写物流单号和收件人"
                    hintText.setTextColor(Color.parseColor("#FF5252"))
                    resumeScanning()
                }
                return@setPositiveButton
            }

            performShip(barcode, logisticsNo, recipient)
        }

        builder.setNegativeButton("取消") { _, _ ->
            resumeScanning()
        }

        builder.setCancelable(false)
        builder.show()
    }

    /**
     * 执行出库操作
     */
    private fun performShip(barcode: String, logisticsNo: String, recipient: String) {
        ApiClient.shipBarcode(barcode, userId, logisticsNo, recipient, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                runOnUiThread {
                    hintText.text = "🚚 出库成功"
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
