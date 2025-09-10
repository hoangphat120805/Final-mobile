package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.common.ApiProvider
import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.model.CategoryPublic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategoryRepository(
    private val apiProvider: ApiProvider
) {
    private val api get() = apiProvider()

    suspend fun getCategories(): Result<List<CategoryPublic>> = withContext(Dispatchers.IO) {
        safeApiCall { api.getCategories() }
    }
}
