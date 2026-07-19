package com.example.classselector

import com.example.classselector.kit.KitApplicator
import com.example.classselector.integration.OnboardingIntegration
import com.example.classselector.integration.OnboardingVisibilitySync
import com.example.classselector.embark.SelectionMode
import com.example.classselector.embark.SelectionDataRepository
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
import net.minecraftforge.event.server.ServerAboutToStartEvent
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
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        // Fail fast during initial startup if active selection config syntax is invalid.
        SelectionDataRepository.load()
    }

    @JvmStatic
    @SubscribeEvent
    fun onDatapackSync(event: OnDatapackSyncEvent) {
        val selectionData = SelectionDataRepository.load()
        val activeInWorld = ClassSelectorScope.isActiveIn(event.playerList.server)

        if (event.player != null) {
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { event.player },
                SyncClassesPacket.fromSelectionData(activeInWorld, selectionData)
            )
            return
        }

        event.playerList.players.forEach { online ->
            ClassSelectorNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with { online },
                SyncClassesPacket.fromSelectionData(activeInWorld, selectionData)
            )
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val selectionData = SelectionDataRepository.getOrLoad()
        val activeInWorld = ClassSelectorScope.isActiveIn(player.server)
        ClassSelectorNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with { player },
            SyncClassesPacket.fromSelectionData(activeInWorld, selectionData)
        )
        OnboardingVisibilitySync.sync(player.server)

        if (!activeInWorld) {
            return
        }

        if (!OnboardingIntegration.hasCompletedOnboarding(player)) {
            player.setGameMode(GameType.SPECTATOR)
            val joinPrompt = when (selectionData.mode) {
                SelectionMode.NONE -> "Press K to set your starting spawn and begin."
                SelectionMode.CLASS -> "Choose a class to begin."
                SelectionMode.EMBARK_POINTS -> "Choose your starting supplies and respawn to begin."
            }
            player.sendSystemMessage(Component.literal(joinPrompt))
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
        OnboardingIntegration.copyPersistentState(original, cloned)
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        OnboardingVisibilitySync.sync(player.server)
    }
}
