package com.example.classselector.network

import com.example.classselector.respawn.RespawnHubService
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class RequestRespawnVotingSyncPacket {
    companion object {
        fun encode(@Suppress("UNUSED_PARAMETER") packet: RequestRespawnVotingSyncPacket, @Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf) {}
        fun decode(@Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf): RequestRespawnVotingSyncPacket = RequestRespawnVotingSyncPacket()

        fun handle(@Suppress("UNUSED_PARAMETER") packet: RequestRespawnVotingSyncPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                RespawnHubService.syncVotingState(player)
            }
            ctx.packetHandled = true
        }
    }
}
