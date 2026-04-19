package com.example.classselector.kit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClassKitRepositoryTest {
    @Test
    fun parsesClassKitsFromJson() {
        val json = """
            [
              {
                "id": "warrior",
                "title": "Warrior",
                "blurb": "Melee",
                "description": "Front line",
                "items": [
                  {"item": "minecraft:stone_sword", "count": 1, "slot": "inventory"},
                  {"item": "minecraft:iron_helmet", "count": 1, "slot": "armor:head"},
                  {"item": "minecraft:spyglass", "count": 1, "slot": "curio:charm"}
                ]
              }
            ]
        """.trimIndent()

        val kits = ClassKitRepository.parse(json)
        assertEquals(1, kits.size)
        assertEquals("warrior", kits.first().id)
        assertEquals("minecraft:stone_sword", kits.first().items.first().item)
        assertEquals("armor:head", kits.first().items[1].slot)
        assertEquals("curio:charm", kits.first().items[2].slot)
        assertTrue(kits.first().items.first().count > 0)
    }

    @Test
    fun validatesSupportedSlotsIncludingOffhand() {
        val kits = listOf(
            ClassKit(
                id = "guardian",
                title = "Guardian",
                blurb = "Defensive",
                description = "Uses a shield.",
                items = listOf(
                    KitItem(item = "minecraft:shield", slot = "offhand"),
                    KitItem(item = "minecraft:iron_sword", slot = "inventory")
                )
            )
        )

        val validated = ClassKitRepository.validate(kits)
        assertEquals("offhand", validated.first().items.first().slot)
    }

    @Test
    fun rejectsUnsupportedSlots() {
        val kits = listOf(
            ClassKit(
                id = "broken",
                title = "Broken",
                blurb = "Invalid",
                description = "Contains an unsupported slot.",
                items = listOf(
                    KitItem(item = "minecraft:stick", slot = "belt:left")
                )
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ClassKitRepository.validate(kits)
        }
        assertTrue(error.message!!.contains("unsupported slot"))
    }
}
