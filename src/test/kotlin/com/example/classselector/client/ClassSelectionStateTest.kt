package com.example.classselector.client

import com.example.classselector.kit.ClassKit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassSelectionStateTest {
    @Test
    fun resetsSelectionState() {
        ClassSelectionState.activeInCurrentWorld = true
        ClassSelectionState.promptOpen = true
        ClassSelectionState.selectionRequired = true
        ClassSelectionState.reminderCooldownTicks = 123
        ClassSelectionState.lockedClassId = "miner"
        ClassSelectionState.lockedRespawn = PendingRespawnSelection("minecraft:overworld", 1, 64, 2)

        ClassSelectionState.reset()

        assertFalse(ClassSelectionState.activeInCurrentWorld)
        assertFalse(ClassSelectionState.promptOpen)
        assertFalse(ClassSelectionState.selectionRequired)
        assertTrue(ClassSelectionState.kits.isEmpty())
        assertTrue(ClassSelectionState.reminderCooldownTicks == 0)
        assertNull(ClassSelectionState.lockedClassId)
        assertNull(ClassSelectionState.lockedRespawn)
    }

    @Test
    fun clearPendingLocksOnlyClearsLocks() {
        val kit = ClassKit("miner", "Miner", "Dig", "Hard", emptyList())
        ClassSelectionState.kits = listOf(kit)
        ClassSelectionState.lockedClassId = "miner"
        ClassSelectionState.lockedRespawn = PendingRespawnSelection("minecraft:overworld", 3, 4, 5)

        ClassSelectionState.clearPendingLocks()

        assertNull(ClassSelectionState.lockedClassId)
        assertNull(ClassSelectionState.lockedRespawn)
        assertTrue(ClassSelectionState.kits.isNotEmpty())
        assertTrue(ClassSelectionState.kits[0].id == "miner")
    }
}
