package com.example.classselector.network

import com.example.classselector.client.RespawnVotingSnapshot
import com.example.classselector.client.RespawnVotingState
import com.google.gson.Gson
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SyncRespawnVotingPacket(private val jsonPayload: String) {
    companion object {
        private val gson = Gson()

        fun fromSnapshot(snapshot: RespawnVotingSnapshot) = SyncRespawnVotingPacket(gson.toJson(snapshot))

        fun encode(packet: SyncRespawnVotingPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.jsonPayload)
        }

        fun decode(buf: FriendlyByteBuf): SyncRespawnVotingPacket = SyncRespawnVotingPacket(buf.readUtf(32767))

        fun handle(packet: SyncRespawnVotingPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                RespawnVotingState.snapshot = runCatching {
                    gson.fromJson(packet.jsonPayload, RespawnVotingSnapshot::class.java)
                }.getOrElse { RespawnVotingSnapshot(false, false, 0, null, emptyList()) }
            }
            ctx.packetHandled = true
        }
    }
}
