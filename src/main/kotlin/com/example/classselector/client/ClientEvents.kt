package com.example.classselector.client

import com.example.classselector.ClassSelectorMod
import com.example.classselector.network.ClassSelectorNetwork
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID, value = [Dist.CLIENT], bus = Mod.EventBusSubscriber.Bus.MOD)
object ClientModEvents {
    var openClassMenuKey: KeyMapping? = null
    var openRespawnVotingKey: KeyMapping? = null

    @JvmStatic
    @SubscribeEvent
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        val classKey = KeyMapping("key.classselector.open_menu", GLFW.GLFW_KEY_K, "key.categories.gameplay")
        val votingKey = KeyMapping("key.classselector.open_respawn_voting", GLFW.GLFW_KEY_V, "key.categories.gameplay")
        openClassMenuKey = classKey
        openRespawnVotingKey = votingKey
        event.register(classKey)
        event.register(votingKey)
    }
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID, value = [Dist.CLIENT])
object ClientForgeEvents {
    private const val REMINDER_INTERVAL_TICKS = 100

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()
        val player = mc.player

        if (player == null) {
            ClassSelectionState.activeInCurrentWorld = false
            ClassSelectionState.kits = emptyList()
            ClassSelectionState.promptOpen = false
            ClassSelectionState.selectionRequired = false
            ClassSelectionState.reminderCooldownTicks = 0
            RespawnVotingState.snapshot = RespawnVotingSnapshot(false, false, 0, null, emptyList())
            return
        }

        if (ClassSelectionState.activeInCurrentWorld && ClientModEvents.openClassMenuKey?.consumeClick() == true) {
            if (ClassSelectionState.kits.isNotEmpty()) {
                mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            } else {
                player.sendSystemMessage(Component.literal("Class kits are still syncing."))
            }
        }

        if (ClassSelectionState.activeInCurrentWorld && ClientModEvents.openRespawnVotingKey?.consumeClick() == true) {
            mc.setScreen(RespawnVotingScreen())
        }

        if (ClassSelectionState.promptOpen && mc.screen == null) {
            ClassSelectionState.promptOpen = false
            ClassSelectionState.reminderCooldownTicks = REMINDER_INTERVAL_TICKS
            val openMessage = if (RespawnVotingState.snapshot.canVote) {
                "Press K to choose a class or V to vote on a respawn hub."
            } else {
                "Press K to choose a class."
            }
            player.displayClientMessage(Component.literal(openMessage), true)
            if (ClassSelectionState.kits.isNotEmpty()) {
                mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            }
            return
        }

        if (!ClassSelectionState.activeInCurrentWorld || !ClassSelectionState.selectionRequired || mc.screen != null) {
            return
        }

        if (ClassSelectionState.reminderCooldownTicks > 0) {
            ClassSelectionState.reminderCooldownTicks--
            return
        }

        ClassSelectionState.reminderCooldownTicks = REMINDER_INTERVAL_TICKS
        val reminderMessage = if (RespawnVotingState.snapshot.canVote) {
            "Press K to select your class. Press V to propose or vote on a start location."
        } else {
            "Press K to select your class."
        }
        player.displayClientMessage(Component.literal(reminderMessage), true)
    }
}
