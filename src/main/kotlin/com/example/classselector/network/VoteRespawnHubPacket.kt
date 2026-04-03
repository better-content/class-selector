package com.example.classselector.network

import com.example.classselector.respawn.RespawnHubService
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class VoteRespawnHubPacket(private val proposalId: Int) {
    companion object {
        fun encode(packet: VoteRespawnHubPacket, buf: FriendlyByteBuf) {
            buf.writeInt(packet.proposalId)
        }

        fun decode(buf: FriendlyByteBuf): VoteRespawnHubPacket = VoteRespawnHubPacket(buf.readInt())

        fun handle(packet: VoteRespawnHubPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                RespawnHubService.castVote(player, packet.proposalId)
            }
            ctx.packetHandled = true
        }
    }
}
