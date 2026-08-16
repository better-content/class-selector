package com.bettercontent.classselector

import net.minecraft.server.MinecraftServer

object ClassSelectorScope {
    fun isActiveIn(server: MinecraftServer): Boolean = true
}
