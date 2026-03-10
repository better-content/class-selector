package com.example.classselector.network

import com.example.classselector.ClassSelectorMod
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object ClassSelectorNetwork {
    private const val PROTOCOL = "1"
    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(ClassSelectorMod.MOD_ID, "main"),
        { PROTOCOL },
        PROTOCOL::equals,
        PROTOCOL::equals
    )

    fun register() {
        var id = 0
        CHANNEL.registerMessage(id++, RequestOpenMenuPacket::class.java, RequestOpenMenuPacket::encode, RequestOpenMenuPacket::decode, RequestOpenMenuPacket::handle)
        CHANNEL.registerMessage(id++, ChooseClassPacket::class.java, ChooseClassPacket::encode, ChooseClassPacket::decode, ChooseClassPacket::handle)
        CHANNEL.registerMessage(id, SyncClassesPacket::class.java, SyncClassesPacket::encode, SyncClassesPacket::decode, SyncClassesPacket::handle)
    }
}
