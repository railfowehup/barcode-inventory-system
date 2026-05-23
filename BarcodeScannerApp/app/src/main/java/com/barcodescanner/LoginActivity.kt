package com.barcodescanner

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.barcodescanner.network.ServerConfig
import org.json.JSONArray
import org.json.JSONObject


/**
 * 登录页面 - 输入姓名进入系统，也可启动手机服务器
 * 支持同步/备份/角色选择
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var serverUrlInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var loginBtn: CardView
    private lateinit var scanQrBtn: CardView
    private lateinit var offlineBtn: CardView
    private lateinit var startServerBtn: CardView
    private lateinit var stopServerBtn: CardView
    private lateinit var syncBtn: CardView
    private lateinit var backupBtn: CardView
    private lateinit var statusText: TextView
    private lateinit var deviceIdText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var serverQrImage: ImageView

    companion object {
        private const val SCAN_QR_REQUEST_CODE = 1001
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        nameInput = findViewById(R.id.nameInput)
        loginBtn = findViewById(R.id.loginBtn)
        scanQrBtn = findViewById(R.id.scanQrBtn)
        offlineBtn = findViewById(R.id.offlineBtn)
        startServerBtn = findViewById(R.id.startServerBtn)
        stopServerBtn = findViewById(R.id.stopServerBtn)
        syncBtn = findViewById(R.id.syncBtn)
        backupBtn = findViewById(R.id.backupBtn)
        statusText = findViewById(R.id.statusText)
        deviceIdText = findViewById(R.id.deviceIdText)
        serverStatusText = findViewById(R.id.serverStatusText)
        serverQrImage = findViewById(R.id.serverQrImage)


        // 读取上次保存的服务器地址和用户名
        val prefs = getSharedPreferences("barcode_prefs", MODE_PRIVATE)
        serverUrlInput.setText(prefs.getString("server_url", "http://192.168.38.62:3000"))
        nameInput.setText(prefs.getString("user_name", ""))

        // 显示设备ID
        val deviceId = getMyDeviceId()
        deviceIdText.text = "设备ID: $deviceId"
        deviceIdText.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("device_id", deviceId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "设备ID已复制", Toast.LENGTH_SHORT).show()
        }

        // 检查服务器是否已在运行
        updateServerStatus()

        loginBtn.setOnClickListener { attemptLogin() }
        scanQrBtn.setOnClickListener { startQrCodeScanner() }
        offlineBtn.setOnClickListener { enterOfflineMode() }
        startServerBtn.setOnClickListener { startServer() }
        stopServerBtn.setOnClickListener { stopServer() }
        syncBtn.setOnClickListener { showSyncDialog() }
        backupBtn.setOnClickListener { showBackupDialog() }

    }

    /**
     * 获取设备唯一ID（Android ID，重装不变）
     */
    private fun getMyDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: "unknown"
    }

    /**
     * 更新服务器状态显示
     */
    private fun updateServerStatus() {
        if (EmbeddedServer.isServerRunning()) {
            val ip = EmbeddedServer.getLocalIpAddress()
            // 隐藏启动按钮，显示停止按钮
            startServerBtn.visibility = View.GONE
            stopServerBtn.visibility = View.VISIBLE
            serverStatusText.text = "服务器地址: http://$ip:3000"
            serverStatusText.visibility = View.VISIBLE
            // 自动填入服务器地址
            serverUrlInput.setText("http://$ip:3000")
        } else {
            startServerBtn.visibility = View.VISIBLE
            stopServerBtn.visibility = View.GONE
        }
    }

    /**
     * 启动手机服务器
     */
    private fun startServer() {
        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    200
                )
                return
            }
        }

        val intent = Intent(this, EmbeddedServer::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // 等待服务器启动
        startServerBtn.isEnabled = false
        val textView = startServerBtn.getChildAt(0)?.let {
            if (it is android.widget.LinearLayout) {
                it.getChildAt(1) as? TextView
            } else null
        }
        textView?.text = "⏳ 启动中..."
        serverStatusText.visibility = View.VISIBLE
        serverStatusText.text = "正在启动服务器..."

        // 延迟检查服务器状态
        android.os.Handler(mainLooper).postDelayed({
            if (EmbeddedServer.isServerRunning()) {
                val ip = EmbeddedServer.getLocalIpAddress()
                // 隐藏启动按钮，显示停止按钮
                startServerBtn.visibility = View.GONE
                stopServerBtn.visibility = View.VISIBLE
                serverStatusText.text = "服务器地址: http://$ip:3000"
                serverStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                serverUrlInput.setText("http://$ip:3000")
                Toast.makeText(this, "服务器已启动: http://$ip:3000", Toast.LENGTH_LONG).show()
                // 生成二维码
                generateServerQrCode()
            } else {
                startServerBtn.isEnabled = true
                textView?.text = "📡 启动手机服务器"
                startServerBtn.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                serverStatusText.text = "启动失败，请重试"
                serverStatusText.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            }
        }, 2000)

    }

    /**
     * 停止手机服务器
     */
    private fun stopServer() {
        AlertDialog.Builder(this)
            .setTitle("确认停止服务器")
            .setMessage("停止服务器后，其他设备将无法连接到此手机。确定要停止吗？")
            .setPositiveButton("确认停止") { _, _ ->
                // 停止服务
                val intent = Intent(this, EmbeddedServer::class.java)
                stopService(intent)
                // 重置 UI
                startServerBtn.visibility = View.VISIBLE
                startServerBtn.isEnabled = true
                val textView = startServerBtn.getChildAt(0)?.let {
                    if (it is android.widget.LinearLayout) {
                        it.getChildAt(1) as? TextView
                    } else null
                }
                textView?.text = "📡 启动手机服务器"
                startServerBtn.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                stopServerBtn.visibility = View.GONE
                serverStatusText.visibility = View.GONE
                serverQrImage.visibility = View.GONE
                showStatus("✅ 服务器已停止")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startServer()
            } else {
                Toast.makeText(this, "需要通知权限才能启动服务器", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== 同步 ====================

    /**
     * 显示同步对话框
     */
    private fun showSyncDialog() {
        val options = arrayOf("📤 上传本机数据到服务器", "📥 从服务器拉取数据到本机")
        AlertDialog.Builder(this)
            .setTitle("🔄 数据同步")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> syncToServer()
                    1 -> pullFromServer()
                }
            }
            .show()
    }

    /**
     * 上传本机数据到服务器
     */
    private fun syncToServer() {
        val serverUrl = serverUrlInput.text.toString().trim()
        if (serverUrl.isEmpty()) {
            showStatus("请先输入服务器地址")
            return
        }
        ApiClient.setServerUrl(serverUrl)

        showStatus("正在上传本机数据...")
        syncBtn.isEnabled = false

        // 从本机 SQLite 读取所有记录，上传到远程服务器
        Thread {
            try {
                val db = EmbeddedDatabase(this)
                val localRecords = db.getAllRecords()
                val localUsers = db.getAllUsers()

                val usersArr = org.json.JSONArray()
                for (u in localUsers) {
                    val obj = org.json.JSONObject()
                    obj.put("id", u["id"] as? Int ?: 0)
                    obj.put("name", u["name"] as? String ?: "")
                    obj.put("theme_color", u["theme_color"] as? String ?: "#2196F3")
                    obj.put("role", u["role"] as? String ?: "operator")
                    usersArr.put(obj)
                }

                val recordsArr = org.json.JSONArray()
                for (r in localRecords) {
                    val obj = org.json.JSONObject()
                    for ((key, value) in r) {
                        @Suppress("UNCHECKED_CAST")
                        val v: Any? = value
                        when (v) {
                            is Int -> obj.put(key, v)
                            is Long -> obj.put(key, v)
                            is Double -> obj.put(key, v)
                            is Boolean -> obj.put(key, v)
                            else -> obj.put(key, v?.toString() ?: "")
                        }
                    }
                    recordsArr.put(obj)
                }

                val deviceId = getMyDeviceId()
                val userName = nameInput.text.toString().trim()
                ApiClient.mergeToServer(usersArr, recordsArr, deviceId, userName, object : ApiClient.ApiCallback {
                    override fun onSuccess(data: org.json.JSONObject?) {
                        val added = data?.optInt("added", 0) ?: 0
                        val skipped = data?.optInt("skipped", 0) ?: 0
                        runOnUiThread {
                            showStatus("✅ 上传完成（新增 $added，跳过 $skipped）")
                            syncBtn.isEnabled = true
                        }
                    }
                    override fun onError(error: String?) {
                        runOnUiThread {
                            showStatus("上传失败: $error")
                            syncBtn.isEnabled = true
                        }
                    }
                })
            } catch (e: Exception) {
                runOnUiThread {
                    showStatus("读取本机数据失败: ${e.message}")
                    syncBtn.isEnabled = true
                }
            }
        }.start()
    }

    /**
     * 从服务器拉取数据到本机
     */
    private fun pullFromServer() {
        val serverUrl = serverUrlInput.text.toString().trim()
        if (serverUrl.isEmpty()) {
            showStatus("请先输入服务器地址")
            return
        }
        ApiClient.setServerUrl(serverUrl)

        showStatus("正在拉取数据到本机...")
        syncBtn.isEnabled = false

        ApiClient.pullFromServer("all", null, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                try {
                    val records = data?.optJSONArray("records") ?: JSONArray()
                    val users = data?.optJSONArray("users") ?: JSONArray()

                    // 保存到本机 SQLite
                    Thread {
                        try {
                            val db = EmbeddedDatabase(this@LoginActivity)
                            var added = 0
                            var skipped = 0

                            // 先保存用户
                            for (i in 0 until users.length()) {
                                val u = users.getJSONObject(i)
                                val name = u.optString("name", "")
                                if (name.isNotEmpty()) {
                                    db.loginUser(name)
                                }
                            }

                            // 再保存记录
                            for (i in 0 until records.length()) {
                                val r = records.getJSONObject(i)
                                val barcode = r.optString("barcode", "")
                                if (barcode.isEmpty()) continue

                                val existing = db.findRecordByBarcode(barcode)
                                if (existing == null) {
                                    val userId = r.optInt("user_id", 0)
                                    val address = r.optString("address", "")
                                    val weight = r.optDouble("weight", 0.0)
                                    val note = r.optString("note", "")
                                    db.insertRecord(barcode, userId, address, weight, note)
                                    added++
                                } else {
                                    skipped++
                                }
                            }

                            val finalAdded = added
                            val finalSkipped = skipped
                            runOnUiThread {
                                showStatus("✅ 拉取完成（新增 $finalAdded，跳过 $finalSkipped）")
                                syncBtn.isEnabled = true
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                showStatus("保存到本机失败: ${e.message}")
                                syncBtn.isEnabled = true
                            }
                        }
                    }.start()
                } catch (e: Exception) {
                    runOnUiThread {
                        showStatus("解析数据失败: ${e.message}")
                        syncBtn.isEnabled = true
                    }
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    showStatus("拉取失败: $error")
                    syncBtn.isEnabled = true
                }
            }
        })
    }

    // ==================== 备份/恢复 ====================

    /**
     * 显示备份/恢复对话框
     */
    private fun showBackupDialog() {
        val options = arrayOf("💾 备份数据", "📂 恢复数据")
        AlertDialog.Builder(this)
            .setTitle("备份与恢复")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> performBackup()
                    1 -> showRestoreDialog()
                }
            }
            .show()
    }

    /**
     * 执行备份
     */
    private fun performBackup() {
        val serverUrl = serverUrlInput.text.toString().trim()
        if (serverUrl.isEmpty()) {
            showStatus("请先输入服务器地址")
            return
        }
        ApiClient.setServerUrl(serverUrl)

        showStatus("正在备份...")
        backupBtn.isEnabled = false

        ApiClient.backup(object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                runOnUiThread {
                    try {
                        // 保存到本地
                        val prefs = getSharedPreferences("barcode_prefs", MODE_PRIVATE)
                        prefs.edit().putString("backup_data", data.toString()).apply()
                        showStatus("✅ 备份成功，已保存到本地")
                    } catch (e: Exception) {
                        showStatus("保存备份失败: ${e.message}")
                    }
                    backupBtn.isEnabled = true
                }
            }

            override fun onError(error: String?) {
                runOnUiThread {
                    showStatus("备份失败: $error")
                    backupBtn.isEnabled = true
                }
            }
        })
    }

    /**
     * 显示恢复对话框
     */
    private fun showRestoreDialog() {
        val prefs = getSharedPreferences("barcode_prefs", MODE_PRIVATE)
        val backupData = prefs.getString("backup_data", null)
        if (backupData == null) {
            showStatus("没有找到本地备份数据")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("确认恢复")
            .setMessage("将从本地备份恢复数据，现有数据将被保留（重复条码跳过）")
            .setPositiveButton("确认恢复") { _, _ ->
                performRestore(backupData)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行恢复
     */
    private fun performRestore(backupData: String) {
        val serverUrl = serverUrlInput.text.toString().trim()
        if (serverUrl.isEmpty()) {
            showStatus("请先输入服务器地址")
            return
        }
        ApiClient.setServerUrl(serverUrl)

        showStatus("正在恢复...")
        backupBtn.isEnabled = false

        try {
            val data = JSONObject(backupData)
            ApiClient.restore(data, object : ApiClient.ApiCallback {
                override fun onSuccess(data: JSONObject?) {
                    runOnUiThread {
                        showStatus("✅ 恢复完成")
                        backupBtn.isEnabled = true
                    }
                }

                override fun onError(error: String?) {
                    runOnUiThread {
                        showStatus("恢复失败: $error")
                        backupBtn.isEnabled = true
                    }
                }
            })
        } catch (e: Exception) {
            showStatus("解析备份数据失败: ${e.message}")
            backupBtn.isEnabled = true
        }
    }

    // ==================== 二维码扫码 ====================

    /**
     * 启动二维码扫描器
     */
    private fun startQrCodeScanner() {
        val intent = Intent(this, QrCodeScannerActivity::class.java)
        qrCodeLauncher.launch(intent)
    }

    private val qrCodeLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedUrl = result.data?.getStringExtra("scanned_url")
            if (scannedUrl != null) {
                serverUrlInput.setText(scannedUrl)
                showStatus("✅ 已填入扫描到的服务器地址")
                Toast.makeText(this, "已填入服务器地址: $scannedUrl", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // ==================== 二维码生成（手机服务器） ====================

    /**
     * 生成手机服务器地址的二维码
     */
    private fun generateServerQrCode() {
        val ip = EmbeddedServer.getLocalIpAddress()
        if (ip.isEmpty()) {
            showStatus("无法获取IP地址")
            return
        }
        val serverUrl = "http://$ip:3000"
        val qrBitmap = QrCodeUtils.generateQrCode(serverUrl, 400)
        if (qrBitmap != null) {
            serverQrImage.setImageBitmap(qrBitmap)
            serverQrImage.visibility = View.VISIBLE
            serverQrImage.setOnClickListener {
                // 点击二维码复制地址
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("server_url", serverUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "服务器地址已复制: $serverUrl", Toast.LENGTH_SHORT).show()
            }
            showStatus("✅ 已生成二维码，其他设备扫码即可连接")
        } else {
            showStatus("二维码生成失败")
        }
    }

    // ==================== 登录/离线 ====================

    /**
     * 离线模式：不连服务器，直接进入扫码页
     */
    private fun enterOfflineMode() {

        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            showStatus("请输入姓名")
            return
        }

        // 保存用户名
        val prefs = getSharedPreferences("barcode_prefs", MODE_PRIVATE)
        prefs.edit().putString("user_name", name).apply()

        // 直接进入主菜单，userId=0 表示离线模式
        startActivity(Intent(this, com.barcodescanner.ui.menu.MainMenuActivity::class.java).apply {
            putExtra("user_id", 0)
            putExtra("user_name", name)
            putExtra("is_offline", true)
            putExtra("device_id", getMyDeviceId())
        })
        finish()
    }


    private fun attemptLogin() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val name = nameInput.text.toString().trim()

        if (TextUtils.isEmpty(serverUrl)) {
            showStatus("请输入服务器地址")
            return
        }
        if (TextUtils.isEmpty(name)) {
            showStatus("请输入您的姓名")
            return
        }

        // 去掉末尾斜杠
        val cleanUrl = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl

        // 保存
        val prefs = getSharedPreferences("barcode_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("server_url", cleanUrl)
            .putString("user_name", name)
            .apply()

        // 设置 API 地址
        ApiClient.setServerUrl(cleanUrl)

        loginBtn.isEnabled = false
        // 更新 CardView 内文字
        val textView = loginBtn.getChildAt(0)?.let {
            if (it is android.widget.LinearLayout) {
                it.getChildAt(1) as? TextView
            } else null
        }
        textView?.text = "连接中..."
        statusText.visibility = View.GONE

        ApiClient.login(name, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                try {
                    val userId = data!!.getInt("id")
                    val userName = data.getString("name")
                    val themeColor = data.getString("theme_color")

                    prefs.edit()
                        .putInt("user_id", userId)
                        .putString("user_name", userName)
                        .putString("theme_color", themeColor)
                        .apply()

                    startActivity(Intent(this@LoginActivity, com.barcodescanner.ui.menu.MainMenuActivity::class.java).apply {
                        putExtra("user_id", userId)
                        putExtra("user_name", userName)
                        putExtra("device_id", getMyDeviceId())
                    })
                    finish()
                } catch (e: Exception) {
                    showStatus("解析数据失败: ${e.message}")
                    resetButton()
                }
            }

            override fun onError(error: String?) {
                showStatus(error ?: "连接失败")
                resetButton()
            }
        })
    }

    private fun resetButton() {
        loginBtn.isEnabled = true
        val textView = loginBtn.getChildAt(0)?.let {
            if (it is android.widget.LinearLayout) {
                it.getChildAt(1) as? TextView
            } else null
        }
        textView?.text = "进入系统"
    }

    private fun showStatus(msg: String) {
        statusText.visibility = View.VISIBLE
        statusText.text = msg
    }
}
