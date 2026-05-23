package com.barcodescanner.ui.sign

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.barcodescanner.R
import com.barcodescanner.base.BaseScannerActivity
import com.barcodescanner.network.ApiClient
import org.json.JSONObject

/**
 * 签收扫码页面
 * 扫码后填写签收人和异常情况，确认后签收
 */
class SignActivity : BaseScannerActivity() {

    override fun getLayoutId(): Int = R.layout.activity_sign

    override fun getScanHintText(): String = "扫码填写签收信息"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    override fun onBarcodeScanned(barcode: String) {
        showSignDialog(barcode)
    }

    /**
     * 显示签收信息填写对话框
     */
    private fun showSignDialog(barcode: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("✅ 签收信息")

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

        // 签收人
        layout.addView(TextView(this).apply {
            text = "签收人:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 16, 0, 4)
        })
        val signerInput = EditText(this).apply {
            hint = "请输入签收人姓名"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#555555"))
            setPadding(12, 8, 12, 8)
        }
        layout.addView(signerInput)

        // 异常情况
        layout.addView(TextView(this).apply {
            text = "异常情况（选填）:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 12, 0, 4)
        })

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }

        val exceptions = arrayOf("无异常", "包裹破损", "包裹丢失", "其他异常")
        for (exc in exceptions) {
            val rb = RadioButton(this).apply {
                text = exc
                setTextColor(Color.WHITE)
                id = View.generateViewId()
            }
            radioGroup.addView(rb)
        }
        radioGroup.check(radioGroup.getChildAt(0).id)
        layout.addView(radioGroup)

        builder.setView(layout)

        builder.setPositiveButton("确认签收") { _, _ ->
            val signer = signerInput.text.toString().trim()
            if (signer.isEmpty()) {
                runOnUiThread {
                    hintText.text = "⚠️ 请填写签收人"
                    hintText.setTextColor(Color.parseColor("#FF5252"))
                    resumeScanning()
                }
                return@setPositiveButton
            }

            val selectedId = radioGroup.checkedRadioButtonId
            val selectedRadio = radioGroup.findViewById<RadioButton>(selectedId)
            val exceptionText = selectedRadio?.text?.toString() ?: "无异常"
            val exceptionType = if (exceptionText == "无异常") "" else exceptionText

            performSign(barcode, signer, exceptionType)
        }

        builder.setNegativeButton("取消") { _, _ ->
            resumeScanning()
        }

        builder.setCancelable(false)
        builder.show()
    }

    /**
     * 执行签收操作
     */
    private fun performSign(barcode: String, signer: String, exceptionType: String) {
        ApiClient.signBarcode(barcode, userId, signer, exceptionType, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                runOnUiThread {
                    val msg = if (exceptionType.isNotEmpty()) "⚠️ 已标记异常: $exceptionType" else "✅ 签收成功"
                    hintText.text = msg
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
