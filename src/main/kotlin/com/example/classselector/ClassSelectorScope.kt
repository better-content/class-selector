package com.example.classselector

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType

object ClassSelectorScope {
    fun isActiveIn(server: MinecraftServer): Boolean = server.worldData.gameType == GameType.SURVIVAL
}
