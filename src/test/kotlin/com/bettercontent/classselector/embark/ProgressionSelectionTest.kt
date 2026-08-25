package com.bettercontent.classselector.embark

import com.bettercontent.classselector.kit.ClassKit
import com.bettercontent.classselector.kit.KitItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionSelectionTest {
    private val classIds = listOf(
        "wayfinder", "field_cook", "rail_scout", "flood_runner", "market_runner", "trail_wrangler"
    )
    private val kits = classIds.mapIndexed { index, id ->
        ClassKit(id, id, "", "", listOf(KitItem("minecraft:item_$index")))
    }
    private val configured = EmbarkSettings(
        SelectionMode.PROGRESSION,
        6,
        kits.mapIndexed { index, kit ->
            EmbarkPoolItem(
                "item_$index", kit.id, "Class supplies", "", kit.items.single().item, 1, 1, 1, "inventory"
            )
        }
    )

    @Test
    fun startsWithSpawnOnlyBeforeTheFreeClassSelector() {
        val data = resolve(SelectionMode.NONE)

        assertEquals(SelectionMode.NONE, data.mode)
        assertTrue(data.kits.isEmpty())
        assertFalse(data.starterSchematicannon)
    }

    @Test
    fun revealsExactlyThePaidClassesInCanonicalOrder() {
        classIds.indices.forEach { lastUnlocked ->
            val unlocked = classIds.take(lastUnlocked + 1).toSet()
            val data = resolve(SelectionMode.CLASS, unlocked)

            assertEquals(SelectionMode.CLASS, data.mode)
            assertEquals(classIds.take(lastUnlocked + 1), data.kits.map { it.id })
            assertFalse(data.starterSchematicannon)
        }
    }

    @Test
    fun embarkReplacesClassesAtEveryBudgetTier() {
        listOf(6, 9, 12, 15, 18).forEach { budget ->
            val data = resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), budget)

            assertEquals(SelectionMode.EMBARK_POINTS, data.mode)
            assertTrue(data.kits.isEmpty())
            assertEquals(budget, data.embarkSettings.pointQuota)
        }
    }

    @Test
    fun finalEntitlementAddsTheStarterSchematicannonWithoutChangingEmbark() {
        val data = resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), 18, starterSchematicannon = true)

        assertEquals(18, data.embarkSettings.pointQuota)
        assertTrue(data.starterSchematicannon)
    }

    @Test
    fun rejectsIncompleteClassOrEmbarkCatalogs() {
        assertFailsWith<IllegalArgumentException> {
            SelectionDataRepository.resolveProgression(configured, kits.dropLast(1), policy(SelectionMode.NONE))
        }
        assertFailsWith<IllegalArgumentException> {
            SelectionDataRepository.resolveProgression(
                configured.copy(items = configured.items.dropLast(1)), kits, policy(SelectionMode.NONE)
            )
        }
    }

    @Test
    fun rejectsImpossibleLifecyclePolicies() {
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.CLASS, emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.CLASS, setOf("unknown"))
        }
        assertFailsWith<IllegalArgumentException> {
            resolve(SelectionMode.EMBARK_POINTS, classIds.toSet(), 7)
        }
    }

    private fun resolve(
        mode: SelectionMode,
        unlocked: Set<String> = emptySet(),
        budget: Int = 0,
        starterSchematicannon: Boolean = false
    ): SelectionData = SelectionDataRepository.resolveProgression(
        configured, kits, policy(mode, unlocked, budget, starterSchematicannon)
    )

    private fun policy(
        mode: SelectionMode,
        unlocked: Set<String> = emptySet(),
        budget: Int = 0,
        starterSchematicannon: Boolean = false
    ) = ProgressionPolicy(mode, unlocked, budget, starterSchematicannon)
}
