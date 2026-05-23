package com.barcodescanner.ui.records

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.barcodescanner.EmbeddedDatabase
import com.barcodescanner.R
import com.barcodescanner.model.RecordItem
import com.barcodescanner.network.ServerConfig
import com.barcodescanner.ui.theme.AppTheme

/**
 * 本机操作记录页面
 * 数据来源：本地 SQLite（EmbeddedDatabase）
 * 支持搜索、状态筛选、统计、查看详情
 */
class RecordsActivity : AppCompatActivity() {

    private var userId = 0
    private var userName = ""

    private lateinit var db: EmbeddedDatabase

    private lateinit var statsTotalCount: TextView
    private lateinit var statsTodayCount: TextView
    private lateinit var statsInCount: TextView
    private lateinit var statsSortCount: TextView
    private lateinit var statsShipCount: TextView
    private lateinit var statsSignCount: TextView
    private lateinit var statsExceptionCount: TextView
    private lateinit var recordsList: RecyclerView
    private lateinit var loadingText: TextView
    private lateinit var searchInput: EditText
    private lateinit var statusFilter: Spinner
    private lateinit var adapter: RecordsAdapter

    private var currentStatus: String? = null
    private var currentQuery: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchDelay: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        userId = intent.getIntExtra("user_id", ServerConfig.getUserId())
        userName = intent.getStringExtra("user_name") ?: ServerConfig.getUserName()

        db = EmbeddedDatabase(this)

        statsTotalCount = findViewById(R.id.statsTotalCount)
        statsTodayCount = findViewById(R.id.statsTodayCount)
        statsInCount = findViewById(R.id.statsInCount)
        statsSortCount = findViewById(R.id.statsSortCount)
        statsShipCount = findViewById(R.id.statsShipCount)
        statsSignCount = findViewById(R.id.statsSignCount)
        statsExceptionCount = findViewById(R.id.statsExceptionCount)
        recordsList = findViewById(R.id.recordsList)
        loadingText = findViewById(R.id.loadingText)
        searchInput = findViewById(R.id.searchInput)
        statusFilter = findViewById(R.id.statusFilter)

        // 返回
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        // 导出（打开浏览器访问服务器导出）
        findViewById<View>(R.id.exportBtn).setOnClickListener { exportData() }

        // 列表
        adapter = RecordsAdapter { item -> showRecordDetail(item) }
        recordsList.layoutManager = LinearLayoutManager(this)
        recordsList.adapter = adapter

        // 状态筛选
        val statusOptions = arrayOf("全部", "入库", "分拣", "出库", "签收", "异常")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        statusFilter.adapter = spinnerAdapter
        statusFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentStatus = if (position == 0) null else statusOptions[position]
                loadRecords()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 搜索输入（带防抖）
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim() ?: ""
                // 防抖：用户停止输入 500ms 后搜索
                searchDelay?.let { mainHandler.removeCallbacks(it) }
                searchDelay = Runnable { loadRecords() }
                mainHandler.postDelayed(searchDelay!!, 500)
            }
        })

        // 加载数据
        loadStats()
        loadRecords()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面刷新数据（可能在其他页面有操作）
        loadStats()
        loadRecords()
    }

    // ==================== 统计 ====================

    private fun loadStats() {
        try {
            val stats = db.getStats(userId)
            statsTotalCount.text = (stats["total"] as? Int ?: 0).toString()
            statsTodayCount.text = (stats["today"] as? Int ?: 0).toString()
            statsInCount.text = (stats["in_count"] as? Int ?: 0).toString()
            statsSortCount.text = (stats["sort_count"] as? Int ?: 0).toString()
            statsShipCount.text = (stats["ship_count"] as? Int ?: 0).toString()
            statsSignCount.text = (stats["sign_count"] as? Int ?: 0).toString()
            statsExceptionCount.text = (stats["duplicate_count"] as? Int ?: 0).toString()
        } catch (e: Exception) {
            // 静默处理
        }
    }

    // ==================== 记录列表 ====================

    private fun loadRecords() {
        loadingText.visibility = View.VISIBLE
        loadingText.text = "加载中..."

        try {
            val result: Map<String, Any?>

            if (currentQuery.isNotEmpty()) {
                // 搜索模式 - 从本地数据库搜索
                result = db.searchRecords(currentQuery, currentStatus, 1, 200)
            } else {
                // 普通查询 - 从本地数据库获取
                result = db.getRecords(userId, 1, 200, currentStatus)
            }

            loadingText.visibility = View.GONE

            @Suppress("UNCHECKED_CAST")
            val records = result["records"] as? List<Map<String, Any?>> ?: emptyList()
            val items = parseRecords(records)
            adapter.setItems(items)

            if (items.isEmpty()) {
                loadingText.visibility = View.VISIBLE
                loadingText.text = "暂无记录"
            }
        } catch (e: Exception) {
            loadingText.visibility = View.VISIBLE
            loadingText.text = "加载失败: ${e.message}"
        }
    }

    private fun parseRecords(records: List<Map<String, Any?>>): List<RecordItem> {
        return records.map { r ->
            RecordItem(
                id = (r["id"] as? Number)?.toInt() ?: 0,
                barcode = r["barcode"] as? String ?: "",
                status = r["status"] as? String ?: "入库",
                address = r["address"] as? String ?: "",
                weight = (r["weight"] as? Number)?.toDouble() ?: 0.0,
                deviceId = r["device_id"] as? String ?: "",
                note = r["note"] as? String ?: "",
                recipient = r["recipient"] as? String ?: "",
                logisticsNo = r["logistics_no"] as? String ?: "",
                signer = r["signer"] as? String ?: "",
                exceptionType = r["exception_type"] as? String ?: "",
                createdAt = r["created_at"] as? String ?: "",
                sortAt = r["sort_at"] as? String ?: "",
                userName = r["user_name"] as? String ?: "",
                themeColor = r["theme_color"] as? String ?: ""
            )
        }
    }

    // ==================== 记录详情弹窗 ====================

    private fun showRecordDetail(item: RecordItem) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("📋 包裹详情")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        fun addDetailRow(label: String, value: String) {
            if (value.isNotEmpty()) {
                layout.addView(TextView(this).apply {
                    text = "$label: $value"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setPadding(0, 6, 0, 6)
                })
            }
        }

        addDetailRow("条码", item.barcode)
        addDetailRow("状态", item.status)
        addDetailRow("地址", item.address)
        addDetailRow("重量", if (item.weight > 0) "${item.weight}kg" else "")
        addDetailRow("设备", item.deviceId)
        addDetailRow("备注", item.note)
        addDetailRow("收件人", item.recipient)
        addDetailRow("物流单号", item.logisticsNo)
        addDetailRow("签收人", item.signer)
        addDetailRow("异常类型", item.exceptionType)
        addDetailRow("操作人", item.userName)

        // 时间
        var timeStr = item.createdAt
        if (timeStr.length >= 19) {
            timeStr = timeStr.substring(0, 19).replace("T", " ")
        }
        addDetailRow("创建时间", timeStr)

        if (item.sortAt.isNotEmpty()) {
            var sortStr = item.sortAt
            if (sortStr.length >= 19) {
                sortStr = sortStr.substring(0, 19).replace("T", " ")
            }
            addDetailRow("分拣时间", sortStr)
        }

        builder.setView(layout)
        builder.setPositiveButton("关闭", null)
        builder.show()
    }

    // ==================== 导出 ====================

    private fun exportData() {
        val serverUrl = ServerConfig.getServerUrl()
        if (serverUrl.isNotEmpty()) {
            val exportUrl = "$serverUrl/api/export?user_id=$userId"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(exportUrl))
            startActivity(intent)
        } else {
            android.widget.Toast.makeText(this, "未设置服务器地址，无法导出", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
