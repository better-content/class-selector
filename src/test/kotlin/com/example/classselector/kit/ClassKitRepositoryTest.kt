package com.example.classselector.kit

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
