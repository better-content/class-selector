package com.bettercontent.classselector.network

import com.bettercontent.classselector.client.ClassSelectionState
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class RequestOpenMenuPacket {
    companion object {
        fun encode(@Suppress("UNUSED_PARAMETER") packet: RequestOpenMenuPacket, @Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf) {}
        fun decode(@Suppress("UNUSED_PARAMETER") buf: FriendlyByteBuf): RequestOpenMenuPacket = RequestOpenMenuPacket()
        fun handle(@Suppress("UNUSED_PARAMETER") packet: RequestOpenMenuPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                if (!ClassSelectionState.activeInCurrentWorld) {
                    return@enqueueWork
                }
                ClassSelectionState.selectionRequired = true
                ClassSelectionState.promptOpen = true
                ClassSelectionState.reminderCooldownTicks = 0
            }
            ctx.packetHandled = true
        }
    }
}
