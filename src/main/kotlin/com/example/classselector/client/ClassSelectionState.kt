package com.example.classselector.client

import com.example.classselector.embark.EmbarkPoolItem
import com.example.classselector.embark.EmbarkPurchase
import com.example.classselector.embark.SelectionMode
import com.example.classselector.kit.ClassKit

data class PendingRespawnSelection(
    val dim: String,
    val x: Int,
    val y: Int,
    val z: Int
)

object ClassSelectionState {
    var activeInCurrentWorld: Boolean = false
    var selectionMode: SelectionMode = SelectionMode.CLASS
    var kits: List<ClassKit> = emptyList()
    var embarkItems: List<EmbarkPoolItem> = emptyList()
    var pointQuota: Int = 0
    var promptOpen: Boolean = false
    var selectionRequired: Boolean = false
    var reminderCooldownTicks: Int = 0
    var lockedClassId: String? = null
    var lockedRespawn: PendingRespawnSelection? = null
    val embarkPurchases: MutableMap<String, Int> = linkedMapOf()

    fun clearPendingLocks() {
        lockedClassId = null
        lockedRespawn = null
        embarkPurchases.clear()
    }

    fun hasSelectionOptions(): Boolean = when (selectionMode) {
        SelectionMode.CLASS -> kits.isNotEmpty()
        SelectionMode.EMBARK_POINTS -> pointQuota > 0 && embarkItems.isNotEmpty()
    }

    fun selectedEmbarkPurchases(): List<EmbarkPurchase> =
        embarkItems.mapNotNull { item ->
            val quantity = embarkPurchases[item.id] ?: return@mapNotNull null
            if (quantity > 0) EmbarkPurchase(item.id, quantity) else null
        }

    fun spentEmbarkPoints(): Int =
        embarkItems.sumOf { item -> (embarkPurchases[item.id] ?: 0) * item.cost }

    fun remainingEmbarkPoints(): Int = pointQuota - spentEmbarkPoints()

    fun canPurchase(item: EmbarkPoolItem): Boolean {
        val current = embarkPurchases[item.id] ?: 0
        return current < item.maxPurchases && remainingEmbarkPoints() >= item.cost
    }

    fun addEmbarkPurchase(item: EmbarkPoolItem) {
        if (!canPurchase(item)) return
        embarkPurchases[item.id] = (embarkPurchases[item.id] ?: 0) + 1
    }

    fun removeEmbarkPurchase(item: EmbarkPoolItem) {
        val current = embarkPurchases[item.id] ?: return
        if (current <= 1) {
            embarkPurchases.remove(item.id)
        } else {
            embarkPurchases[item.id] = current - 1
        }
    }

    fun reconcileEmbarkPurchases() {
        val poolById = embarkItems.associateBy { it.id }
        val iterator = embarkPurchases.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val poolItem = poolById[entry.key]
            if (poolItem == null || entry.value <= 0) {
                iterator.remove()
            } else if (entry.value > poolItem.maxPurchases) {
                entry.setValue(poolItem.maxPurchases)
            }
        }

        while (remainingEmbarkPoints() < 0 && embarkPurchases.isNotEmpty()) {
            val lastSelected = embarkPurchases.keys.last()
            val poolItem = poolById[lastSelected] ?: break
            removeEmbarkPurchase(poolItem)
        }
    }

    fun reset() {
        activeInCurrentWorld = false
        selectionMode = SelectionMode.CLASS
        kits = emptyList()
        embarkItems = emptyList()
        pointQuota = 0
        promptOpen = false
        selectionRequired = false
        reminderCooldownTicks = 0
        clearPendingLocks()
    }
}
