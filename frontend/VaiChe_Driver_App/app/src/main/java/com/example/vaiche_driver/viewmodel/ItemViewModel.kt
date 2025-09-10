package com.example.vaiche_driver.viewmodel

import androidx.lifecycle.*
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.CategoryRepository
import com.example.vaiche_driver.model.CategoryPublic
import kotlinx.coroutines.launch

class ItemViewModel(
    private val repo: CategoryRepository = CategoryRepository { RetrofitClient.instance }
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _categories = MutableLiveData<List<CategoryPublic>>(emptyList())
    val categories: LiveData<List<CategoryPublic>> = _categories

    private val _filtered = MutableLiveData<List<CategoryPublic>>(emptyList())
    val filtered: LiveData<List<CategoryPublic>> = _filtered

    private val _error = MutableLiveData<Event<String>>()
    val error: LiveData<Event<String>> = _error

    fun load() {
        if (_categories.value?.isNotEmpty() == true) return
        _isLoading.value = true
        viewModelScope.launch {
            repo.getCategories()
                .onSuccess {
                    _categories.value = it
                    _filtered.value = it
                }
                .onFailure { e -> _error.value = Event(e.message ?: "Load categories failed") }
            _isLoading.value = false
        }
    }

    fun filter(query: String) {
        val src = _categories.value ?: emptyList()
        if (query.isBlank()) {
            _filtered.value = src
        } else {
            val q = query.trim().lowercase()
            _filtered.value = src.filter {
                it.name.lowercase().contains(q) || it.slug.lowercase().contains(q)
            }
        }
    }
}
