package com.example.classselector

import com.example.classselector.kit.ClassKitRepository
import com.example.classselector.kit.KitApplicator
import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.RequestOpenMenuPacket
import com.example.classselector.network.SyncClassesPacket
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import net.minecraftforge.event.AddReloadListenerEvent
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
    fun onReload(event: AddReloadListenerEvent) {
        ClassKitRepository.load(event.serverResources.resourceManager)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerLoggedInEvent) {
        val player = event.entity
        val kits = ClassKitRepository.get()
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
