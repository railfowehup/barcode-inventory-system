package com.barcodescanner.ui.records

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.barcodescanner.R
import com.barcodescanner.model.RecordItem

/**
 * 本机记录列表适配器 - 卡片风格
 */
class RecordsAdapter(
    private val onItemClick: ((RecordItem) -> Unit)? = null
) : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {

    private var items = listOf<RecordItem>()

    fun setItems(items: List<RecordItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvBarcode: TextView = view.findViewById(R.id.tvBarcode)
        private val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        private val tvWeight: TextView = view.findViewById(R.id.tvWeight)
        private val tvExtra: TextView = view.findViewById(R.id.tvExtra)
        private val cardView: CardView = view as CardView

        fun bind(item: RecordItem) {
            // 条码
            tvBarcode.text = item.barcode

            // 状态标签和颜色
            val (statusTag, statusColor) = when (item.status) {
                "入库" -> "📦 入库" to "#4CAF50"
                "分拣" -> "📤 分拣" to "#FF9800"
                "出库" -> "🚚 出库" to "#2196F3"
                "签收" -> "✅ 签收" to "#9C27B0"
                "异常" -> "⚠️ 异常" to "#F44336"
                else -> "📦 ${item.status}" to "#607D8B"
            }
            tvStatus.text = statusTag
            tvStatus.setTextColor(Color.parseColor(statusColor))

            // 时间
            var timeStr = item.createdAt
            if (timeStr.length >= 19) {
                timeStr = timeStr.substring(0, 19).replace("T", " ")
            }
            tvTime.text = timeStr

            // 地址
            tvAddress.text = if (item.address.isNotEmpty()) "📍 ${item.address}" else ""

            // 重量
            tvWeight.text = if (item.weight > 0) "${item.weight}kg" else ""

            // 额外信息（操作人/设备/备注等）
            val extraParts = mutableListOf<String>()
            if (item.userName.isNotEmpty()) {
                extraParts.add("操作:${item.userName}")
            }
            if (item.deviceId.isNotEmpty()) {
                val shortId = if (item.deviceId.length > 8) item.deviceId.substring(0, 8) else item.deviceId
                extraParts.add("设备:${shortId}")
            }
            if (item.recipient.isNotEmpty()) {
                extraParts.add("收件:${item.recipient}")
            }
            if (item.signer.isNotEmpty()) {
                extraParts.add("签收:${item.signer}")
            }
            if (item.exceptionType.isNotEmpty()) {
                extraParts.add("异常:${item.exceptionType}")
            }
            if (item.note.isNotEmpty()) {
                extraParts.add("备注:${item.note}")
            }
            tvExtra.text = extraParts.joinToString(" · ")

            // 点击事件
            cardView.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }
}
