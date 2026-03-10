package com.example.classselector.test

import com.example.classselector.ClassSelectorMod
import com.example.classselector.kit.ClassKitRepository
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.gametest.GameTestHolder

@GameTestHolder(ClassSelectorMod.MOD_ID)
object ClassSelectorGameTests {
    @GameTest(template = "empty")
    fun kitsShouldLoad(helper: GameTestHelper) {
        val kits = ClassKitRepository.load(helper.level.server.resourceManager)
        if (kits.isEmpty()) {
            throw GameTestAssertException("Expected class kits to be loaded from JSON")
        }
        helper.succeed()
    }
}
