package com.example.classselector.network

import com.example.classselector.client.ClassSelectionState
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class RequestOpenMenuPacket {
    companion object {
        fun encode(packet: RequestOpenMenuPacket, buf: FriendlyByteBuf) {}
        fun decode(buf: FriendlyByteBuf): RequestOpenMenuPacket = RequestOpenMenuPacket()
        fun handle(packet: RequestOpenMenuPacket, context: Supplier<NetworkEvent.Context>) {
            context.get().enqueueWork { ClassSelectionState.promptOpen = true }
            context.get().packetHandled = true
        }
    }
}
