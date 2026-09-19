package com.bettercontent.classselector.respawn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingSitePolicyTest {
    @Test
    fun permitsOnlyTheSafeTemperateStarterWhitelist() {
        assertTrue(isSafeTemperateBiomeId("minecraft:plains"))
        assertTrue(isSafeTemperateBiomeId("minecraft:flower_forest"))
        assertFalse(isSafeTemperateBiomeId("minecraft:desert"))
        assertFalse(isSafeTemperateBiomeId("minecraft:deep_dark"))
        assertFalse(isSafeTemperateBiomeId("unknown:plains"))
    }
}
