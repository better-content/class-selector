package com.example.classselector.network

import com.example.classselector.client.ClassSelectionState
import com.example.classselector.kit.ClassKit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

class SyncClassesPacket(private val jsonPayload: String) {
    companion object {
        private val gson = Gson()
        private val type = object : TypeToken<List<ClassKit>>() {}.type

        fun fromKits(kits: List<ClassKit>) = SyncClassesPacket(gson.toJson(kits))

        fun encode(packet: SyncClassesPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.jsonPayload)
        }

        fun decode(buf: FriendlyByteBuf): SyncClassesPacket = SyncClassesPacket(buf.readUtf(32767))

        fun handle(packet: SyncClassesPacket, context: Supplier<NetworkEvent.Context>) {
            val ctx = context.get()
            ctx.enqueueWork {
                ClassSelectionState.kits = runCatching { gson.fromJson<List<ClassKit>>(packet.jsonPayload, type) }
                    .getOrDefault(emptyList())
            }
            ctx.packetHandled = true
        }
    }
}
