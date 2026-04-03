package com.example.classselector.network

import com.example.classselector.respawn.RespawnHubService
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SuggestRespawnHubPacket {
    companion object {
        fun encode(@Suppress("UNUSED_PARAMETER") packet: SuggestRespawnHubPacket, @Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf) {}
        fun decode(@Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf): SuggestRespawnHubPacket = SuggestRespawnHubPacket()

        fun handle(@Suppress("UNUSED_PARAMETER") packet: SuggestRespawnHubPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                RespawnHubService.suggestHere(player)
            }
            ctx.packetHandled = true
        }
    }
}
