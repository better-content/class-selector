package com.example.classselector.client

import com.example.classselector.kit.ClassKit
import com.example.classselector.kit.KitItem
import com.example.classselector.kit.KitSlot
import com.example.classselector.kit.KitSlotTarget
import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.FinalizeSelectionPacket
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
    private companion object {
        private const val PREVIEW_PROFILE_SEED = "classselector-preview"
    }

    private var selectedKitIndex: Int = 0
    private val classButtons: MutableList<Button> = mutableListOf()
    private var lockClassButton: Button? = null
    private var unlockClassButton: Button? = null
    private var lockRespawnButton: Button? = null
    private var clearRespawnButton: Button? = null
    private var beginButton: Button? = null
    private var hoveredItem: ItemStack = ItemStack.EMPTY

    override fun init() {
        classButtons.clear()

        if (kits.isEmpty()) {
            lockClassButton = null
            unlockClassButton = null
            lockRespawnButton = null
            clearRespawnButton = null
            beginButton = null
            return
        }

        ClassSelectionState.lockedClassId?.let { lockedId ->
            val lockedIndex = kits.indexOfFirst { it.id == lockedId }
            if (lockedIndex >= 0) {
                selectedKitIndex = lockedIndex
            }
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

        lockClassButton = addRenderableWidget(
            Button.builder(Component.literal("Lock Selected Class")) {
                val selected = kits.getOrNull(selectedKitIndex) ?: return@builder
                ClassSelectionState.lockedClassId = selected.id
                refreshButtonState()
            }.pos(middlePanelX, height - 68).size(150, 20).build()
        )

        unlockClassButton = addRenderableWidget(
            Button.builder(Component.literal("Unlock Class")) {
                ClassSelectionState.lockedClassId = null
                refreshButtonState()
            }.pos(middlePanelX + 160, height - 68).size(100, 20).build()
        )

        lockRespawnButton = addRenderableWidget(
            Button.builder(Component.literal("Lock Current Respawn")) {
                val player = minecraft?.player ?: return@builder
                val dim = player.level().dimension().location().toString()
                ClassSelectionState.lockedRespawn = PendingRespawnSelection(dim, player.blockX, player.blockY, player.blockZ)
                refreshButtonState()
            }.pos(middlePanelX, height - 42).size(150, 20).build()
        )

        clearRespawnButton = addRenderableWidget(
            Button.builder(Component.literal("Unlock Respawn")) {
                ClassSelectionState.lockedRespawn = null
                refreshButtonState()
            }.pos(middlePanelX + 160, height - 42).size(100, 20).build()
        )

        beginButton = addRenderableWidget(
            Button.builder(Component.literal("Begin")) {
                val classId = ClassSelectionState.lockedClassId ?: return@builder
                val respawn = ClassSelectionState.lockedRespawn ?: return@builder
                ClassSelectionState.selectionRequired = false
                ClassSelectionState.promptOpen = false
                ClassSelectionState.reminderCooldownTicks = 0
                ClassSelectorNetwork.CHANNEL.sendToServer(
                    FinalizeSelectionPacket(classId, respawn.dim, respawn.x, respawn.y, respawn.z)
                )
                onClose()
            }.pos(middlePanelX + middlePanelWidth - 90, height - 42).size(90, 20).build()
        )

        refreshButtonState()
    }

    private fun refreshButtonState() {
        val selected = kits.getOrNull(selectedKitIndex)
        val lockedClassId = ClassSelectionState.lockedClassId
        val lockedRespawn = ClassSelectionState.lockedRespawn
        classButtons.forEachIndexed { index, button ->
            button.active = index != selectedKitIndex
        }
        lockClassButton?.active = ClassSelectionState.activeInCurrentWorld && selected != null && selected.id != lockedClassId
        unlockClassButton?.active = ClassSelectionState.activeInCurrentWorld && lockedClassId != null
        lockRespawnButton?.active = ClassSelectionState.activeInCurrentWorld
        clearRespawnButton?.active = ClassSelectionState.activeInCurrentWorld && lockedRespawn != null
        beginButton?.active = ClassSelectionState.activeInCurrentWorld && lockedClassId != null && lockedRespawn != null
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
                "Lock your class and lock a respawn point separately. You can unlock either one and change it before pressing Begin."
            ),
            panelX,
            panelY + 72,
            panelWidth - 8,
            0xE6D28C
        )

        val lockedClass = kits.firstOrNull { it.id == ClassSelectionState.lockedClassId }
        gui.drawString(font, Component.literal("Locked class"), panelX, panelY + 104, 0xFFFFFF)
        gui.drawWordWrap(
            font,
            Component.literal(lockedClass?.title ?: "None"),
            panelX + 92,
            panelY + 104,
            panelWidth - 100,
            if (lockedClass != null) 0x9FE3A0 else 0xC8A0A0
        )

        val currentPlayer = minecraft?.player
        val currentDim = currentPlayer?.level()?.dimension()?.location()?.toString() ?: "unknown"
        val currentCoords = currentPlayer?.let { "${it.blockX} ${it.blockY} ${it.blockZ}" } ?: "unknown"
        gui.drawString(font, Component.literal("Current spot"), panelX, panelY + 122, 0xFFFFFF)
        gui.drawWordWrap(font, Component.literal(currentDim), panelX + 92, panelY + 122, panelWidth - 100, 0xA0A0A0)
        gui.drawString(font, Component.literal(currentCoords), panelX + 92, panelY + 136, 0xA0A0A0)

        val lockedRespawn = ClassSelectionState.lockedRespawn
        gui.drawString(font, Component.literal("Locked respawn"), panelX, panelY + 154, 0xFFFFFF)
        if (lockedRespawn == null) {
            gui.drawString(font, Component.literal("None"), panelX + 92, panelY + 154, 0xC8A0A0)
        } else {
            gui.drawWordWrap(font, Component.literal(lockedRespawn.dim), panelX + 92, panelY + 154, panelWidth - 100, 0x9FE3A0)
            gui.drawString(font, Component.literal("${lockedRespawn.x} ${lockedRespawn.y} ${lockedRespawn.z}"), panelX + 92, panelY + 168, 0x9FE3A0)
        }

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

        gui.drawString(font, Component.literal("Kit items"), panelX, panelY + 196, 0xFFFFFF)
        renderKitItems(gui, selected, panelX, panelY + 210, mouseX, mouseY)

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
        val previewProfile = GameProfile(UUID.nameUUIDFromBytes(PREVIEW_PROFILE_SEED.toByteArray()), baseProfile?.name ?: "Preview")
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
        when (val slotTarget = KitSlot.parse(kitItem.slot)) {
            KitSlotTarget.Inventory -> {
                if (index == firstHandItemIndex()) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, stack)
                }
            }
            KitSlotTarget.Offhand -> player.setItemSlot(EquipmentSlot.OFFHAND, stack)
            is KitSlotTarget.Armor -> player.setItemSlot(slotTarget.slot, stack)
            is KitSlotTarget.Curio -> {}
            is KitSlotTarget.Unknown -> {}
        }
    }

    private fun firstHandItemIndex(): Int = kits.getOrNull(selectedKitIndex)
        ?.items
        ?.indexOfFirst { item ->
            KitSlot.parse(item.slot) == KitSlotTarget.Inventory
        }
        ?: -1

    private fun toDisplayEntry(kitItem: KitItem): DisplayItemEntry? {
        val stack = resolveStack(kitItem) ?: return null
        return DisplayItemEntry(stack, KitSlot.label(kitItem.slot))
    }

    private fun resolveStack(kitItem: KitItem): ItemStack? {
        val itemId = ResourceLocation.tryParse(kitItem.item) ?: return null
        val item = ForgeRegistries.ITEMS.getValue(itemId) ?: return null
        if (item == Items.AIR) return null
        return ItemStack(item, kitItem.count.coerceAtLeast(1))
    }

    private data class DisplayItemEntry(val stack: ItemStack, val slotLabel: String?)
}
