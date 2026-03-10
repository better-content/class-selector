package com.example.classselector.client

import com.example.classselector.kit.ClassKit
import com.example.classselector.network.ChooseClassPacket
import com.example.classselector.network.ClassSelectorNetwork
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ClassSelectionScreen(private val kits: List<ClassKit>) : Screen(Component.literal("Select Class")) {
    override fun init() {
        var y = 40
        kits.forEach { kit ->
            addRenderableWidget(Button.builder(Component.literal("Choose ${kit.title}")) {
                ClassSelectorNetwork.CHANNEL.sendToServer(ChooseClassPacket(kit.id))
                onClose()
            }.pos(this.width / 2 - 100, y).size(200, 20).build())
            y += 26
        }
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(gui)
        gui.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)
        var y = 42
        kits.forEach { kit ->
            gui.drawString(font, kit.blurb, width / 2 + 110, y + 6, 0xAAAAAA)
            gui.drawWordWrap(font, Component.literal(kit.description), width / 2 - 220, y + 22, 440, 0xDDDDDD)
            y += 80
        }
        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false
}
