package com.example.vaicheuserapp

import android.util.Log
import com.example.vaicheuserapp.data.model.CategoryPublic

object CategoryCache {
    // Map from category ID to CategoryPublic object for quick lookup
    private var categoriesMap: Map<String, CategoryPublic> = emptyMap()

    // Call this from HomeFragment after fetching categories
    fun setCategories(categories: List<CategoryPublic>) {
        categoriesMap = categories.associateBy { it.id }
        Log.d("CategoryCache", "Categories cache populated with ${categoriesMap.size} items.")
    }

    // Use this to get a category by its ID
    fun getCategoryById(categoryId: String): CategoryPublic? {
        return categoriesMap[categoryId]
    }

    // Clear the cache (e.g., on logout if categories are user-specific, though usually they are not)
    fun clear() {
        categoriesMap = emptyMap()
        Log.d("CategoryCache", "Categories cache cleared.")
    }
}