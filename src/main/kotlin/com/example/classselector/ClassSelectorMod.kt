package com.example.classselector

import com.example.classselector.kit.ClassKitRepository
import com.example.classselector.kit.KitApplicator
import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.RequestOpenMenuPacket
import com.example.classselector.network.SyncClassesPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraftforge.event.OnDatapackSyncEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor

@Mod(ClassSelectorMod.MOD_ID)
class ClassSelectorMod {
    init {
        ClassSelectorNetwork.register()
    }

    companion object {
        const val MOD_ID = "classselector"
    }
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID)
object ServerEvents {

    @SubscribeEvent
    fun onDatapackSync(event: OnDatapackSyncEvent) {
        val kits = runCatching { ClassKitRepository.load(event.playerList.server.resourceManager) }
            .getOrElse {
                val message = Component.literal("Class kits failed to reload; check server logs and datapacks.")
                event.player?.sendSystemMessage(message) ?: event.playerList.broadcastSystemMessage(message, false)
                emptyList()
            }

        if (event.player != null) {
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { event.player }, SyncClassesPacket.fromKits(kits))
            return
        }

        event.playerList.players.forEach { online ->
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { online }, SyncClassesPacket.fromKits(kits))
        }
    }
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val kits = if (ClassKitRepository.get().isEmpty()) {
            runCatching { ClassKitRepository.load(player.server.resourceManager) }
                .getOrElse {
                    player.sendSystemMessage(Component.literal("Class kits failed to load; contact an admin."))
                    emptyList()
                }
        } else {
            ClassKitRepository.get()
        }
        ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, SyncClassesPacket.fromKits(kits))

        if (!KitApplicator.hasSelectedClass(player)) {
            player.setGameMode(GameType.SPECTATOR)
            player.sendSystemMessage(Component.literal("Choose a class to begin."))
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, RequestOpenMenuPacket())
        }
    }

    @SubscribeEvent
    fun onClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        val selected = event.original.persistentData.getString(KitApplicator.SELECTED_CLASS_TAG)
        if (selected.isNotBlank()) {
            event.entity.persistentData.putString(KitApplicator.SELECTED_CLASS_TAG, selected)
        }
    }
}
