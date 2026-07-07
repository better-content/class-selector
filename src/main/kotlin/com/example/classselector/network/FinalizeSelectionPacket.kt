package com.example.classselector.network

import com.example.classselector.ClassSelectorScope
import com.example.classselector.embark.EmbarkPurchase
import com.example.classselector.embark.EmbarkPurchaseService
import com.example.classselector.embark.SelectionDataRepository
import com.example.classselector.embark.SelectionMode
import com.example.classselector.integration.OnboardingIntegration
import com.example.classselector.kit.KitApplicator
import com.example.classselector.network.FinalizeSelectionPacket.Companion.MAX_DIMENSION_ID_LENGTH
import com.example.classselector.respawn.PersonalRespawnPoint
import com.example.classselector.respawn.PersonalRespawnService
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class FinalizeSelectionPacket(
    private val selectionMode: String,
    private val classId: String,
    private val embarkPurchases: List<EmbarkPurchase>,
    private val dimensionId: String,
    private val x: Int,
    private val y: Int,
    private val z: Int
) {
    companion object {
        const val MAX_DIMENSION_ID_LENGTH: Int = 128
        private const val MAX_SELECTION_ID_LENGTH: Int = 128
        private const val MAX_PURCHASES: Int = 256

        fun spawnOnly(dimensionId: String, x: Int, y: Int, z: Int): FinalizeSelectionPacket =
            FinalizeSelectionPacket(
                selectionMode = SelectionMode.NONE.wireName,
                classId = "",
                embarkPurchases = emptyList(),
                dimensionId = dimensionId,
                x = x,
                y = y,
                z = z
            )

        fun classSelection(classId: String, dimensionId: String, x: Int, y: Int, z: Int): FinalizeSelectionPacket =
            FinalizeSelectionPacket(
                selectionMode = SelectionMode.CLASS.wireName,
                classId = classId,
                embarkPurchases = emptyList(),
                dimensionId = dimensionId,
                x = x,
                y = y,
                z = z
            )

        fun embarkSelection(
            purchases: List<EmbarkPurchase>,
            dimensionId: String,
            x: Int,
            y: Int,
            z: Int
        ): FinalizeSelectionPacket =
            FinalizeSelectionPacket(
                selectionMode = SelectionMode.EMBARK_POINTS.wireName,
                classId = EmbarkPurchaseService.SELECTION_ID,
                embarkPurchases = purchases,
                dimensionId = dimensionId,
                x = x,
                y = y,
                z = z
            )

        fun encode(packet: FinalizeSelectionPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.selectionMode)
            buf.writeUtf(packet.classId)
            buf.writeVarInt(packet.embarkPurchases.size)
            packet.embarkPurchases.forEach { purchase ->
                buf.writeUtf(purchase.itemId)
                buf.writeVarInt(purchase.quantity)
            }
            buf.writeUtf(packet.dimensionId)
            buf.writeInt(packet.x)
            buf.writeInt(packet.y)
            buf.writeInt(packet.z)
        }

        fun decode(buf: FriendlyByteBuf): FinalizeSelectionPacket {
            val selectionMode = buf.readUtf(64)
            val classId = buf.readUtf(MAX_SELECTION_ID_LENGTH)
            val purchaseCount = buf.readVarInt()
            require(purchaseCount in 0..MAX_PURCHASES) { "Invalid embark purchase count $purchaseCount" }
            val purchases = List(purchaseCount) {
                EmbarkPurchase(
                    itemId = buf.readUtf(MAX_SELECTION_ID_LENGTH),
                    quantity = buf.readVarInt()
                )
            }

            return FinalizeSelectionPacket(
                selectionMode = selectionMode,
                classId = classId,
                embarkPurchases = purchases,
                dimensionId = buf.readUtf(MAX_DIMENSION_ID_LENGTH),
                x = buf.readInt(),
                y = buf.readInt(),
                z = buf.readInt()
            )
        }

        fun handle(packet: FinalizeSelectionPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                if (!ClassSelectorScope.isActiveIn(player.server)) return@enqueueWork
                if (OnboardingIntegration.hasCompletedOnboarding(player)) return@enqueueWork

                val selectionData = SelectionDataRepository.getOrLoad()
                val requestedMode = runCatching { SelectionMode.parse(packet.selectionMode) }.getOrNull()
                if (requestedMode == null || requestedMode != selectionData.mode) {
                    player.sendSystemMessage(Component.literal("Starting selection mode changed. Open the menu and try again."))
                    return@enqueueWork
                }

                val location = ResourceLocation.tryParse(packet.dimensionId)
                if (location == null || player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location)) == null) {
                    player.sendSystemMessage(Component.literal("Invalid respawn dimension."))
                    return@enqueueWork
                }

                val requestedPoint = PersonalRespawnPoint(packet.dimensionId, packet.x, packet.y, packet.z)
                val preparedPoint = PersonalRespawnService.setRespawnPoint(player, requestedPoint)
                val resolvedPoint = preparedPoint.point
                val spawnId = OnboardingIntegration.buildSpawnId(resolvedPoint)

                val selectionName = when (selectionData.mode) {
                    SelectionMode.NONE -> {
                        OnboardingIntegration.finalizeOnboarding(player, "spawn_only", spawnId)
                        "Starting site locked"
                    }

                    SelectionMode.CLASS -> {
                        val kit = selectionData.kits.firstOrNull { it.id == packet.classId }
                        if (kit == null) {
                            player.sendSystemMessage(Component.literal("Invalid class selection."))
                            return@enqueueWork
                        }
                        KitApplicator.apply(player, kit)
                        OnboardingIntegration.finalizeOnboarding(player, kit.id, spawnId)
                        kit.title
                    }

                    SelectionMode.EMBARK_POINTS -> {
                        val purchaseResult = runCatching {
                            EmbarkPurchaseService.validate(selectionData.embarkSettings, packet.embarkPurchases)
                        }.getOrElse { error ->
                            player.sendSystemMessage(Component.literal(error.message ?: "Invalid embark purchases."))
                            return@enqueueWork
                        }
                        KitApplicator.applyItems(
                            player,
                            purchaseResult.selectedItems,
                            EmbarkPurchaseService.SELECTION_ID
                        )
                        OnboardingIntegration.finalizeOnboarding(
                            player,
                            EmbarkPurchaseService.SELECTION_ID,
                            spawnId
                        )
                        "Embark supplies (${purchaseResult.totalCost}/${selectionData.embarkSettings.pointQuota} points)"
                    }
                }

                PersonalRespawnService.releasePlayerFromSpectator(player)
                val message = when {
                    preparedPoint.sitePrepared && preparedPoint.locationAdjusted ->
                        "Starting loadout selected: $selectionName. Permanent respawn prepared at ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                    preparedPoint.sitePrepared ->
                        "Starting loadout selected: $selectionName. Permanent respawn prepared in place at ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                    preparedPoint.locationAdjusted ->
                        "Starting loadout selected: $selectionName. Permanent respawn set to ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                    else ->
                        "Starting loadout selected: $selectionName. Permanent respawn set to ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                }
                player.sendSystemMessage(Component.literal(message))
            }
            ctx.packetHandled = true
        }
    }
}
