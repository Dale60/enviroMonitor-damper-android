package com.example.damperlocator.floorplan

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class FloorPlanRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private val _floorPlans = MutableStateFlow<List<FloorPlan>>(emptyList())
    val floorPlans: StateFlow<List<FloorPlan>> = _floorPlans.asStateFlow()

    init {
        _floorPlans.value = loadAllFloorPlans()
    }

    /**
     * Save or update a floor plan
     */
    suspend fun save(floorPlan: FloorPlan) = withContext(Dispatchers.IO) {
        val updated = floorPlan.copy(modifiedAtMs = System.currentTimeMillis())
        val json = gson.toJson(updated)
        prefs.edit().putString(updated.id, json).apply()

        // Update index
        val index = loadIndex().toMutableSet()
        index.add(updated.id)
        saveIndex(index)

        // Refresh list
        _floorPlans.value = loadAllFloorPlans()
    }

    /**
     * Get a specific floor plan by ID
     */
    fun getById(id: String): FloorPlan? {
        val json = prefs.getString(id, null) ?: return null
        return try {
            gson.fromJson(json, FloorPlan::class.java)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Delete a floor plan
     */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(id).apply()
        val index = loadIndex().toMutableSet()
        index.remove(id)
        saveIndex(index)
        _floorPlans.value = loadAllFloorPlans()
    }

    /**
     * Export floor plan as JSON string (for sharing)
     */
    fun exportToJson(floorPlan: FloorPlan): String {
        return gson.toJson(floorPlan)
    }

    /**
     * Import floor plan from JSON string
     */
    suspend fun importFromJson(json: String): FloorPlan? {
        return try {
            val plan = gson.fromJson(json, FloorPlan::class.java)
            save(plan)
            plan
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAllFloorPlans(): List<FloorPlan> {
        return loadIndex().mapNotNull { id -> getById(id) }
            .sortedByDescending { it.modifiedAtMs }
    }

    private fun loadIndex(): Set<String> {
        val json = prefs.getString(INDEX_KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveIndex(index: Set<String>) {
        prefs.edit().putString(INDEX_KEY, gson.toJson(index)).apply()
    }

    companion object {
        private const val PREFS_NAME = "floor_plans"
        private const val INDEX_KEY = "_index"
    }
}
