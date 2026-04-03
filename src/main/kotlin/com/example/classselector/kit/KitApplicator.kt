package com.example.classselector.kit

import com.example.classselector.respawn.RespawnHubService
import net.minecraft.network.chat.Component
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
        RespawnHubService.onVotingEligibilityChanged(player.server)
        val released = RespawnHubService.tryReleasePlayerFromSpectator(player)
        if (released) {
            player.sendSystemMessage(Component.literal("Class selected: ${kit.title}"))
        } else {
            player.sendSystemMessage(Component.literal("Class selected: ${kit.title}. Waiting for a finalized spawn location."))
        }
    }

    private fun giveItemToConfiguredSlot(player: ServerPlayer, kitItem: KitItem) {
        val itemId = ResourceLocation.tryParse(kitItem.item) ?: return
        val item = ForgeRegistries.ITEMS.getValue(itemId) ?: return
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
