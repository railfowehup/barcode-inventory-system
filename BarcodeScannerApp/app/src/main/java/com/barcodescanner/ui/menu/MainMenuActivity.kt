package com.barcodescanner.ui.menu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.barcodescanner.EmbeddedDatabase
import com.barcodescanner.EmbeddedServer
import com.barcodescanner.LoginActivity
import com.barcodescanner.R
import com.barcodescanner.RecordsActivity
import com.barcodescanner.ApiClient
import com.barcodescanner.network.ServerConfig
import com.barcodescanner.ui.changeaddress.ChangeAddressActivity
import com.barcodescanner.ui.inbound.InboundActivity
import com.barcodescanner.ui.sign.SignActivity
import com.barcodescanner.ui.sort.SortActivity
import com.barcodescanner.ui.ship.ShipActivity
import com.barcodescanner.utils.NetworkUtils
import org.json.JSONObject

/**
 * 主菜单页面 - 功能选择入口
 * 替代原来的 ScannerActivity 中的操作选择对话框
 */
class MainMenuActivity : AppCompatActivity() {

    private lateinit var userNameDisplay: TextView
    private lateinit var offlineBadge: TextView
    private lateinit var cardInbound: CardView
    private lateinit var cardSort: CardView
    private lateinit var cardShip: CardView
    private lateinit var cardSign: CardView
    private lateinit var cardChangeAddress: CardView
    private lateinit var cardRecords: CardView
    private lateinit var btnSync: TextView
    private lateinit var btnBackup: TextView
    private lateinit var btnLogout: TextView

    private var userId = 0
    private var userName = ""
    private var isOffline = false
    private var deviceId = ""

    // ==================== 心跳上报 ====================
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatInterval = 60000L
    private var isHeartbeatRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        userId = intent.getIntExtra("user_id", ServerConfig.getUserId())
        userName = intent.getStringExtra("user_name") ?: ServerConfig.getUserName()
        isOffline = intent.getBooleanExtra("is_offline", false)
        deviceId = intent.getStringExtra("device_id") ?: ServerConfig.getDeviceId()

        initViews()
        setupClickListeners()

        // 启动心跳（非离线模式）
        if (!isOffline && userId > 0) {
            startHeartbeat()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartbeat()
    }

    // ==================== 心跳上报 ====================

    private fun startHeartbeat() {
        isHeartbeatRunning = true
        doHeartbeat()
    }

    private fun doHeartbeat() {
        if (!isHeartbeatRunning) return

        val ipAddress = NetworkUtils.getLocalIpAddress()
        val deviceName = NetworkUtils.getDeviceName()

        ApiClient.sendHeartbeat(deviceId, deviceName, ipAddress, userName, object : ApiClient.ApiCallback {
            override fun onSuccess(data: org.json.JSONObject?) {}
            override fun onError(error: String?) {}
        })

        heartbeatHandler.postDelayed({ doHeartbeat() }, heartbeatInterval)
    }

    private fun stopHeartbeat() {
        isHeartbeatRunning = false
        heartbeatHandler.removeCallbacksAndMessages(null)
    }

    // ==================== UI 初始化 ====================

    private fun initViews() {
        userNameDisplay = findViewById(R.id.userNameDisplay)
        offlineBadge = findViewById(R.id.offlineBadge)
        cardInbound = findViewById(R.id.cardInbound)
        cardSort = findViewById(R.id.cardSort)
        cardShip = findViewById(R.id.cardShip)
        cardSign = findViewById(R.id.cardSign)
        cardChangeAddress = findViewById(R.id.cardChangeAddress)
        cardRecords = findViewById(R.id.cardRecords)
        btnSync = findViewById(R.id.btnSync)
        btnBackup = findViewById(R.id.btnBackup)
        btnLogout = findViewById(R.id.btnLogout)

        userNameDisplay.text = "👋 你好，$userName"

        if (isOffline) {
            offlineBadge.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        cardInbound.setOnClickListener {
            startActivity(Intent(this, InboundActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("is_offline", isOffline)
                putExtra("device_id", deviceId)
            })
        }

        cardSort.setOnClickListener {
            if (isOffline) {
                Toast.makeText(this, "分拣需要联网", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, SortActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("device_id", deviceId)
            })
        }

        cardShip.setOnClickListener {
            if (isOffline) {
                Toast.makeText(this, "出库需要联网", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ShipActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("device_id", deviceId)
            })
        }

        cardSign.setOnClickListener {
            if (isOffline) {
                Toast.makeText(this, "签收需要联网", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, SignActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("device_id", deviceId)
            })
        }

        cardChangeAddress.setOnClickListener {
            if (isOffline) {
                Toast.makeText(this, "改地址需要联网", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ChangeAddressActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("device_id", deviceId)
            })
        }

        cardRecords.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("theme_color", ServerConfig.getThemeColor())
            })
        }

        btnSync.setOnClickListener { showSyncDialog() }
        btnBackup.setOnClickListener { showBackupDialog() }
        btnLogout.setOnClickListener {
            // 通知服务器设备已离线
            ApiClient.sendLogout(deviceId, object : ApiClient.ApiCallback {
                override fun onSuccess(data: org.json.JSONObject?) {}
                override fun onError(error: String?) {}
            })
            ServerConfig.clearAll()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ==================== 同步 ====================

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

    private fun syncToServer() {
        ApiClient.setServerUrl(ServerConfig.getServerUrl())
        Toast.makeText(this, "正在上传本机数据...", Toast.LENGTH_SHORT).show()

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

                ApiClient.mergeToServer(usersArr, recordsArr, deviceId, userName, object : ApiClient.ApiCallback {
                    override fun onSuccess(data: org.json.JSONObject?) {
                        val added = data?.optInt("added", 0) ?: 0
                        val skipped = data?.optInt("skipped", 0) ?: 0
                        runOnUiThread {
                            Toast.makeText(this@MainMenuActivity, "✅ 上传完成（新增 $added，跳过 $skipped）", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onError(error: String?) {
                        runOnUiThread { Toast.makeText(this@MainMenuActivity, "上传失败: $error", Toast.LENGTH_SHORT).show() }
                    }
                })
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainMenuActivity, "读取本机数据失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun pullFromServer() {
        ApiClient.setServerUrl(ServerConfig.getServerUrl())
        Toast.makeText(this, "正在拉取数据到本机...", Toast.LENGTH_SHORT).show()

        ApiClient.pullFromServer("all", null, object : ApiClient.ApiCallback {
            override fun onSuccess(data: org.json.JSONObject?) {
                try {
                    val records = data?.optJSONArray("records") ?: org.json.JSONArray()
                    val users = data?.optJSONArray("users") ?: org.json.JSONArray()

                    // 保存到本机 SQLite
                    Thread {
                        try {
                            val db = EmbeddedDatabase(this@MainMenuActivity)
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
                                Toast.makeText(this@MainMenuActivity, "✅ 拉取完成（新增 $finalAdded，跳过 $finalSkipped）", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this@MainMenuActivity, "保存到本机失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }.start()
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainMenuActivity, "解析数据失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(error: String?) {
                runOnUiThread { Toast.makeText(this@MainMenuActivity, "拉取失败: $error", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    // ==================== 备份 ====================

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

    private fun performBackup() {
        ApiClient.setServerUrl(ServerConfig.getServerUrl())
        Toast.makeText(this, "正在备份...", Toast.LENGTH_SHORT).show()

        ApiClient.backup(object : ApiClient.ApiCallback {
            override fun onSuccess(data: org.json.JSONObject?) {
                try {
                    ServerConfig.saveBackupData(data.toString())
                    runOnUiThread { Toast.makeText(this@MainMenuActivity, "✅ 备份成功", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainMenuActivity, "保存备份失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(error: String?) {
                runOnUiThread { Toast.makeText(this@MainMenuActivity, "备份失败: $error", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun showRestoreDialog() {
        val backupData = ServerConfig.getBackupData()
        if (backupData == null) {
            Toast.makeText(this, "没有找到本地备份数据", Toast.LENGTH_SHORT).show()
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

    private fun performRestore(backupData: String) {
        ApiClient.setServerUrl(ServerConfig.getServerUrl())
        Toast.makeText(this, "正在恢复...", Toast.LENGTH_SHORT).show()

        try {
            val data = org.json.JSONObject(backupData)
            ApiClient.restore(data, object : ApiClient.ApiCallback {
                override fun onSuccess(data: org.json.JSONObject?) {
                    runOnUiThread { Toast.makeText(this@MainMenuActivity, "✅ 恢复完成", Toast.LENGTH_SHORT).show() }
                }
                override fun onError(error: String?) {
                    runOnUiThread { Toast.makeText(this@MainMenuActivity, "恢复失败: $error", Toast.LENGTH_SHORT).show() }
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "解析备份数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
