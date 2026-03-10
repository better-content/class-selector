package com.example.classselector.network

import com.example.classselector.client.ClassSelectionState
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class RequestOpenMenuPacket {
    companion object {
        fun encode(@Suppress("UNUSED_PARAMETER") packet: RequestOpenMenuPacket, @Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf) {}
        fun decode(@Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf): RequestOpenMenuPacket = RequestOpenMenuPacket()
        fun handle(@Suppress("UNUSED_PARAMETER") packet: RequestOpenMenuPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork { ClassSelectionState.promptOpen = true }
            ctx.packetHandled = true
        }
    }
}
