package com.example.classselector.network

import com.example.classselector.ClassSelectorScope
import com.example.classselector.integration.OnboardingIntegration
import com.example.classselector.kit.ClassKitRepository
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
    private val classId: String,
    private val dimensionId: String,
    private val x: Int,
    private val y: Int,
    private val z: Int
) {
    companion object {
        const val MAX_DIMENSION_ID_LENGTH: Int = 128

        fun encode(packet: FinalizeSelectionPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.classId)
            buf.writeUtf(packet.dimensionId)
            buf.writeInt(packet.x)
            buf.writeInt(packet.y)
            buf.writeInt(packet.z)
        }

        fun decode(buf: FriendlyByteBuf): FinalizeSelectionPacket = FinalizeSelectionPacket(
            buf.readUtf(),
            buf.readUtf(MAX_DIMENSION_ID_LENGTH),
            buf.readInt(),
            buf.readInt(),
            buf.readInt()
        )

        fun handle(packet: FinalizeSelectionPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                if (!ClassSelectorScope.isActiveIn(player.server)) return@enqueueWork
                if (OnboardingIntegration.hasCompletedOnboarding(player)) return@enqueueWork
                if (KitApplicator.hasSelectedClass(player)) return@enqueueWork

                val kit = ClassKitRepository.get().firstOrNull { it.id == packet.classId }
                if (kit == null) {
                    player.sendSystemMessage(Component.literal("Invalid class selection."))
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
                KitApplicator.apply(player, kit)
                OnboardingIntegration.finalizeOnboarding(player, kit.id, spawnId)
                PersonalRespawnService.releasePlayerFromSpectator(player)
                val message = when {
                    preparedPoint.sitePrepared && preparedPoint.locationAdjusted ->
                        "Class selected: ${kit.title}. Permanent respawn prepared at ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                    preparedPoint.sitePrepared ->
                        "Class selected: ${kit.title}. Permanent respawn prepared in place at ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                    preparedPoint.locationAdjusted ->
                        "Class selected: ${kit.title}. Permanent respawn set to ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                    else ->
                        "Class selected: ${kit.title}. Permanent respawn set to ${resolvedPoint.dim} ${resolvedPoint.x} ${resolvedPoint.y} ${resolvedPoint.z}."
                }
                player.sendSystemMessage(Component.literal(message))
            }
            ctx.packetHandled = true
        }
    }
}
