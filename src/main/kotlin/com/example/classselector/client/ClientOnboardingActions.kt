package com.example.classselector.client

import com.example.classselector.embark.EmbarkPurchase
import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.FinalizeSelectionPacket
import net.minecraft.client.Minecraft

object ClientOnboardingActions {
    fun lockCurrentRespawn(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        val dim = player.level().dimension().location().toString()
        ClassSelectionState.lockedRespawn = PendingRespawnSelection(dim, player.blockX, player.blockY, player.blockZ)
        return true
    }

    fun clearLockedRespawn() {
        ClassSelectionState.lockedRespawn = null
    }

    fun submitSpawnOnly(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        markSelectionSubmitted()
        ClassSelectorNetwork.CHANNEL.sendToServer(
            FinalizeSelectionPacket.spawnOnly(
                player.level().dimension().location().toString(),
                player.blockX,
                player.blockY,
                player.blockZ
            )
        )
        return true
    }

    fun submitClassSelection(classId: String): Boolean {
        val respawn = ClassSelectionState.lockedRespawn ?: return false
        markSelectionSubmitted()
        ClassSelectorNetwork.CHANNEL.sendToServer(
            FinalizeSelectionPacket.classSelection(classId, respawn.dim, respawn.x, respawn.y, respawn.z)
        )
        return true
    }

    fun submitEmbarkSelection(purchases: List<EmbarkPurchase>): Boolean {
        val respawn = ClassSelectionState.lockedRespawn ?: return false
        if (purchases.isEmpty()) return false
        markSelectionSubmitted()
        ClassSelectorNetwork.CHANNEL.sendToServer(
            FinalizeSelectionPacket.embarkSelection(purchases, respawn.dim, respawn.x, respawn.y, respawn.z)
        )
        return true
    }

    internal fun markSelectionSubmitted() {
        ClassSelectionState.selectionRequired = false
        ClassSelectionState.promptOpen = false
        ClassSelectionState.reminderCooldownTicks = 0
    }
}
