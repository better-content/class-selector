package com.example.classselector.client

import com.example.classselector.embark.EmbarkPoolItem
import com.example.classselector.embark.SelectionMode
import com.example.classselector.kit.ClassKit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassSelectionStateTest {
    @Test
    fun resetsSelectionState() {
        ClassSelectionState.activeInCurrentWorld = true
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.promptOpen = true
        ClassSelectionState.selectionRequired = true
        ClassSelectionState.reminderCooldownTicks = 123
        ClassSelectionState.lockedClassId = "miner"
        ClassSelectionState.lockedRespawn = PendingRespawnSelection("minecraft:overworld", 1, 64, 2)
        ClassSelectionState.embarkItems = listOf(poolItem())
        ClassSelectionState.pointQuota = 4
        ClassSelectionState.embarkPurchases["logs"] = 2

        ClassSelectionState.reset()

        assertFalse(ClassSelectionState.activeInCurrentWorld)
        assertEquals(SelectionMode.CLASS, ClassSelectionState.selectionMode)
        assertFalse(ClassSelectionState.promptOpen)
        assertFalse(ClassSelectionState.selectionRequired)
        assertTrue(ClassSelectionState.kits.isEmpty())
        assertTrue(ClassSelectionState.embarkItems.isEmpty())
        assertTrue(ClassSelectionState.embarkPurchases.isEmpty())
        assertTrue(ClassSelectionState.pointQuota == 0)
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
        ClassSelectionState.embarkPurchases["logs"] = 1

        ClassSelectionState.clearPendingLocks()

        assertNull(ClassSelectionState.lockedClassId)
        assertNull(ClassSelectionState.lockedRespawn)
        assertTrue(ClassSelectionState.embarkPurchases.isEmpty())
        assertTrue(ClassSelectionState.kits.isNotEmpty())
        assertTrue(ClassSelectionState.kits[0].id == "miner")
    }

    @Test
    fun tracksEmbarkPurchasesWithinPointQuota() {
        val logs = poolItem(id = "logs", cost = 1, maxPurchases = 3)
        val shield = poolItem(id = "shield", cost = 3, maxPurchases = 1)
        ClassSelectionState.selectionMode = SelectionMode.EMBARK_POINTS
        ClassSelectionState.pointQuota = 4
        ClassSelectionState.embarkItems = listOf(logs, shield)
        ClassSelectionState.clearPendingLocks()

        ClassSelectionState.addEmbarkPurchase(logs)
        ClassSelectionState.addEmbarkPurchase(logs)
        ClassSelectionState.addEmbarkPurchase(shield)

        assertEquals(2, ClassSelectionState.embarkPurchases["logs"])
        assertNull(ClassSelectionState.embarkPurchases["shield"])
        assertEquals(2, ClassSelectionState.spentEmbarkPoints())
        assertEquals(2, ClassSelectionState.remainingEmbarkPoints())

        ClassSelectionState.removeEmbarkPurchase(logs)
        ClassSelectionState.addEmbarkPurchase(shield)

        assertEquals(1, ClassSelectionState.embarkPurchases["logs"])
        assertEquals(1, ClassSelectionState.embarkPurchases["shield"])
        assertEquals(4, ClassSelectionState.spentEmbarkPoints())
    }

    private fun poolItem(
        id: String = "logs",
        cost: Int = 1,
        maxPurchases: Int = 2
    ) = EmbarkPoolItem(
        id = id,
        title = id,
        blurb = "",
        item = "minecraft:oak_log",
        count = 16,
        cost = cost,
        maxPurchases = maxPurchases,
        slot = "inventory"
    )
}
