//package com.example.vaiche_driver.viewmodel
//
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.vaiche_driver.data.repository.OrderRepository
//import com.example.vaiche_driver.model.CategoryPublic
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.update
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//data class ItemUiState(
//    val isLoading: Boolean = false,
//    val items: List<CategoryPublic> = emptyList(),
//    val query: String = "",
//    val error: String? = null
//)
//
//@HiltViewModel
//class ItemViewModel @Inject constructor(
//    private val repo: OrderRepository
//) : ViewModel() {
//
//    private val _ui = MutableStateFlow(ItemUiState(isLoading = true))
//    val ui: StateFlow<ItemUiState> = _ui
//
//    init { refresh() }
//
//    fun refresh() {
//        viewModelScope.launch {
//            _ui.update { it.copy(isLoading = true, error = null) }
//            when (val res = repo.getCategories()) {
//                is Result.Success -> _ui.update { it.copy(isLoading = false, items = res.getOrNull().orEmpty()) }
//                is Result.Failure -> _ui.update { it.copy(isLoading = false, error = res.exceptionOrNull()?.message) }
//            }
//        }
//    }
//
//    fun setQuery(q: String) {
//        _ui.update { it.copy(query = q) }
//    }
//
//    fun filtered(): List<CategoryPublic> {
//        val q = _ui.value.query.trim().lowercase()
//        if (q.isEmpty()) return _ui.value.items
//        return _ui.value.items.filter { it.name.lowercase().contains(q) || it.slug.lowercase().contains(q) }
//    }
//}
