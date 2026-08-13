package com.bettercontent.classselector.client

import com.bettercontent.classselector.ClassSelectorMod
import com.bettercontent.classselector.embark.SelectionMode
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
        "key.class_selector.open_menu",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "key.categories.class_selector"
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
            OnboardingPlayerVisibility.clear(mc)
            ClassSelectionState.reset()
            return
        }

        OnboardingPlayerVisibility.tick(mc)

        if (ClassSelectionState.activeInCurrentWorld && ClassSelectionState.selectionRequired && ClientModEvents.openClassMenuKey.consumeClick()) {
            if (ClassSelectionState.selectionMode == SelectionMode.NONE) {
                finalizeSpawnOnlySelection(mc)
            } else if (ClassSelectionState.hasSelectionOptions()) {
                openSelectionScreen(mc)
            } else {
                player.sendSystemMessage(Component.literal("Starting options are still syncing."))
            }
        }

        if (ClassSelectionState.promptOpen && mc.screen == null) {
            ClassSelectionState.promptOpen = false
            ClassSelectionState.reminderCooldownTicks = REMINDER_INTERVAL_TICKS
            player.displayClientMessage(
                lockInPromptComponent(),
                true
            )
            if (ClassSelectionState.selectionMode != SelectionMode.NONE && ClassSelectionState.hasSelectionOptions()) {
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
            lockInReminderComponent(),
            true
        )
    }

    private fun finalizeSpawnOnlySelection(mc: Minecraft) {
        ClientOnboardingActions.submitSpawnOnly(mc)
    }

    private fun openSelectionScreen(mc: Minecraft) {
        when (ClassSelectionState.selectionMode) {
            SelectionMode.NONE -> {}
            SelectionMode.CLASS -> mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            SelectionMode.EMBARK_POINTS -> mc.setScreen(
                EmbarkSelectionScreen(ClassSelectionState.embarkItems, ClassSelectionState.pointQuota)
            )
        }
    }

    private fun lockInPromptComponent(): Component =
        when (ClassSelectionState.selectionMode) {
            SelectionMode.NONE -> Component.translatable(
                "message.class_selector.spawn_only_prompt",
                ClientModEvents.openClassMenuKey.translatedKeyMessage
            )

            SelectionMode.CLASS,
            SelectionMode.EMBARK_POINTS -> Component.translatable(
                "message.class_selector.lock_in_prompt",
                ClientModEvents.openClassMenuKey.translatedKeyMessage
            )
        }

    private fun lockInReminderComponent(): Component =
        when (ClassSelectionState.selectionMode) {
            SelectionMode.NONE -> Component.translatable(
                "message.class_selector.spawn_only_reminder",
                ClientModEvents.openClassMenuKey.translatedKeyMessage
            )

            SelectionMode.CLASS,
            SelectionMode.EMBARK_POINTS -> Component.translatable(
                "message.class_selector.lock_in_reminder",
                ClientModEvents.openClassMenuKey.translatedKeyMessage
            )
        }
}
