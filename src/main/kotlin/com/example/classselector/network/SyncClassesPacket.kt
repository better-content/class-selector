package com.example.classselector.network

import com.example.classselector.client.ClassSelectionState
import com.example.classselector.client.RespawnVotingSnapshot
import com.example.classselector.client.RespawnVotingState
import com.example.classselector.kit.ClassKit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SyncClassesPacket(private val activeInCurrentWorld: Boolean, private val jsonPayload: String) {
    companion object {
        private val gson = Gson()
        private val type = object : TypeToken<List<ClassKit>>() {}.type

        fun fromKits(activeInCurrentWorld: Boolean, kits: List<ClassKit>) =
            SyncClassesPacket(activeInCurrentWorld, gson.toJson(kits))

        fun encode(packet: SyncClassesPacket, buf: FriendlyByteBuf) {
            buf.writeBoolean(packet.activeInCurrentWorld)
            buf.writeUtf(packet.jsonPayload)
        }

        fun decode(buf: FriendlyByteBuf): SyncClassesPacket = SyncClassesPacket(
            buf.readBoolean(),
            buf.readUtf(32767)
        )

        fun handle(packet: SyncClassesPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                ClassSelectionState.activeInCurrentWorld = packet.activeInCurrentWorld
                ClassSelectionState.kits = runCatching { gson.fromJson<List<ClassKit>>(packet.jsonPayload, type) }
                    .getOrDefault(emptyList())
                if (!packet.activeInCurrentWorld) {
                    ClassSelectionState.promptOpen = false
                    ClassSelectionState.selectionRequired = false
                    ClassSelectionState.reminderCooldownTicks = 0
                    RespawnVotingState.snapshot = RespawnVotingSnapshot(false, false, 0, null, emptyList())
                }
            }
            ctx.packetHandled = true
        }
    }
}
