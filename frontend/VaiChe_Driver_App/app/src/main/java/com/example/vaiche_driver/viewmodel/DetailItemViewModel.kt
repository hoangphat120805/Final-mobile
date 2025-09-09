//package com.example.vaiche_driver.ui.item
//
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.vaiche_driver.data.repository.OrderRepository
//import com.example.vaiche_driver.model.OrderPublic
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.update
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//enum class DetailMode { ADD, EDIT }
//
//data class DetailUiState(
//    val isLoading: Boolean = false,
//    val success: Boolean = false,
//    val error: String? = null,
//    val lastOrder: OrderPublic? = null
//)
//
//@HiltViewModel
//class DetailItemViewModel @Inject constructor(
//    private val repo: OrderRepository
//) : ViewModel() {
//
//    private val _ui = MutableStateFlow(DetailUiState())
//    val ui: StateFlow<DetailUiState> = _ui
//
//    fun add(orderId: String, categoryId: String, qty: Double) {
//        viewModelScope.launch {
//            _ui.update { it.copy(isLoading = true, error = null, success = false) }
//            val result = repo.addItemToOrder(orderId, categoryId, qty)
//            if (result.isSuccess) {
//                _ui.update { it.copy(isLoading = false, success = true, lastOrder = result.getOrNull()) }
//            } else {
//                _ui.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
//            }
//        }
//    }
//
//    fun update(orderId: String, orderItemId: String, qty: Double) {
//        viewModelScope.launch {
//            _ui.update { it.copy(isLoading = true, error = null, success = false) }
//            val result = repo.updateOrderItem(orderId, orderItemId, qty)
//            if (result.isSuccess) {
//                _ui.update { it.copy(isLoading = false, success = true, lastOrder = result.getOrNull()) }
//            } else {
//                _ui.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
//            }
//        }
//    }
//
//    fun delete(orderId: String, orderItemId: String) {
//        viewModelScope.launch {
//            _ui.update { it.copy(isLoading = true, error = null, success = false) }
//            val result = repo.deleteOrderItem(orderId, orderItemId)
//            if (result.isSuccess) {
//                _ui.update { it.copy(isLoading = false, success = true, lastOrder = result.getOrNull()) }
//            } else {
//                _ui.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
//            }
//        }
//    }
//
//    fun clearTransient() {
//        _ui.update { it.copy(success = false, error = null) }
//    }
//}
