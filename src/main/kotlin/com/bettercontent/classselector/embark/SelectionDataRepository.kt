package com.bettercontent.classselector.embark

import com.bettercontent.classselector.kit.ClassKit
import com.bettercontent.classselector.kit.ClassKitRepository

data class SelectionData(
    val mode: SelectionMode,
    val kits: List<ClassKit>,
    val embarkSettings: EmbarkSettings
)

object SelectionDataRepository {
    @Volatile
    private var cached: SelectionData? = null

    fun load(): SelectionData {
        val embarkSettings = EmbarkConfigRepository.load()
        val kits = when (embarkSettings.mode) {
            SelectionMode.CLASS -> ClassKitRepository.load()
            SelectionMode.NONE,
            SelectionMode.EMBARK_POINTS -> ClassKitRepository.get()
        }
        val data = SelectionData(
            mode = embarkSettings.mode,
            kits = kits,
            embarkSettings = embarkSettings
        )
        cached = data
        return data
    }

    fun getOrLoad(): SelectionData = cached ?: load()
}
