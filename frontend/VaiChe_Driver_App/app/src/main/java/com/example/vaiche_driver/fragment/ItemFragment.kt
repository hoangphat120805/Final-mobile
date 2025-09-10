package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vaiche_driver.R
import com.example.vaiche_driver.adapter.CategoryAdapter
import com.example.vaiche_driver.fragment.DetailItemFragment
import com.example.vaiche_driver.viewmodel.ItemViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

class ItemFragment : Fragment() {

    companion object {
        private const val ARG_ORDER_ID = "order_id"
        fun newInstance(orderId: String) = ItemFragment().apply {
            arguments = bundleOf(ARG_ORDER_ID to orderId)
        }
    }

    private val viewModel: ItemViewModel by viewModels()
    private lateinit var adapter: CategoryAdapter
    private var orderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orderId = requireArguments().getString(ARG_ORDER_ID).orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_item, container, false) // tên file đúng theo bạn
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)
        val rv = view.findViewById<RecyclerView>(R.id.rv_categories)
        val etSearch = view.findViewById<EditText>(R.id.et_search)

        adapter = CategoryAdapter { category ->
            // Điều hướng sang DetailItemFragment
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    DetailItemFragment.newInstance(orderId = orderId, categoryId = category.id)
                )
                .addToBackStack(null)
                .commit()
        }
        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        rv.adapter = adapter

        // Observers
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.filtered.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let { msg ->
                Snackbar.make(view, msg, Snackbar.LENGTH_LONG).show()
            }
        }

        // Search
        etSearch.setOnEditorActionListener { v, _, _ ->
            viewModel.filter(v.text.toString())
            true
        }
        // Optional: lọc realtime
        // etSearch.addTextChangedListener { s -> viewModel.filter(s?.toString().orEmpty()) }

        // Load
        viewModel.load()
    }
}
