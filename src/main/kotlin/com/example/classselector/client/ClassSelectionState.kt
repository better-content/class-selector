package com.example.classselector.client

import com.example.classselector.kit.ClassKit

data class PendingRespawnSelection(
    val dim: String,
    val x: Int,
    val y: Int,
    val z: Int
)

object ClassSelectionState {
    var activeInCurrentWorld: Boolean = false
    var kits: List<ClassKit> = emptyList()
    var promptOpen: Boolean = false
    var selectionRequired: Boolean = false
    var reminderCooldownTicks: Int = 0
    var lockedClassId: String? = null
    var lockedRespawn: PendingRespawnSelection? = null

    fun clearPendingLocks() {
        lockedClassId = null
        lockedRespawn = null
    }

    fun reset() {
        activeInCurrentWorld = false
        kits = emptyList()
        promptOpen = false
        selectionRequired = false
        reminderCooldownTicks = 0
        clearPendingLocks()
    }
}
