package com.example.classselector.kit

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.fml.ModList
import net.minecraftforge.registries.ForgeRegistries

object KitApplicator {
    const val SELECTED_CLASS_TAG: String = "classselector:selected_class"

    fun hasSelectedClass(player: ServerPlayer): Boolean = player.persistentData.contains(SELECTED_CLASS_TAG)

    fun apply(player: ServerPlayer, kit: ClassKit) {
        player.inventory.clearContent()
        kit.items.forEach { giveItemToConfiguredSlot(player, it) }
        player.persistentData.putString(SELECTED_CLASS_TAG, kit.id)
    }

    private fun giveItemToConfiguredSlot(player: ServerPlayer, kitItem: KitItem) {
        val itemId = ResourceLocation.tryParse(kitItem.item) ?: return
        val item = ForgeRegistries.ITEMS.getValue(itemId) ?: return
        if (item == Items.AIR) return

        val stack = ItemStack(item, kitItem.count.coerceAtLeast(1))
        when (val slotTarget = KitSlot.parse(kitItem.slot)) {
            KitSlotTarget.Inventory -> player.addItem(stack)
            KitSlotTarget.Offhand -> equipEquipmentSlot(player, stack, EquipmentSlot.OFFHAND)
            is KitSlotTarget.Armor -> equipEquipmentSlot(player, stack, slotTarget.slot)
            is KitSlotTarget.Curio -> equipCurioSlot(player, stack, slotTarget.identifier)
            is KitSlotTarget.Unknown -> player.addItem(stack)
        }
    }

    private fun equipEquipmentSlot(player: ServerPlayer, stack: ItemStack, target: EquipmentSlot) {
        if (!player.getItemBySlot(target).isEmpty) {
            player.addItem(stack)
            return
        }

        player.setItemSlot(target, stack)
    }

    private fun equipCurioSlot(player: ServerPlayer, stack: ItemStack, curioIdentifier: String) {
        if (!ModList.get().isLoaded("curios")) {
            player.addItem(stack)
            return
        }

        val handler = resolveCurioStacksHandler(player, curioIdentifier)
        if (handler == null) {
            player.addItem(stack)
            return
        }

        val slots = handler.javaClass.getMethod("getSlots").invoke(handler) as? Int ?: run {
            player.addItem(stack)
            return
        }
        val dynamicHandler = handler.javaClass.getMethod("getStacks").invoke(handler) ?: run {
            player.addItem(stack)
            return
        }
        val getStackInSlot = dynamicHandler.javaClass.getMethod("getStackInSlot", Int::class.javaPrimitiveType)
        val setStackInSlot = dynamicHandler.javaClass.getMethod("setStackInSlot", Int::class.javaPrimitiveType, ItemStack::class.java)

        for (index in 0 until slots) {
            val currentStack = getStackInSlot.invoke(dynamicHandler, index) as? ItemStack ?: continue
            if (currentStack.isEmpty) {
                setStackInSlot.invoke(dynamicHandler, index, stack.copyWithCount(1))
                val remainder = stack.count - 1
                if (remainder > 0) {
                    player.addItem(stack.copyWithCount(remainder))
                }
                return
            }
        }

        player.addItem(stack)
    }

    private fun resolveCurioStacksHandler(player: ServerPlayer, curioIdentifier: String): Any? = runCatching {
        val curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi")
        val getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity::class.java)
        val inventoryOptional = getCuriosInventory.invoke(null, player) ?: return null
        val resolveMethod = inventoryOptional.javaClass.getMethod("resolve")
        val resolvedInventory = resolveMethod.invoke(inventoryOptional)
        val orElseMethod = resolvedInventory.javaClass.getMethod("orElse", Any::class.java)
        val inventoryHandler = orElseMethod.invoke(resolvedInventory, null) ?: return null
        val getStacksHandler = inventoryHandler.javaClass.getMethod("getStacksHandler", String::class.java)
        val stacksHandlerOptional = getStacksHandler.invoke(inventoryHandler, curioIdentifier) ?: return null
        val handlerOrElse = stacksHandlerOptional.javaClass.getMethod("orElse", Any::class.java)
        handlerOrElse.invoke(stacksHandlerOptional, null)
    }.getOrNull()
}
