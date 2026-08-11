package com.example.classselector.test

import com.example.classselector.ClassSelectorMod
import com.example.classselector.kit.ClassKitRepository
import com.example.classselector.kit.KitItemStackFactory
import com.example.classselector.kit.KitSlot
import com.example.classselector.respawn.PersonalRespawnPoint
import com.example.classselector.respawn.PersonalRespawnService
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(ClassSelectorMod.MOD_ID)
object ClassSelectorGameTests {
    @JvmStatic
    @GameTest(template = "empty")
    fun kitsShouldLoad(helper: GameTestHelper) {
        val kits = ClassKitRepository.load()
        val expectedIds = setOf(
            "wayfinder",
            "field_cook",
            "rail_scout",
            "flood_runner",
            "market_runner",
            "trail_wrangler"
        )
        val actualIds = kits.map { it.id }.toSet()
        if (actualIds != expectedIds) {
            throw GameTestAssertException("Expected ids $expectedIds, found $actualIds")
        }

        val wayfinder = kits.firstOrNull { it.id == "wayfinder" }
            ?: throw GameTestAssertException("Expected wayfinder kit to exist")
        if (wayfinder.items.firstOrNull()?.slot != "weapon.offhand") {
            throw GameTestAssertException("Expected wayfinder compass to target weapon.offhand")
        }

        kits.forEach { kit ->
            kit.items.forEachIndexed { index, item ->
                if (!KitSlot.isSupported(item.slot)) {
                    throw GameTestAssertException("Unsupported slot '${item.slot}' in kit '${kit.id}' item #${index + 1}")
                }
                runCatching { KitItemStackFactory.parse(item.item) }.getOrElse { error ->
                    throw GameTestAssertException("Invalid item spec in kit '${kit.id}' item #${index + 1}: ${error.message}")
                }
            }
        }

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun invalidRespawnSelectionSnapsToNearestValidSite(helper: GameTestHelper) {
        val requestedFeetPos = BlockPos(1, 2, 1)
        val nearestValidFeetPos = BlockPos(2, 2, 1)
        val fartherValidFeetPos = BlockPos(4, 2, 1)

        helper.setBlock(requestedFeetPos.below(), Blocks.AIR)
        helper.setBlock(requestedFeetPos, Blocks.STONE)
        helper.setBlock(requestedFeetPos.above(), Blocks.STONE)

        helper.setBlock(nearestValidFeetPos.below(), Blocks.STONE)
        helper.setBlock(nearestValidFeetPos, Blocks.AIR)
        helper.setBlock(nearestValidFeetPos.above(), Blocks.AIR)

        helper.setBlock(fartherValidFeetPos.below(), Blocks.STONE)
        helper.setBlock(fartherValidFeetPos, Blocks.AIR)
        helper.setBlock(fartherValidFeetPos.above(), Blocks.AIR)

        val requestedFeetAbs = helper.absolutePos(requestedFeetPos)
        val nearestValidFeetAbs = helper.absolutePos(nearestValidFeetPos)
        val fartherValidFeetAbs = helper.absolutePos(fartherValidFeetPos)

        val prepared = PersonalRespawnService.prepareRespawnPoint(
            helper.level.server,
            PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            )
        )

        helper.assertTrue(prepared.locationAdjusted, "Expected invalid selection to snap to a nearby valid respawn site")
        helper.assertTrue(prepared.sitePrepared, "Expected snapped respawn site to be prepared with crying obsidian")
        helper.assertTrue(
            prepared.point == PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = nearestValidFeetAbs.x,
                y = nearestValidFeetAbs.y,
                z = nearestValidFeetAbs.z
            ),
            "Expected respawn point to snap to nearest valid site, found ${prepared.point}"
        )
        helper.assertBlockPresent(Blocks.CRYING_OBSIDIAN, nearestValidFeetPos.below())
        helper.assertBlockPresent(Blocks.AIR, nearestValidFeetPos)
        helper.assertBlockPresent(Blocks.AIR, nearestValidFeetPos.above())
        helper.assertBlockPresent(Blocks.STONE, fartherValidFeetPos.below())

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun respawnSnapPrefersThreeHorizontalOverOneVertical(helper: GameTestHelper) {
        val requestedFeetPos = BlockPos(3, 4, 3)
        val verticalCandidate = BlockPos(3, 5, 3)
        val horizontalCandidate = BlockPos(5, 4, 3)

        for (x in 0..7) {
            for (y in 0..7) {
                for (z in 0..7) {
                    helper.setBlock(BlockPos(x, y, z), Blocks.AIR)
                }
            }
        }

        helper.setBlock(requestedFeetPos, Blocks.STONE)
        helper.setBlock(requestedFeetPos.above(), Blocks.STONE)

        helper.setBlock(verticalCandidate.below(), Blocks.STONE)
        helper.setBlock(verticalCandidate, Blocks.AIR)
        helper.setBlock(verticalCandidate.above(), Blocks.AIR)

        helper.setBlock(horizontalCandidate.below(), Blocks.STONE)
        helper.setBlock(horizontalCandidate, Blocks.AIR)
        helper.setBlock(horizontalCandidate.above(), Blocks.AIR)

        val requestedFeetAbs = helper.absolutePos(requestedFeetPos)
        val horizontalCandidateAbs = helper.absolutePos(horizontalCandidate)

        val prepared = PersonalRespawnService.prepareRespawnPoint(
            helper.level.server,
            PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            )
        )

        helper.assertTrue(
            prepared.point == PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = horizontalCandidateAbs.x,
                y = horizontalCandidateAbs.y,
                z = horizontalCandidateAbs.z
            ),
            "Expected horizontal candidate to beat vertical candidate with 3:1 vertical weighting, found ${prepared.point}"
        )
        helper.assertBlockPresent(Blocks.CRYING_OBSIDIAN, horizontalCandidate.below())
        helper.assertBlockPresent(Blocks.STONE, verticalCandidate.below())

        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "empty")
    fun alreadyPreparedValidRespawnSiteIsLeftInPlace(helper: GameTestHelper) {
        val requestedFeetPos = BlockPos(1, 1, 1)

        helper.setBlock(requestedFeetPos.below(), Blocks.CRYING_OBSIDIAN)
        helper.setBlock(requestedFeetPos, Blocks.AIR)
        helper.setBlock(requestedFeetPos.above(), Blocks.AIR)

        val requestedFeetAbs = helper.absolutePos(requestedFeetPos)
        val prepared = PersonalRespawnService.prepareRespawnPoint(
            helper.level.server,
            PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            )
        )

        helper.assertTrue(!prepared.locationAdjusted, "Expected a valid requested site to stay selected")
        helper.assertTrue(!prepared.sitePrepared, "Expected already prepared site to avoid extra block writes")
        helper.assertTrue(
            prepared.point == PersonalRespawnPoint(
                dim = helper.level.dimension().location().toString(),
                x = requestedFeetAbs.x,
                y = requestedFeetAbs.y,
                z = requestedFeetAbs.z
            ),
            "Expected prepared point to equal requested point, found ${prepared.point}"
        )
        helper.assertBlockPresent(Blocks.CRYING_OBSIDIAN, requestedFeetPos.below())
        helper.assertBlockPresent(Blocks.AIR, requestedFeetPos)
        helper.assertBlockPresent(Blocks.AIR, requestedFeetPos.above())

        helper.succeed()
    }
}
