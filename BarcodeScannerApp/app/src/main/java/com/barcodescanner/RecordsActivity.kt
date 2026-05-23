package com.barcodescanner

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.barcodescanner.model.RecordItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 入库记录 + 统计页面
 * 支持搜索、状态筛选、更多统计
 */
class RecordsActivity : AppCompatActivity() {

    private var userId = 0
    private var themeColor = "#2196F3"
    private var userName = ""

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        userId = intent.getIntExtra("user_id", 0)
        themeColor = intent.getStringExtra("theme_color") ?: "#2196F3"
        userName = intent.getStringExtra("user_name") ?: ""

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

        // 导出
        findViewById<View>(R.id.exportBtn).setOnClickListener { exportData() }

        // 主题色
        val color = Color.parseColor(themeColor)
        findViewById<View>(R.id.exportBtn).backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)

        // 列表
        adapter = RecordsAdapter()
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

        // 搜索输入
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim() ?: ""
                loadRecords()
            }
        })

        // 加载数据
        loadStats()
        loadRecords()
    }

    private fun loadStats() {
        ApiClient.getStats(userId, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {
                try {
                    statsTotalCount.text = data!!.getInt("total").toString()
                    statsTodayCount.text = data.getInt("today").toString()
                    statsInCount.text = data.getInt("in_count").toString()
                    statsSortCount.text = data.getInt("sort_count").toString()
                    statsShipCount.text = data.optInt("ship_count", 0).toString()
                    statsSignCount.text = data.optInt("sign_count", 0).toString()
                    statsExceptionCount.text = data.optInt("duplicate_count", 0).toString()
                } catch (_: Exception) {}
            }

            override fun onError(error: String?) {}
        })
    }

    private fun loadRecords() {
        loadingText.visibility = View.VISIBLE
        loadingText.text = "加载中..."

        if (currentQuery.isNotEmpty()) {
            // 搜索模式
            ApiClient.searchRecords(currentQuery, currentStatus, 1, object : ApiClient.ApiCallback {
                override fun onSuccess(data: JSONObject?) {
                    loadingText.visibility = View.GONE
                    try {
                        val records = data!!.getJSONArray("records")
                        val items = parseRecords(records)
                        adapter.setItems(items)
                    } catch (e: Exception) {
                        loadingText.text = "解析失败"
                    }
                }

                override fun onError(error: String?) {
                    loadingText.visibility = View.VISIBLE
                    loadingText.text = "加载失败: $error"
                }
            })
        } else {
            // 普通查询（可筛状态）
            ApiClient.getRecords(userId, 1, currentStatus, object : ApiClient.ApiCallback {
                override fun onSuccess(data: JSONObject?) {
                    loadingText.visibility = View.GONE
                    try {
                        val records = data!!.getJSONArray("records")
                        val items = parseRecords(records)
                        adapter.setItems(items)
                    } catch (e: Exception) {
                        loadingText.text = "解析失败"
                    }
                }

                override fun onError(error: String?) {
                    loadingText.visibility = View.VISIBLE
                    loadingText.text = "加载失败: $error"
                }
            })
        }
    }

    private fun parseRecords(records: JSONArray): List<RecordItem> {
        val items = mutableListOf<RecordItem>()
        for (i in 0 until records.length()) {
            val r = records.getJSONObject(i)
            items.add(RecordItem(
                id = r.optInt("id", 0),
                barcode = r.getString("barcode"),
                status = r.optString("status", "入库"),
                address = r.optString("address", ""),
                weight = r.optDouble("weight", 0.0),
                deviceId = r.optString("device_id", ""),
                note = r.optString("note", ""),
                recipient = r.optString("recipient", ""),
                logisticsNo = r.optString("logistics_no", ""),
                signer = r.optString("signer", ""),
                exceptionType = r.optString("exception_type", ""),
                createdAt = r.getString("created_at"),
                sortAt = r.optString("sort_at", "")
            ))
        }
        return items
    }

    private fun exportData() {
        val exportUrl = "${ApiClient.getServerUrl()}/api/export?user_id=$userId"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(exportUrl)))
    }

    // ==================== 适配器 ====================

    inner class RecordsAdapter : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {

        private var items = listOf<RecordItem>()

        fun setItems(items: List<RecordItem>) {
            this.items = items
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            // 状态标签和颜色
            val (statusTag, statusColor) = when (item.status) {
                "入库" -> "📦 入库" to "#4CAF50"
                "分拣" -> "📤 分拣" to "#FF9800"
                "出库" -> "🚚 出库" to "#2196F3"
                "签收" -> "✅ 签收" to "#9C27B0"
                "异常" -> "⚠️ 异常" to "#F44336"
                else -> "📦 ${item.status}" to "#607D8B"
            }

            holder.text1.text = "${item.barcode}  [${statusTag}]"
            holder.text1.setTextColor(Color.parseColor(statusColor))

            // 第二行：详细信息
            val sb = StringBuilder()
            var timeStr = item.createdAt
            if (timeStr.length >= 19) {
                timeStr = timeStr.substring(0, 19).replace("T", " ")
            }
            sb.append(timeStr)

            if (item.address.isNotEmpty()) {
                sb.append(" · ${item.address}")
            }
            if (item.weight > 0) {
                sb.append(" · ${item.weight}kg")
            }
            if (item.deviceId.isNotEmpty()) {
                val shortId = if (item.deviceId.length > 8) item.deviceId.substring(0, 8) else item.deviceId
                sb.append(" · 分拣:${shortId}")
            }
            if (item.recipient.isNotEmpty()) {
                sb.append(" · 收件:${item.recipient}")
            }
            if (item.signer.isNotEmpty()) {
                sb.append(" · 签收:${item.signer}")
            }
            if (item.exceptionType.isNotEmpty()) {
                sb.append(" · 异常:${item.exceptionType}")
            }
            if (item.note.isNotEmpty()) {
                sb.append(" · ${item.note}")
            }

            holder.text2.text = sb.toString()
            holder.text2.setTextColor(Color.GRAY)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text1: TextView = view.findViewById(android.R.id.text1)
            val text2: TextView = view.findViewById(android.R.id.text2)

            init {
                text1.textSize = 15f
            }
        }
    }
}
