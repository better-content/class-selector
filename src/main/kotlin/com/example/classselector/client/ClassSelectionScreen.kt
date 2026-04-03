package com.example.classselector.client

import com.example.classselector.kit.ClassKit
import com.example.classselector.kit.KitItem
import com.example.classselector.client.ClassSelectionState
import com.example.classselector.network.ChooseClassPacket
import com.example.classselector.network.ClassSelectorNetwork
import com.mojang.authlib.GameProfile
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.registries.ForgeRegistries
import java.util.UUID

class ClassSelectionScreen(private val kits: List<ClassKit>) : Screen(Component.literal("Select Class")) {
    private var selectedKitIndex: Int = 0
    private val classButtons: MutableList<Button> = mutableListOf()
    private var selectClassButton: Button? = null
    private var respawnVotingButton: Button? = null
    private var hoveredItem: ItemStack = ItemStack.EMPTY

    override fun init() {
        classButtons.clear()

        if (kits.isEmpty()) {
            selectClassButton = null
            return
        }

        selectedKitIndex = selectedKitIndex.coerceIn(0, kits.lastIndex)

        val totalLayoutWidth = 560
        val listColumnWidth = 140
        val middlePanelWidth = 260
        val layoutLeft = (width - totalLayoutWidth) / 2
        val leftColumnX = layoutLeft
        val middlePanelX = layoutLeft + listColumnWidth + 20
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
                ClassSelectionState.selectionRequired = false
                ClassSelectionState.promptOpen = false
                ClassSelectionState.reminderCooldownTicks = 0
                ClassSelectorNetwork.CHANNEL.sendToServer(ChooseClassPacket(selected.id))
                onClose()
            }.pos(middlePanelX + middlePanelWidth - 130, height - 42).size(130, 20).build()
        )

        respawnVotingButton = addRenderableWidget(
            Button.builder(Component.literal("Respawn Voting")) {
                minecraft?.setScreen(RespawnVotingScreen())
            }.pos(middlePanelX, height - 42).size(140, 20).build()
        )

        refreshButtonState()
    }

    private fun refreshButtonState() {
        classButtons.forEachIndexed { index, button ->
            button.active = index != selectedKitIndex
        }
        selectClassButton?.active = ClassSelectionState.activeInCurrentWorld && kits.isNotEmpty()
        respawnVotingButton?.active = ClassSelectionState.activeInCurrentWorld && RespawnVotingState.snapshot.canVote
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(gui)
        hoveredItem = ItemStack.EMPTY
        gui.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF)

        if (kits.isEmpty()) {
            gui.drawCenteredString(font, Component.literal("No classes available."), width / 2, height / 2 - 10, 0xFF7777)
            gui.drawCenteredString(font, Component.literal("Ask an admin to configure class kits."), width / 2, height / 2 + 4, 0xAAAAAA)
            super.render(gui, mouseX, mouseY, partialTick)
            return
        }

        val selected = kits[selectedKitIndex]

        val totalLayoutWidth = 560
        val listColumnWidth = 140
        val middlePanelWidth = 260
        val layoutLeft = (width - totalLayoutWidth) / 2
        val panelX = layoutLeft + listColumnWidth + 20
        val panelY = 36
        val panelWidth = middlePanelWidth
        val panelHeight = height - 90
        val previewCenterX = panelX + panelWidth + 110
        val previewBaseY = panelY + 210

        gui.fill(panelX - 8, panelY - 8, panelX + panelWidth, panelY + panelHeight, 0x66000000)

        gui.drawString(font, Component.literal(selected.title), panelX, panelY, 0xFFFFFF)
        gui.drawString(font, Component.literal(selected.blurb), panelX, panelY + 14, 0xB0B0B0)

        gui.drawWordWrap(font, Component.literal(selected.description), panelX, panelY + 32, panelWidth - 8, 0xDDDDDD)
        gui.drawWordWrap(
            font,
            Component.literal(
                if (RespawnVotingState.snapshot.canVote)
                    "Press Esc to close this screen and survey the world in spectator before choosing a class start location. Press V for respawn voting."
                else
                    "Press Esc to close this screen and survey the world in spectator before choosing a class start location."
            ),
            panelX,
            panelY + 72,
            panelWidth - 8,
            0xE6D28C
        )

        buildPreviewPlayer(selected)?.let { previewPlayer ->
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                gui,
                previewCenterX,
                previewBaseY,
                52,
                (previewCenterX - mouseX).toFloat(),
                (previewBaseY - 44 - mouseY).toFloat(),
                previewPlayer
            )
        }

        gui.drawString(font, Component.literal("Kit items"), panelX, panelY + 118, 0xFFFFFF)
        renderKitItems(gui, selected, panelX, panelY + 132, mouseX, mouseY)

        hoveredItem.takeIf { !it.isEmpty }?.let {
            gui.renderTooltip(font, it, mouseX, mouseY)
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    private fun renderKitItems(gui: GuiGraphics, kit: ClassKit, startX: Int, startY: Int, mouseX: Int, mouseY: Int) {
        val displayItems = kit.items.mapNotNull(::toDisplayEntry)
        val columns = 4
        val cellWidth = 30
        val cellHeight = 28

        displayItems.forEachIndexed { index, entry ->
            val x = startX + (index % columns) * cellWidth
            val y = startY + (index / columns) * cellHeight

            gui.renderFakeItem(entry.stack, x, y)
            gui.renderItemDecorations(font, entry.stack, x, y)

            val slotLabel = entry.slotLabel
            if (slotLabel != null) {
                gui.drawCenteredString(font, Component.literal(slotLabel), x + 8, y + 18, 0x9A9A9A)
            }

            if (mouseX in x until (x + 16) && mouseY in y until (y + 16)) {
                hoveredItem = entry.stack
            }
        }

        val hiddenCount = kit.items.size - displayItems.size
        if (hiddenCount > 0) {
            gui.drawString(font, Component.literal("$hiddenCount invalid item${if (hiddenCount == 1) "" else "s"}"), startX, startY + ((displayItems.size + columns - 1) / columns) * cellHeight, 0xC07070)
        }
    }

    private fun buildPreviewPlayer(kit: ClassKit): net.minecraft.client.player.RemotePlayer? {
        val mc = minecraft ?: return null
        val level = mc.level ?: return null
        val localPlayer = mc.player
        val baseProfile = localPlayer?.gameProfile
        val previewProfile = GameProfile(UUID.nameUUIDFromBytes("classselector-preview".toByteArray()), baseProfile?.name ?: "Preview")
        val preview = net.minecraft.client.player.RemotePlayer(level, previewProfile)

        preview.setPos(0.0, 0.0, 0.0)
        preview.setOnGround(true)
        preview.setInvisible(false)
        preview.setPose(Pose.STANDING)
        preview.setXRot(0f)
        preview.setYRot(0f)
        preview.setYHeadRot(0f)
        preview.setYBodyRot(0f)

        kit.items.forEachIndexed { index, item ->
            equipPreviewItem(preview, item, index)
        }
        return preview
    }

    private fun equipPreviewItem(player: net.minecraft.client.player.RemotePlayer, kitItem: KitItem, index: Int) {
        val stack = resolveStack(kitItem) ?: return
        val slot = kitItem.slot?.lowercase()

        when {
            slot == null || slot == "inventory" -> {
                if (index == firstHandItemIndex()) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, stack)
                }
            }
            slot == "offhand" -> player.setItemSlot(EquipmentSlot.OFFHAND, stack)
            slot.startsWith("armor:") -> {
                val equipmentSlot = when (slot.removePrefix("armor:")) {
                    "head", "helmet" -> EquipmentSlot.HEAD
                    "chest", "chestplate" -> EquipmentSlot.CHEST
                    "legs", "leggings" -> EquipmentSlot.LEGS
                    "feet", "boots" -> EquipmentSlot.FEET
                    else -> null
                }
                if (equipmentSlot != null) {
                    player.setItemSlot(equipmentSlot, stack)
                }
            }
        }
    }

    private fun firstHandItemIndex(): Int = kits.getOrNull(selectedKitIndex)
        ?.items
        ?.indexOfFirst { item ->
            val slot = item.slot?.lowercase()
            slot == null || slot == "inventory"
        }
        ?: -1

    private fun toDisplayEntry(kitItem: KitItem): DisplayItemEntry? {
        val stack = resolveStack(kitItem) ?: return null
        return DisplayItemEntry(stack, toSlotLabel(kitItem.slot))
    }

    private fun resolveStack(kitItem: KitItem): ItemStack? {
        val itemId = ResourceLocation.tryParse(kitItem.item) ?: return null
        val item = ForgeRegistries.ITEMS.getValue(itemId) ?: return null
        if (item == Items.AIR) return null
        return ItemStack(item, kitItem.count.coerceAtLeast(1))
    }

    private fun toSlotLabel(slot: String?): String? = when (slot?.lowercase()) {
        null, "inventory" -> null
        "offhand" -> "Off"
        "armor:head", "armor:helmet" -> "Head"
        "armor:chest", "armor:chestplate" -> "Chest"
        "armor:legs", "armor:leggings" -> "Legs"
        "armor:feet", "armor:boots" -> "Feet"
        else -> slot.substringAfter(':').replaceFirstChar { it.titlecase() }
    }

    private data class DisplayItemEntry(val stack: ItemStack, val slotLabel: String?)
}
