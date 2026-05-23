package com.barcodescanner.ui.inbound

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.barcodescanner.R
import com.barcodescanner.base.BaseScannerActivity
import com.barcodescanner.ApiClient
import com.barcodescanner.ui.theme.AppTheme
import com.barcodescanner.utils.WeightCalculator
import org.json.JSONObject

/**
 * 入库扫码页面
 * 扫码后自动弹出地址选择对话框，确认后入库
 */
class InboundActivity : BaseScannerActivity() {

    override fun getLayoutId(): Int = R.layout.activity_inbound

    override fun getScanHintText(): String = "将条形码对准框内自动入库"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 返回按钮
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    override fun onBarcodeScanned(barcode: String) {
        showInboundDialog(barcode)
    }

    /**
     * 显示入库确认对话框
     */
    private fun showInboundDialog(barcode: String) {
        val weight = WeightCalculator.calculate(barcode)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("📦 包裹入库确认")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        // 条码信息
        layout.addView(TextView(this).apply {
            text = "条码: $barcode"
            setTextColor(Color.WHITE)
            textSize = 16f
        })

        // 重量信息
        layout.addView(TextView(this).apply {
            text = "重量: ${weight} kg（由条码自动生成）"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 14f
            setPadding(0, 12, 0, 12)
        })

        // 地址选择
        layout.addView(TextView(this).apply {
            text = "选择目的地地址:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val addresses = arrayOf("北京", "上海", "吉林")
        for (addr in addresses) {
            val rb = RadioButton(this).apply {
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

        builder.setNegativeButton("取消") { _, _ ->
            resumeScanning()
        }

        builder.setCancelable(false)
        builder.show()
    }

    /**
     * 执行入库操作
     */
    private fun performInbound(barcode: String, address: String, weight: Double) {
        if (isOffline || userId == 0) {
            saveOfflineRecord(barcode, address, weight)
            runOnUiThread {
                hintText.text = "📴 离线已保存（连网后自动同步）"
                hintText.setTextColor(Color.parseColor("#FFD600"))
                resumeScanning()
            }
            return
        }

        ApiClient.scanBarcode(barcode, userId, address, weight, "", object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                runOnUiThread {
                    hintText.text = "✅ 入库成功 ($address, ${weight}kg)"
                    hintText.setTextColor(Color.GREEN)
                    resumeScanning()
                }
            }

            override fun onError(error: String?) {
                if (error?.contains("已入库") == true) {
                    runOnUiThread {
                        hintText.text = "⚠️ 该包裹已入库，不可重复入库"
                        hintText.setTextColor(Color.parseColor("#FF5252"))
                        resumeScanning()
                    }
                } else {
                    saveOfflineRecord(barcode, address, weight)
                    runOnUiThread {
                        hintText.text = "✅ 已缓存，待同步"
                        hintText.setTextColor(Color.parseColor("#FFD600"))
                        resumeScanning()
                    }
                }
            }
        })
    }
}
