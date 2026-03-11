package com.example.classselector.kit

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraftforge.registries.ForgeRegistries
import top.theillusivec4.curios.api.CuriosApi

object KitApplicator {
    const val SELECTED_CLASS_TAG: String = "classselector:selected_class"

    fun hasSelectedClass(player: ServerPlayer): Boolean = player.persistentData.contains(SELECTED_CLASS_TAG)

    fun apply(player: ServerPlayer, kit: ClassKit) {
        player.inventory.clearContent()
        kit.items.forEach { giveItemToConfiguredSlot(player, it) }
        player.persistentData.putString(SELECTED_CLASS_TAG, kit.id)
        player.setGameMode(GameType.SURVIVAL)
        player.sendSystemMessage(Component.literal("Class selected: ${kit.title}"))
    }

    private fun giveItemToConfiguredSlot(player: ServerPlayer, kitItem: KitItem) {
        val item = ForgeRegistries.ITEMS.getValue(ResourceLocation(kitItem.item)) ?: return
        if (item == Items.AIR) return

        val stack = ItemStack(item, kitItem.count.coerceAtLeast(1))
        val slot = kitItem.slot?.lowercase()

        when {
            slot == null || slot == "inventory" -> player.addItem(stack)
            slot.startsWith("armor:") -> equipArmorSlot(player, stack, slot.removePrefix("armor:"))
            slot.startsWith("curio:") -> equipCurioSlot(player, stack, slot.removePrefix("curio:"))
            else -> player.addItem(stack)
        }
    }

    private fun equipArmorSlot(player: ServerPlayer, stack: ItemStack, armorSlot: String) {
        val target = when (armorSlot) {
            "head", "helmet" -> EquipmentSlot.HEAD
            "chest", "chestplate" -> EquipmentSlot.CHEST
            "legs", "leggings" -> EquipmentSlot.LEGS
            "feet", "boots" -> EquipmentSlot.FEET
            else -> null
        }

        if (target == null || !player.getItemBySlot(target).isEmpty) {
            player.addItem(stack)
            return
        }

        player.setItemSlot(target, stack)
    }

    private fun equipCurioSlot(player: ServerPlayer, stack: ItemStack, curioIdentifier: String) {
        val inventory = CuriosApi.getCuriosInventory(player)
        if (!inventory.isPresent) {
            player.addItem(stack)
            return
        }

        val handler = inventory.resolve().orElse(null)?.getStacksHandler(curioIdentifier)?.orElse(null)
        if (handler == null) {
            player.addItem(stack)
            return
        }

        for (index in 0 until handler.slots) {
            if (handler.stacks.getStackInSlot(index).isEmpty) {
                handler.stacks.setStackInSlot(index, stack.copyWithCount(1))
                val remainder = stack.count - 1
                if (remainder > 0) {
                    player.addItem(stack.copyWithCount(remainder))
                }
                return
            }
        }

        player.addItem(stack)
    }
}
