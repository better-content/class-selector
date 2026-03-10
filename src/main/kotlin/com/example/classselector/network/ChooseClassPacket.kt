package com.example.classselector.network

import com.example.classselector.kit.ClassKitRepository
import com.example.classselector.kit.KitApplicator
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class ChooseClassPacket(private val classId: String) {
    companion object {
        fun encode(packet: ChooseClassPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.classId)
        }

        fun decode(buf: FriendlyByteBuf): ChooseClassPacket = ChooseClassPacket(buf.readUtf())

        fun handle(packet: ChooseClassPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                val player = ctx.sender ?: return@enqueueWork
                if (KitApplicator.hasSelectedClass(player)) return@enqueueWork
                val kit = ClassKitRepository.get().firstOrNull { it.id == packet.classId } ?: return@enqueueWork
                KitApplicator.apply(player, kit)
            }
            ctx.packetHandled = true
        }
    }
}
