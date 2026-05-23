package com.barcodescanner.ui.menu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.barcodescanner.EmbeddedServer
import com.barcodescanner.LoginActivity
import com.barcodescanner.R
import com.barcodescanner.RecordsActivity
import com.barcodescanner.network.ApiClient
import com.barcodescanner.network.ServerConfig
import com.barcodescanner.ui.changeaddress.ChangeAddressActivity
import com.barcodescanner.ui.inbound.InboundActivity
import com.barcodescanner.ui.sign.SignActivity
import com.barcodescanner.ui.sort.SortActivity
import com.barcodescanner.ui.ship.ShipActivity
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
    private lateinit var btnStartServer: TextView
    private lateinit var btnSync: TextView
    private lateinit var btnBackup: TextView
    private lateinit var btnLogout: TextView

    private var userId = 0
    private var userName = ""
    private var isOffline = false
    private var deviceId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        userId = intent.getIntExtra("user_id", ServerConfig.getUserId())
        userName = intent.getStringExtra("user_name") ?: ServerConfig.getUserName()
        isOffline = intent.getBooleanExtra("is_offline", false)
        deviceId = intent.getStringExtra("device_id") ?: ServerConfig.getDeviceId()

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        userNameDisplay = findViewById(R.id.userNameDisplay)
        offlineBadge = findViewById(R.id.offlineBadge)
        cardInbound = findViewById(R.id.cardInbound)
        cardSort = findViewById(R.id.cardSort)
        cardShip = findViewById(R.id.cardShip)
        cardSign = findViewById(R.id.cardSign)
        cardChangeAddress = findViewById(R.id.cardChangeAddress)
        cardRecords = findViewById(R.id.cardRecords)
        btnStartServer = findViewById(R.id.btnStartServer)
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

        btnStartServer.setOnClickListener { startServer() }
        btnSync.setOnClickListener { showSyncDialog() }
        btnBackup.setOnClickListener { showBackupDialog() }
        btnLogout.setOnClickListener {
            ServerConfig.clearAll()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ==================== 服务器 ====================

    private fun startServer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
                return
            }
        }

        val intent = Intent(this, EmbeddedServer::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        btnStartServer.text = "⏳ 启动中..."
        btnStartServer.isEnabled = false

        android.os.Handler(mainLooper).postDelayed({
            if (EmbeddedServer.isServerRunning()) {
                btnStartServer.text = "✅ 服务器运行中"
            } else {
                btnStartServer.text = "📡 服务器"
                btnStartServer.isEnabled = true
                Toast.makeText(this, "服务器启动失败", Toast.LENGTH_SHORT).show()
            }
        }, 2000)
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
        val serverUrl = ServerConfig.getServerUrl()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先在登录页设置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient.setServerUrl(serverUrl)

        if (!EmbeddedServer.isServerRunning()) {
            Toast.makeText(this, "请先启动本机服务器", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在上传数据...", Toast.LENGTH_SHORT).show()

        ApiClient.pullFromServer("all", null, object : ApiClient.ApiCallback {
            override fun onSuccess(data: org.json.JSONObject?) {
                try {
                    val users = data?.optJSONArray("users") ?: org.json.JSONArray()
                    val records = data?.optJSONArray("records") ?: org.json.JSONArray()
                    ApiClient.mergeToServer(users, records, deviceId, userName, object : ApiClient.ApiCallback {
                        override fun onSuccess(data: org.json.JSONObject?) {
                            runOnUiThread { Toast.makeText(this@MainMenuActivity, "✅ 同步完成", Toast.LENGTH_SHORT).show() }
                        }
                        override fun onError(error: String?) {
                            runOnUiThread { Toast.makeText(this@MainMenuActivity, "同步失败: $error", Toast.LENGTH_SHORT).show() }
                        }
                    })
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainMenuActivity, "解析失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onError(error: String?) {
                runOnUiThread { Toast.makeText(this@MainMenuActivity, "拉取失败: $error", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun pullFromServer() {
        val serverUrl = ServerConfig.getServerUrl()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先在登录页设置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient.setServerUrl(serverUrl)

        Toast.makeText(this, "正在拉取数据...", Toast.LENGTH_SHORT).show()

        ApiClient.pullFromServer("all", null, object : ApiClient.ApiCallback {
            override fun onSuccess(data: org.json.JSONObject?) {
                val total = data?.optInt("total_records", 0) ?: 0
                runOnUiThread { Toast.makeText(this@MainMenuActivity, "✅ 拉取完成，共 $total 条记录", Toast.LENGTH_SHORT).show() }
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
        val serverUrl = ServerConfig.getServerUrl()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先设置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient.setServerUrl(serverUrl)

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
        val serverUrl = ServerConfig.getServerUrl()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先设置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        ApiClient.setServerUrl(serverUrl)

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
