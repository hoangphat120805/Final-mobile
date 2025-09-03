//package com.example.vaiche_driver.adapter
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//import com.example.vaiche_driver.R
//import com.mapbox.search.result.SearchResult
//
//class SearchAdapter(
//    private val results: MutableList<SearchResult>,
//    private val onClick: (SearchResult) -> Unit
//) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {
//
//    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        val name: TextView = view.findViewById(R.id.tv_result_name)
//        val address: TextView = view.findViewById(R.id.tv_result_address)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.list_item_search_result, parent, false)
//        return ViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        val result = results[position]
//        holder.name.text = result.name
//        holder.address.text = result.address?.formattedAddress() ?: "Details not available"
//        holder.itemView.setOnClickListener { onClick(result) }
//    }
//
//    override fun getItemCount() = results.size
//
//    fun updateData(newResults: List<SearchResult>) {
//        results.clear()
//        results.addAll(newResults)
//        notifyDataSetChanged()
//    }
//}