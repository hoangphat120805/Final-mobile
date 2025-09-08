//package com.example.vaiche_driver.ui.item
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.bumptech.glide.Glide
//import com.example.vaiche_driver.R
//import com.example.vaiche_driver.model.CategoryPublic
//
//class ItemCategoryAdapter(
//    private val onClick: (CategoryPublic) -> Unit
//) : ListAdapter<CategoryPublic, ItemCategoryAdapter.VH>(Diff) {
//
//    object Diff : DiffUtil.ItemCallback<CategoryPublic>() {
//        override fun areItemsTheSame(o: CategoryPublic, n: CategoryPublic) = o.id == n.id
//        override fun areContentsTheSame(o: CategoryPublic, n: CategoryPublic) = o == n
//    }
//
//    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
//        private val iv: ImageView = v.findViewById(R.id.iv_icon)
//        private val name: TextView = v.findViewById(R.id.tv_name)
//        private val hint: TextView = v.findViewById(R.id.tv_unit_price_hint)
//
//        fun bind(item: CategoryPublic) {
//            name.text = item.name
//            val unit = item.unit.ifBlank { "unit" }
//            val price = item.estimatedPricePerUnit ?: 0.0
//            hint.text = "~${price.format()} / $unit"
//
//            Glide.with(iv)
//                .load(item.iconUrl)
//                .placeholder(R.drawable.ic_person_circle)
//                .into(iv)
//
//            itemView.setOnClickListener { onClick(item) }
//        }
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
//        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_scrap_category, parent, false))
//
//    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
//}
//
//private fun Double.format(): String =
//    if (this % 1.0 == 0.0) String.format("%.0f", this) else String.format("%.2f", this)
