package com.example.classselector.client

import com.example.classselector.ClassSelectorMod
import com.example.classselector.embark.SelectionMode
import com.mojang.blaze3d.platform.InputConstants
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
    val openClassMenuKey: KeyMapping = KeyMapping(
        "key.classselector.open_menu",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "key.categories.classselector"
    )

    @JvmStatic
    @SubscribeEvent
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        event.register(openClassMenuKey)
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
            ClassSelectionState.reset()
            return
        }

        if (ClassSelectionState.activeInCurrentWorld && ClassSelectionState.selectionRequired && ClientModEvents.openClassMenuKey.consumeClick()) {
            if (ClassSelectionState.hasSelectionOptions()) {
                openSelectionScreen(mc)
            } else {
                player.sendSystemMessage(Component.literal("Starting options are still syncing."))
            }
        }

        if (ClassSelectionState.promptOpen && mc.screen == null) {
            ClassSelectionState.promptOpen = false
            ClassSelectionState.reminderCooldownTicks = REMINDER_INTERVAL_TICKS
            player.displayClientMessage(
                Component.translatable(
                    "message.classselector.lock_in_prompt",
                    ClientModEvents.openClassMenuKey.translatedKeyMessage
                ),
                true
            )
            if (ClassSelectionState.hasSelectionOptions()) {
                openSelectionScreen(mc)
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
        player.displayClientMessage(
            Component.translatable(
                "message.classselector.lock_in_reminder",
                ClientModEvents.openClassMenuKey.translatedKeyMessage
            ),
            true
        )
    }

    private fun openSelectionScreen(mc: Minecraft) {
        when (ClassSelectionState.selectionMode) {
            SelectionMode.CLASS -> mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            SelectionMode.EMBARK_POINTS -> mc.setScreen(
                EmbarkSelectionScreen(ClassSelectionState.embarkItems, ClassSelectionState.pointQuota)
            )
        }
    }
}
