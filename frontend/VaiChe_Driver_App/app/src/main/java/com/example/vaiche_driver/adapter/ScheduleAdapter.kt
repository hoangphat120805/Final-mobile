package com.example.vaiche_driver.adapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.model.OrderStatus
import com.example.vaiche_driver.model.Schedule
import com.example.vaiche_driver.model.ScheduleListItem

// Hằng số để định danh các loại view
private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_ITEM = 1

/**
 * Adapter này có khả năng hiển thị nhiều loại view khác nhau trong cùng một RecyclerView:
 * - Một tiêu đề (Header) cho mỗi section.
 * - Một card lịch trình (ScheduleItem) cho mỗi đơn hàng.
 */
class ScheduleAdapter(
    private val onItemClick: (Schedule) -> Unit
) : ListAdapter<ScheduleListItem, RecyclerView.ViewHolder>(ScheduleListDiffCallback()) {

    // --- ViewHolder cho Tiêu đề Section ---
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.tv_section_title)
        fun bind(item: ScheduleListItem.Header) {
            titleText.text = item.title
        }
    }

    // --- ViewHolder cho Card Lịch trình ---
    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.tv_schedule_date)
        private val timeText: TextView = view.findViewById(R.id.tv_schedule_time)
        private val orderIdText: TextView = view.findViewById(R.id.tv_order_id)
        private val statusText: TextView = view.findViewById(R.id.tv_status)
        private val startName: TextView = view.findViewById(R.id.tv_start_location_name)
        private val startAddress: TextView = view.findViewById(R.id.tv_start_location_address)
        private val endName: TextView = view.findViewById(R.id.tv_end_location_name)
        private val endAddress: TextView = view.findViewById(R.id.tv_end_location_address)

        fun bind(item: ScheduleListItem.ScheduleItem, onItemClick: (Schedule) -> Unit) {
            val schedule = item.schedule // Lấy đối tượng Schedule từ item

            dateText.text = schedule.date
            timeText.text = "${schedule.time},"
            orderIdText.text = "#${schedule.id.takeLast(4)}"
            startName.text = schedule.startLocationName
            startAddress.text = schedule.startLocationAddress
            endName.text = schedule.endLocationName
            endAddress.text = schedule.endLocationAddress

            statusText.visibility = View.VISIBLE
            when (schedule.status) {
                OrderStatus.scheduled -> {
                    statusText.text = "Scheduled"
                    statusText.background = ContextCompat.getDrawable(itemView.context, R.drawable.status_scheduled_background)
                    statusText.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_text_color))
                }
                OrderStatus.delivering -> {
                    statusText.text = "Delivering"
                    statusText.background = ContextCompat.getDrawable(itemView.context, R.drawable.status_delivering_background)
                    statusText.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_delivering_text))
                }
                OrderStatus.completed -> {
                    statusText.text = "Done"
                    statusText.background = ContextCompat.getDrawable(itemView.context, R.drawable.status_done_background)
                    statusText.setTextColor(ContextCompat.getColor(itemView.context, R.color.blue_text_color))
                }
                else -> {
                    statusText.visibility = View.GONE
                }
            }

            // Gán sự kiện click cho toàn bộ card
            itemView.setOnClickListener { onItemClick(schedule) }
        }
    }

    /**
     * Báo cho RecyclerView biết loại view cần sử dụng tại một vị trí cụ thể.
     */
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ScheduleListItem.Header -> VIEW_TYPE_HEADER
            is ScheduleListItem.ScheduleItem -> VIEW_TYPE_ITEM
        }
    }

    /**
     * Tạo ViewHolder tương ứng với viewType.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_section_header, parent, false)
            HeaderViewHolder(view)
        } else { // viewType == VIEW_TYPE_ITEM
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_schedule, parent, false)
            ScheduleViewHolder(view)
        }
    }

    /**
     * Gán dữ liệu cho ViewHolder tại một vị trí cụ thể.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ScheduleListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ScheduleListItem.ScheduleItem -> (holder as ScheduleViewHolder).bind(item, onItemClick)
        }
    }
}

/**
 * DiffUtil được cập nhật để có thể so sánh các item trong danh sách tổng hợp.
 */
class ScheduleListDiffCallback : DiffUtil.ItemCallback<ScheduleListItem>() {
    override fun areItemsTheSame(oldItem: ScheduleListItem, newItem: ScheduleListItem): Boolean {
        // So sánh Header với Header, ScheduleItem với ScheduleItem
        return if (oldItem is ScheduleListItem.ScheduleItem && newItem is ScheduleListItem.ScheduleItem) {
            oldItem.schedule.id == newItem.schedule.id
        } else if (oldItem is ScheduleListItem.Header && newItem is ScheduleListItem.Header) {
            oldItem.title == newItem.title
        } else {
            false // Các loại khác nhau không bao giờ là cùng một item
        }
    }

    override fun areContentsTheSame(oldItem: ScheduleListItem, newItem: ScheduleListItem): Boolean {
        // Data class tự động tạo hàm equals() so sánh, giúp việc so sánh nội dung trở nên đơn giản.
        return oldItem == newItem
    }
}