package com.meshlink.app.data.repository

import android.content.Context
import com.meshlink.app.domain.model.EmergencyGuide
import com.meshlink.app.domain.repository.EmergencyGuideRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyGuideRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : EmergencyGuideRepository {

    private val guidesFlow = MutableStateFlow<List<EmergencyGuide>>(emptyList())

    init {
        loadGuides()
    }

    private fun loadGuides() {
        try {
            val jsonString = context.assets.open("emergency_packs/guides.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val guides = mutableListOf<EmergencyGuide>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                
                val stepsArray = obj.optJSONArray("steps")
                val steps = buildList {
                    if (stepsArray != null) {
                        for (j in 0 until stepsArray.length()) add(stepsArray.getString(j))
                    }
                }

                val warningsArray = obj.optJSONArray("warnings")
                val warnings = buildList {
                    if (warningsArray != null) {
                        for (j in 0 until warningsArray.length()) add(warningsArray.getString(j))
                    }
                }

                val keywordsArray = obj.optJSONArray("keywords")
                val keywords = buildList {
                    if (keywordsArray != null) {
                        for (j in 0 until keywordsArray.length()) add(keywordsArray.getString(j))
                    }
                }

                guides.add(
                    EmergencyGuide(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        icon = obj.optString("icon", "info"),
                        steps = steps,
                        warnings = warnings,
                        keywords = keywords
                    )
                )
            }
            guidesFlow.value = guides
        } catch (e: Exception) {
            Timber.e(e, "Failed to load emergency guides")
        }
    }

    override fun getAllGuides(): Flow<List<EmergencyGuide>> = guidesFlow

    override fun searchGuides(query: String): Flow<List<EmergencyGuide>> {
        return guidesFlow.map { guides ->
            if (query.isBlank()) {
                guides
            } else {
                val lowerQuery = query.lowercase()
                guides.filter { guide ->
                    guide.title.lowercase().contains(lowerQuery) ||
                    guide.keywords.any { it.lowercase().contains(lowerQuery) }
                }
            }
        }
    }

    override suspend fun getGuideById(id: String): EmergencyGuide? = withContext(Dispatchers.IO) {
        guidesFlow.value.find { it.id == id }
    }
}
