//package com.example.vaiche_driver.ui.item
//
//import android.os.Bundle
//import android.view.View
//import android.widget.EditText
//import android.widget.ProgressBar
//import android.widget.TextView
//import androidx.core.widget.addTextChangedListener
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import androidx.navigation.fragment.findNavController
//import androidx.recyclerview.widget.GridLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.example.vaiche_driver.R
//import com.example.vaiche_driver.model.CategoryPublic
//import com.example.vaiche_driver.viewmodel.ItemViewModel
//import dagger.hilt.android.AndroidEntryPoint
//
//@AndroidEntryPoint
//class ItemFragment : Fragment(R.layout.fragment_item) {
//
//    private val vm: ItemViewModel by viewModels()
//    private lateinit var adapter: ItemCategoryAdapter
//
//    // Expecting args: orderId (String)
//    private val orderId: String by lazy {
//        requireArguments().getString(ARG_ORDER_ID) ?: error("orderId required")
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        val rv = view.findViewById<RecyclerView>(R.id.rv_categories)
//        val progress = view.findViewById<ProgressBar>(R.id.progress)
//        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)
//        val etSearch = view.findViewById<EditText>(R.id.et_search)
//
//        adapter = ItemCategoryAdapter { cat ->
//            navigateToDetailAdd(orderId, cat)
//        }
//
//        rv.layoutManager = GridLayoutManager(requireContext(), 2)
//        rv.adapter = adapter
//
//        etSearch.addTextChangedListener { vm.setQuery(it?.toString().orEmpty()) }
//
//        // Observe state (simple collectLatest using viewLifecycleOwner.lifecycleScope is fine)
//        viewLifecycleOwner.lifecycle.addObserver(FlowCollectorLifecycleOwner {
//            vm.ui.collect { state ->
//                progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
//                val list = vm.filtered()
//                adapter.submitList(list)
//                tvEmpty.visibility = if (!state.isLoading && list.isEmpty()) View.VISIBLE else View.GONE
//            }
//        })
//    }
//
//    private fun navigateToDetailAdd(orderId: String, cat: CategoryPublic) {
//        val action = ItemFragmentDirections.actionItemToDetailItem(
//            mode = DetailMode.ADD.name,
//            orderId = orderId,
//            // for ADD
//            categoryId = cat.id,
//            categoryName = cat.name,
//            unit = cat.unit,
//            estimatedPrice = cat.estimatedPricePerUnit ?: 0.0,
//            // for EDIT (not used)
//            orderItemId = null,
//            currentQty = 0.0
//        )
//        findNavController().navigate(action)
//    }
//
//    companion object {
//        const val ARG_ORDER_ID = "orderId"
//    }
//}
//
///**
// * Helper: lifecycle-aware collector without repeating boilerplate.
// * You can replace by repeatOnLifecycle if you want.
// */
//import androidx.lifecycle.DefaultLifecycleObserver
//import androidx.lifecycle.LifecycleOwner
//import androidx.lifecycle.lifecycleScope
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.launch
//
//class FlowCollectorLifecycleOwner(
//    private val block: suspend () -> Unit
//) : DefaultLifecycleObserver {
//    private var job: Job? = null
//    override fun onStart(owner: LifecycleOwner) {
//        job = owner.lifecycleScope.launch { block() }
//    }
//    override fun onStop(owner: LifecycleOwner) {
//        job?.cancel()
//        job = null
//    }
//}
