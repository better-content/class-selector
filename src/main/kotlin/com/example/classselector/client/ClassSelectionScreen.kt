package com.example.classselector.client

import com.example.classselector.kit.ClassKit
import com.example.classselector.network.ChooseClassPacket
import com.example.classselector.network.ClassSelectorNetwork
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component

class ClassSelectionScreen(private val kits: List<ClassKit>) : Screen(Component.literal("Select Class")) {
    private var selectedKitIndex: Int = 0
    private val classButtons: MutableList<Button> = mutableListOf()
    private var selectClassButton: Button? = null

    override fun init() {
        classButtons.clear()

        if (kits.isEmpty()) {
            selectClassButton = null
            return
        }

        selectedKitIndex = selectedKitIndex.coerceIn(0, kits.lastIndex)

        val leftColumnX = width / 2 - 210
        val listTop = 36
        val listButtonWidth = 140
        val listButtonHeight = 20

        kits.forEachIndexed { index, kit ->
            val y = listTop + index * 24
            val button = addRenderableWidget(
                Button.builder(Component.literal(kit.title)) {
                    selectedKitIndex = index
                    refreshButtonState()
                }.pos(leftColumnX, y).size(listButtonWidth, listButtonHeight).build()
            )
            classButtons += button
        }

        selectClassButton = addRenderableWidget(
            Button.builder(Component.literal("Select Class")) {
                val selected = kits.getOrNull(selectedKitIndex) ?: return@builder
                ClassSelectorNetwork.CHANNEL.sendToServer(ChooseClassPacket(selected.id))
                onClose()
            }.pos(width / 2 + 70, height - 42).size(130, 20).build()
        )

        refreshButtonState()
    }

    private fun refreshButtonState() {
        classButtons.forEachIndexed { index, button ->
            button.active = index != selectedKitIndex
        }
        selectClassButton?.active = kits.isNotEmpty()
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(gui)
        gui.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)

        if (kits.isEmpty()) {
            gui.drawCenteredString(font, Component.literal("No classes available."), width / 2, height / 2 - 10, 0xFF7777)
            gui.drawCenteredString(font, Component.literal("Ask an admin to configure class kits."), width / 2, height / 2 + 4, 0xAAAAAA)
            super.render(gui, mouseX, mouseY, partialTick)
            return
        }

        val selected = kits[selectedKitIndex]

        val panelX = width / 2 - 50
        val panelY = 36
        val panelWidth = 260
        val panelHeight = height - 90

        gui.fill(panelX - 8, panelY - 8, panelX + panelWidth, panelY + panelHeight, 0x66000000)

        gui.drawString(font, Component.literal(selected.title), panelX, panelY, 0xFFFFFF)
        gui.drawString(font, Component.literal(selected.blurb), panelX, panelY + 14, 0xB0B0B0)

        gui.drawWordWrap(font, Component.literal(selected.description), panelX, panelY + 32, panelWidth - 8, 0xDDDDDD)

        gui.drawString(font, Component.literal("Preview"), panelX, panelY + 90, 0xFFFFFF)
        minecraft?.player?.let { player ->
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                gui,
                panelX + 70,
                panelY + 210,
                50,
                (panelX + 70 - mouseX).toFloat(),
                (panelY + 170 - mouseY).toFloat(),
                player
            )
        }

        gui.drawString(font, Component.literal("Kit items"), panelX + 120, panelY + 90, 0xFFFFFF)
        var itemY = panelY + 104
        selected.items.take(8).forEach { item ->
            val label = "- ${item.item} x${item.count}" + (item.slot?.let { " (${it})" } ?: "")
            gui.drawString(font, Component.literal(label), panelX + 120, itemY, 0xCCCCCC)
            itemY += 12
        }
        if (selected.items.size > 8) {
            gui.drawString(font, Component.literal("... and ${selected.items.size - 8} more"), panelX + 120, itemY, 0x999999)
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false
}
