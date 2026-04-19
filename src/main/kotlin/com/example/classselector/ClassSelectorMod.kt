package com.example.classselector

import com.example.classselector.kit.ClassKitRepository
import com.example.classselector.kit.KitApplicator
import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.RequestOpenMenuPacket
import com.example.classselector.network.SyncClassesPacket
import com.example.classselector.respawn.PersonalRespawnService
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

    @JvmStatic
    @SubscribeEvent
    fun onDatapackSync(event: OnDatapackSyncEvent) {
        val kits = runCatching { ClassKitRepository.load(event.playerList.server.resourceManager) }
            .getOrElse {
                val message = Component.literal("Class kits failed to reload; check server logs and datapacks.")
                event.player?.sendSystemMessage(message) ?: event.playerList.broadcastSystemMessage(message, false)
                emptyList()
            }
        val activeInWorld = ClassSelectorScope.isActiveIn(event.playerList.server)

        if (event.player != null) {
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { event.player },
                SyncClassesPacket.fromKits(activeInWorld, kits)
            )
            return
        }

        event.playerList.players.forEach { online ->
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { online },
                SyncClassesPacket.fromKits(activeInWorld, kits)
            )
        }
    }

    @JvmStatic
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
        val activeInWorld = ClassSelectorScope.isActiveIn(player.server)
        ClassSelectorNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with { player },
            SyncClassesPacket.fromKits(activeInWorld, kits)
        )

        if (!activeInWorld) {
            return
        }

        if (!KitApplicator.hasSelectedClass(player)) {
            player.setGameMode(GameType.SPECTATOR)
            player.sendSystemMessage(Component.literal("Choose a class to begin."))
            ClassSelectorNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, RequestOpenMenuPacket())
            return
        }

        PersonalRespawnService.releasePlayerFromSpectator(player)
    }

    @JvmStatic
    @SubscribeEvent
    fun onClone(event: PlayerEvent.Clone) {
        if (!event.isWasDeath) return
        val original = event.original as? ServerPlayer ?: return
        val cloned = event.entity as? ServerPlayer ?: return
        val selected = original.persistentData.getString(KitApplicator.SELECTED_CLASS_TAG)
        if (selected.isNotBlank()) {
            cloned.persistentData.putString(KitApplicator.SELECTED_CLASS_TAG, selected)
        }
        PersonalRespawnService.copyRespawnPoint(original, cloned)
    }
}
