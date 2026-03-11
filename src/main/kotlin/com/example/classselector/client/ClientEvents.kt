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

    @SubscribeEvent
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        val key = KeyMapping("key.classselector.open_menu", GLFW.GLFW_KEY_K, "key.categories.gameplay")
        openClassMenuKey = key
        event.register(key)
    }
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID, value = [Dist.CLIENT])
object ClientForgeEvents {
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()

        if (ClientModEvents.openClassMenuKey?.consumeClick() == true) {
            if (ClassSelectionState.kits.isNotEmpty()) {
                mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            } else {
                mc.player?.sendSystemMessage(Component.literal("Class kits are still syncing."))
            }
        }

        if (ClassSelectionState.promptOpen && mc.screen == null) {
            ClassSelectionState.promptOpen = false
            mc.player?.sendSystemMessage(Component.literal("Press K to select your class."))
            if (ClassSelectionState.kits.isNotEmpty()) {
                mc.setScreen(ClassSelectionScreen(ClassSelectionState.kits))
            }
        }
    }
}
