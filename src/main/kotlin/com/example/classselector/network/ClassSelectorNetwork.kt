package com.example.classselector.network

import com.example.classselector.ClassSelectorMod
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object ClassSelectorNetwork {
    private const val PROTOCOL = "1"
    val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(ClassSelectorMod.MOD_ID, "main"),
        { PROTOCOL },
        PROTOCOL::equals,
        PROTOCOL::equals
    )

    fun register() {
        var id = 0
        CHANNEL.messageBuilder(RequestOpenMenuPacket::class.java, id++)
            .encoder(RequestOpenMenuPacket::encode)
            .decoder(RequestOpenMenuPacket::decode)
            .consumerMainThread(RequestOpenMenuPacket::handle)
            .add()
        CHANNEL.messageBuilder(ChooseClassPacket::class.java, id++)
            .encoder(ChooseClassPacket::encode)
            .decoder(ChooseClassPacket::decode)
            .consumerMainThread(ChooseClassPacket::handle)
            .add()
        CHANNEL.messageBuilder(SyncClassesPacket::class.java, id)
            .encoder(SyncClassesPacket::encode)
            .decoder(SyncClassesPacket::decode)
            .consumerMainThread(SyncClassesPacket::handle)
            .add()
        CHANNEL.messageBuilder(SyncRespawnVotingPacket::class.java, ++id)
            .encoder(SyncRespawnVotingPacket::encode)
            .decoder(SyncRespawnVotingPacket::decode)
            .consumerMainThread(SyncRespawnVotingPacket::handle)
            .add()
        CHANNEL.messageBuilder(SuggestRespawnHubPacket::class.java, ++id)
            .encoder(SuggestRespawnHubPacket::encode)
            .decoder(SuggestRespawnHubPacket::decode)
            .consumerMainThread(SuggestRespawnHubPacket::handle)
            .add()
        CHANNEL.messageBuilder(VoteRespawnHubPacket::class.java, ++id)
            .encoder(VoteRespawnHubPacket::encode)
            .decoder(VoteRespawnHubPacket::decode)
            .consumerMainThread(VoteRespawnHubPacket::handle)
            .add()
        CHANNEL.messageBuilder(RequestRespawnVotingSyncPacket::class.java, ++id)
            .encoder(RequestRespawnVotingSyncPacket::encode)
            .decoder(RequestRespawnVotingSyncPacket::decode)
            .consumerMainThread(RequestRespawnVotingSyncPacket::handle)
            .add()
    }
}
