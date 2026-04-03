package com.example.classselector.respawn

import com.example.classselector.ClassSelectorScope
import com.example.classselector.ClassSelectorMod
import com.example.classselector.client.RespawnVotingProposalView
import com.example.classselector.client.RespawnVotingSnapshot
import com.example.classselector.kit.KitApplicator
import com.example.classselector.network.ClassSelectorNetwork
import com.example.classselector.network.SyncRespawnVotingPacket
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.saveddata.SavedData
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor
import java.util.TreeMap

private const val DIM_DEFAULT = "minecraft:overworld"
private const val SCRIPT_VERSION = "v19_manual_only_particles_pulse_3s_thick_soul_with_spooky_sound"

private const val FX_DURATION_TICKS = 60L
private const val FX_PULSE_EVERY_TICKS = 2L
private const val FX_SPREAD = 1.0

private const val FX_COUNT_SOUL_PER_PULSE = 70
private const val FX_COUNT_CHARGE_PER_PULSE = 30
private const val FX_COUNT_POP_PER_PULSE = 20
private const val FX_COUNT_BLUE_PER_PULSE = 18

private const val SOUND_VOL = 6.0
private const val SOUND_MINVOL = 1.0
private const val SOUND_PITCH_BELL = 0.75
private const val SOUND_PITCH_PORTAL = 0.9
private const val SOUND_PITCH_WARDEN = 0.8
private const val SOUND_PITCH_EVOKER = 0.9

data class RespawnHub(val dim: String, val x: Int, val y: Int, val z: Int)

data class RespawnHubProposal(
    val id: Int,
    val hub: RespawnHub,
    val suggesterName: String,
    val suggesterUuid: String
)

class RespawnHubSavedData : SavedData() {
    var enabled: Boolean = false
    var hubs: MutableList<RespawnHub> = mutableListOf()
    var counts: MutableList<Int> = mutableListOf()
    var proposals: MutableList<RespawnHubProposal> = mutableListOf()
    var votesByPlayer: MutableMap<String, Int> = mutableMapOf()
    var nextProposalId: Int = 1

    override fun save(tag: CompoundTag): CompoundTag {
        tag.putString("scriptVersion", SCRIPT_VERSION)
        tag.putBoolean("enabled", enabled)
        tag.putString("hubsJson", GSON.toJson(hubs))
        tag.putString("countsJson", GSON.toJson(counts))
        tag.putString("proposalsJson", GSON.toJson(proposals))
        tag.putString("votesJson", GSON.toJson(votesByPlayer))
        tag.putInt("nextProposalId", nextProposalId)
        return tag
    }

    private fun normalize() {
        counts.replaceAll { value -> value.coerceAtLeast(0) }
        while (counts.size < hubs.size) {
            counts += 0
        }
        if (counts.size > hubs.size) {
            counts = counts.take(hubs.size).toMutableList()
        }
        val validProposalIds = proposals.map { it.id }.toSet()
        votesByPlayer.entries.removeIf { (_, proposalId) -> proposalId !in validProposalIds }
        nextProposalId = maxOf(nextProposalId, (proposals.maxOfOrNull { it.id } ?: 0) + 1)
    }

    fun markDirtyAndNormalize() {
        normalize()
        setDirty()
    }

    companion object {
        private const val DATA_NAME = "${ClassSelectorMod.MOD_ID}_respawn_hubs"
        private val GSON = Gson()
        private val HUB_LIST_TYPE = object : TypeToken<MutableList<RespawnHub>>() {}.type
        private val COUNT_LIST_TYPE = object : TypeToken<MutableList<Int>>() {}.type
        private val PROPOSAL_LIST_TYPE = object : TypeToken<MutableList<RespawnHubProposal>>() {}.type
        private val VOTE_MAP_TYPE = object : TypeToken<MutableMap<String, Int>>() {}.type

        fun get(server: MinecraftServer): RespawnHubSavedData =
            server.overworld().dataStorage.computeIfAbsent(::load, ::RespawnHubSavedData, DATA_NAME)

        private fun load(tag: CompoundTag): RespawnHubSavedData {
            val data = RespawnHubSavedData()
            data.enabled = tag.getBoolean("enabled")
            data.hubs = parseJson(tag.getString("hubsJson"), HUB_LIST_TYPE) { mutableListOf() }
            data.counts = parseJson(tag.getString("countsJson"), COUNT_LIST_TYPE) { mutableListOf() }
            data.proposals = parseJson(tag.getString("proposalsJson"), PROPOSAL_LIST_TYPE) { mutableListOf() }
            data.votesByPlayer = parseJson(tag.getString("votesJson"), VOTE_MAP_TYPE) { mutableMapOf() }
            data.nextProposalId = tag.getInt("nextProposalId").takeIf { it > 0 } ?: 1
            data.normalize()
            return data
        }

        private fun <T> parseJson(json: String?, type: java.lang.reflect.Type, fallback: () -> T): T =
            runCatching { GSON.fromJson<T>(json, type) }.getOrNull() ?: fallback()
    }
}

object RespawnHubService {
    private val scheduledTasks: MutableMap<Long, MutableList<(MinecraftServer) -> Unit>> = TreeMap()
    private val inactiveSnapshot = RespawnVotingSnapshot(
        enabled = false,
        canVote = false,
        eligibleSpectators = 0,
        currentVoteProposalId = null,
        proposals = emptyList()
    )

    fun schedule(server: MinecraftServer, delayTicks: Long, task: (MinecraftServer) -> Unit) {
        val executeAt = server.overworld().gameTime + delayTicks.coerceAtLeast(0)
        scheduledTasks.computeIfAbsent(executeAt) { mutableListOf() }.add(task)
    }

    fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        scheduledTasks.remove(now)?.forEach { it(server) }
    }

    fun onVotingEligibilityChanged(server: MinecraftServer) {
        if (!ClassSelectorScope.isActiveIn(server)) {
            syncVotingStateToAll(server)
            return
        }
        maybeFinalizeWinningProposal(server)
        syncVotingStateToAll(server)
    }

    fun sendSpectatorHint(player: ServerPlayer) {
        if (!ClassSelectorScope.isActiveIn(player.server)) return
        val state = RespawnHubSavedData.get(player.server)
        if (!state.enabled || !isEligibleSpectator(player.server, player)) return
        player.sendSystemMessage(Component.literal("Press V to open respawn voting while you survey the world in spectator."))
    }

    fun syncVotingState(player: ServerPlayer) {
        ClassSelectorNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with { player },
            SyncRespawnVotingPacket.fromSnapshot(
                if (ClassSelectorScope.isActiveIn(player.server)) buildSnapshot(player.server, player) else inactiveSnapshot
            )
        )
    }

    fun syncVotingStateToAll(server: MinecraftServer) {
        server.playerList.players.forEach(::syncVotingState)
    }

    fun setEnabled(server: MinecraftServer, enabled: Boolean) {
        val state = RespawnHubSavedData.get(server)
        state.enabled = enabled
        state.markDirtyAndNormalize()
        broadcast(server, if (enabled) "Enabled. Hubs=${state.hubs.size}" else "Disabled.")
        if (enabled && ClassSelectorScope.isActiveIn(server)) {
            releaseReadyPlayers(server)
        }
        syncVotingStateToAll(server)
    }

    fun sendStatus(sourcePlayer: ServerPlayer?, server: MinecraftServer) {
        val state = RespawnHubSavedData.get(server)
        val message = "Status: enabled=${state.enabled} hubs=${state.hubs.size} final=${state.hubs.isNotEmpty()} proposals=${state.proposals.size}"
        if (sourcePlayer != null) {
            sourcePlayer.sendSystemMessage(prefix(message))
        } else {
            broadcast(server, message)
        }
    }

    fun clearAll(server: MinecraftServer) {
        val state = RespawnHubSavedData.get(server)
        state.hubs.clear()
        state.counts.clear()
        state.proposals.clear()
        state.votesByPlayer.clear()
        state.markDirtyAndNormalize()
        broadcast(server, "Cleared all hubs and proposals.")
        syncVotingStateToAll(server)
    }

    fun addHubAtPlayer(server: MinecraftServer, player: ServerPlayer) {
        val state = RespawnHubSavedData.get(server)
        val hub = hubFromPlayer(player)
        if (state.hubs.any { sameHub(it, hub) }) {
            player.sendSystemMessage(prefix("Hub already exists here."))
            return
        }

        state.hubs += hub
        state.counts += 0
        state.markDirtyAndNormalize()

        placeCryingObsidianBelow(player.serverLevel(), hub)
        playHubSoundForPlayer(server, player, hub)
        spawnHubParticlesForPlayer(server, player, hub)
        broadcast(server, "Hub added at ${hub.dim} ${hub.x} ${hub.y} ${hub.z}")
        releaseReadyPlayers(server)
        syncVotingStateToAll(server)
    }

    fun removeHubAtPlayer(server: MinecraftServer, player: ServerPlayer) {
        val state = RespawnHubSavedData.get(server)
        val hub = hubFromPlayer(player)
        val index = state.hubs.indexOfFirst { sameHub(it, hub) }
        if (index < 0) {
            player.sendSystemMessage(prefix("No hub found at this location."))
            return
        }

        state.hubs.removeAt(index)
        if (index < state.counts.size) {
            state.counts.removeAt(index)
        }
        state.markDirtyAndNormalize()
        broadcast(server, "Hub removed at ${hub.dim} ${hub.x} ${hub.y} ${hub.z}")
        syncVotingStateToAll(server)
    }

    fun removeHubByIndex(server: MinecraftServer, index: Int): Boolean {
        val state = RespawnHubSavedData.get(server)
        if (index !in state.hubs.indices) {
            broadcast(server, "Index out of range.")
            return false
        }
        state.hubs.removeAt(index)
        if (index < state.counts.size) {
            state.counts.removeAt(index)
        }
        state.markDirtyAndNormalize()
        broadcast(server, "Removed hub index $index")
        syncVotingStateToAll(server)
        return true
    }

    fun teleportPlayerToHubAndFx(server: MinecraftServer, player: ServerPlayer, hub: RespawnHub) {
        val level = resolveLevel(server, hub.dim) ?: return
        player.teleportTo(level, hub.x + 0.5, hub.y.toDouble(), hub.z + 0.5, player.yRot, player.xRot)
        schedule(server, 1) {
            placeCryingObsidianBelow(level, hub)
            playHubSoundForPlayer(server, player, hub)
            spawnHubParticlesForPlayer(server, player, hub)
        }
    }

    fun handleRespawn(player: ServerPlayer) {
        val server = player.server
        if (!ClassSelectorScope.isActiveIn(server)) return
        val state = RespawnHubSavedData.get(server)
        if (!state.enabled || playerHasPersonalSpawn(player) || state.hubs.isEmpty()) return

        val hubIndex = chooseLeastUsedHubIndex(state)
        if (hubIndex < 0) return

        state.counts[hubIndex] = (state.counts.getOrNull(hubIndex) ?: 0) + 1
        state.markDirtyAndNormalize()
        val hub = state.hubs[hubIndex]
        schedule(server, 1) {
            val currentPlayer = it.playerList.getPlayer(player.uuid) ?: return@schedule
            teleportPlayerToHubAndFx(it, currentPlayer, hub)
        }
    }

    fun sendHubListTo(player: ServerPlayer) {
        val state = RespawnHubSavedData.get(player.server)
        if (state.hubs.isEmpty()) {
        player.sendSystemMessage(Component.literal("Respawn hubs: (none)").withStyle(ChatFormatting.GOLD))
            return
        }

        player.sendSystemMessage(Component.literal("Respawn hubs (${state.hubs.size}):").withStyle(ChatFormatting.GOLD))
        state.hubs.forEachIndexed { index, hub ->
            val command = "/rrhubs tp $index"
            val locationText = "${hub.dim} ${hub.x + 0.5} ${hub.y} ${hub.z + 0.5}"
            player.sendSystemMessage(
                Component.empty()
                    .append(Component.literal("[$index] ").withStyle(ChatFormatting.GRAY))
                    .append(
                        Component.literal(locationText).withStyle(
                            Style.EMPTY
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport\n$command")))
                        )
                    )
            )
        }
    }

    fun sendProposalListTo(player: ServerPlayer) {
        val server = player.server
        val state = RespawnHubSavedData.get(server)
        if (state.proposals.isEmpty()) {
            player.sendSystemMessage(Component.literal("Respawn proposals: (none)").withStyle(ChatFormatting.GOLD))
            return
        }

        val eligibleVoters = eligibleSpectators(server).map { it.stringUUID }.toSet()
        player.sendSystemMessage(
            Component.literal("Respawn proposals (${state.proposals.size}) - eligible spectators: ${eligibleVoters.size}")
                .withStyle(ChatFormatting.GOLD)
        )

        state.proposals.forEach { proposal ->
            val voteCount = eligibleVoters.count { state.votesByPlayer[it] == proposal.id }
            val command = "/rrhubs vote ${proposal.id}"
            val locationText = "${proposal.hub.dim} ${proposal.hub.x + 0.5} ${proposal.hub.y} ${proposal.hub.z + 0.5}"
            player.sendSystemMessage(
                Component.empty()
                    .append(Component.literal("[${proposal.id}] ").withStyle(ChatFormatting.GRAY))
                    .append(
                        Component.literal(locationText).withStyle(
                            Style.EMPTY
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Vote for this hub\n$command")))
                        )
                    )
                    .append(Component.literal(" votes=$voteCount").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" by ${proposal.suggesterName}").withStyle(ChatFormatting.DARK_GRAY))
            )
        }
    }

    fun suggestHere(player: ServerPlayer) {
        if (!ClassSelectorScope.isActiveIn(player.server)) {
            player.sendSystemMessage(prefix("Respawn voting is only active in survival worlds."))
            return
        }
        if (!isEligibleSpectator(player.server, player)) {
            player.sendSystemMessage(prefix("Only currently logged-in spectators without a class can suggest hubs."))
            return
        }

        val server = player.server
        val state = RespawnHubSavedData.get(server)
        if (state.votesByPlayer.containsKey(player.stringUUID)) {
            player.sendSystemMessage(prefix("You have already voted on the current spawn location round."))
            return
        }
        val hub = hubFromPlayer(player)
        if (state.hubs.any { sameHub(it, hub) }) {
            player.sendSystemMessage(prefix("That location is already a finalized hub."))
            return
        }
        if (state.proposals.any { sameHub(it.hub, hub) }) {
            player.sendSystemMessage(prefix("That location is already proposed."))
            return
        }

        val proposal = RespawnHubProposal(
            id = state.nextProposalId++,
            hub = hub,
            suggesterName = player.scoreboardName,
            suggesterUuid = player.stringUUID
        )
        state.proposals += proposal
        state.votesByPlayer[player.stringUUID] = proposal.id
        state.markDirtyAndNormalize()

        player.sendSystemMessage(prefix("Proposed hub at ${hub.dim} ${hub.x} ${hub.y} ${hub.z}. Your vote was cast automatically."))
        broadcast(server, "${player.scoreboardName} proposed a respawn hub at ${hub.dim} ${hub.x} ${hub.y} ${hub.z}")
        maybeFinalizeWinningProposal(server)
        syncVotingStateToAll(server)
    }

    fun castVote(player: ServerPlayer, proposalId: Int) {
        if (!ClassSelectorScope.isActiveIn(player.server)) {
            player.sendSystemMessage(prefix("Respawn voting is only active in survival worlds."))
            return
        }
        if (!isEligibleSpectator(player.server, player)) {
            player.sendSystemMessage(prefix("Only currently logged-in spectators without a class can vote."))
            return
        }

        val server = player.server
        val state = RespawnHubSavedData.get(server)
        val proposal = state.proposals.firstOrNull { it.id == proposalId }
        if (proposal == null) {
            player.sendSystemMessage(prefix("Unknown proposal id $proposalId."))
            return
        }

        val previousVote = state.votesByPlayer[player.stringUUID]
        state.votesByPlayer[player.stringUUID] = proposalId
        state.markDirtyAndNormalize()
        val verb = if (previousVote == null) "Voted" else "Changed vote"
        player.sendSystemMessage(prefix("$verb to proposal $proposalId at ${proposal.hub.dim} ${proposal.hub.x} ${proposal.hub.y} ${proposal.hub.z}."))
        maybeFinalizeWinningProposal(server)
        syncVotingStateToAll(server)
    }

    private fun maybeFinalizeWinningProposal(server: MinecraftServer) {
        if (!ClassSelectorScope.isActiveIn(server)) return
        val state = RespawnHubSavedData.get(server)
        if (state.proposals.isEmpty()) return

        val eligible = eligibleSpectators(server)
        if (eligible.isEmpty()) return

        val eligibleIds = eligible.map { it.stringUUID }
        val votes = eligibleIds.mapNotNull { voterId ->
            val proposalId = state.votesByPlayer[voterId] ?: return@mapNotNull null
            state.proposals.firstOrNull { it.id == proposalId }?.id
        }

        if (votes.size < eligibleIds.size) return

        val counts = votes.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        if (counts.isEmpty()) return
        if (counts.size > 1 && counts[0].value == counts[1].value) return

        val winnerId = counts[0].key
        val winner = state.proposals.firstOrNull { it.id == winnerId } ?: return
        if (state.hubs.any { sameHub(it, winner.hub) }) {
            state.proposals.removeIf { it.id == winnerId }
            state.votesByPlayer.entries.removeIf { it.value == winnerId }
            state.markDirtyAndNormalize()
            return
        }

        state.hubs += winner.hub
        state.counts += 0
        state.proposals.clear()
        state.votesByPlayer.clear()
        state.markDirtyAndNormalize()

        val level = resolveLevel(server, winner.hub.dim)
        if (level != null) {
            placeCryingObsidianBelow(level, winner.hub)
        }
        eligible.forEach { spectator ->
            playHubSoundForPlayer(server, spectator, winner.hub)
            spawnHubParticlesForPlayer(server, spectator, winner.hub)
        }

        broadcast(server, "Hub vote finalized at ${winner.hub.dim} ${winner.hub.x} ${winner.hub.y} ${winner.hub.z}")
        releaseReadyPlayers(server)
        syncVotingStateToAll(server)
    }

    fun tryReleasePlayerFromSpectator(player: ServerPlayer): Boolean {
        if (!ClassSelectorScope.isActiveIn(player.server)) {
            return false
        }
        if (!KitApplicator.hasSelectedClass(player)) {
            return false
        }

        val server = player.server
        val state = RespawnHubSavedData.get(server)
        if (state.hubs.isEmpty()) {
            player.setGameMode(GameType.SPECTATOR)
            player.sendSystemMessage(prefix("Class locked in. Stay in spectator until a spawn location is finalized."))
            return false
        }

        if (!player.isSpectator) {
            return true
        }

        val hubIndex = chooseLeastUsedHubIndex(state)
        if (hubIndex < 0) {
            return false
        }

        state.counts[hubIndex] = (state.counts.getOrNull(hubIndex) ?: 0) + 1
        state.markDirtyAndNormalize()
        player.setGameMode(GameType.SURVIVAL)
        teleportPlayerToHubAndFx(server, player, state.hubs[hubIndex])
        player.sendSystemMessage(prefix("Spawn location ready. You have been released from spectator mode."))
        syncVotingState(player)
        return true
    }

    private fun releaseReadyPlayers(server: MinecraftServer) {
        if (!ClassSelectorScope.isActiveIn(server)) return
        server.playerList.players.forEach { player ->
            if (player.isSpectator && KitApplicator.hasSelectedClass(player)) {
                tryReleasePlayerFromSpectator(player)
            }
        }
    }

    private fun buildSnapshot(server: MinecraftServer, player: ServerPlayer): RespawnVotingSnapshot {
        val state = RespawnHubSavedData.get(server)
        val eligibleIds = eligibleSpectators(server).map { it.stringUUID }.toSet()
        val proposals = state.proposals.map { proposal ->
            RespawnVotingProposalView(
                id = proposal.id,
                hub = proposal.hub,
                suggesterName = proposal.suggesterName,
                votes = eligibleIds.count { state.votesByPlayer[it] == proposal.id }
            )
        }
        return RespawnVotingSnapshot(
            enabled = state.enabled,
            canVote = isEligibleSpectator(server, player),
            eligibleSpectators = eligibleIds.size,
            currentVoteProposalId = state.votesByPlayer[player.stringUUID],
            proposals = proposals
        )
    }

    private fun eligibleSpectators(server: MinecraftServer): List<ServerPlayer> =
        server.playerList.players.filter { isEligibleSpectator(server, it) }

    private fun isEligibleSpectator(server: MinecraftServer, player: ServerPlayer): Boolean {
        val state = RespawnHubSavedData.get(server)
        return ClassSelectorScope.isActiveIn(server) &&
            player.isSpectator &&
            !KitApplicator.hasSelectedClass(player) &&
            state.hubs.isEmpty()
    }

    private fun playerHasPersonalSpawn(player: ServerPlayer): Boolean = player.respawnPosition != null

    private fun chooseLeastUsedHubIndex(state: RespawnHubSavedData): Int {
        if (state.hubs.isEmpty()) return -1
        state.markDirtyAndNormalize()
        var bestIndex = 0
        var bestCount = state.counts.getOrElse(0) { 0 }
        for (i in 1 until state.hubs.size) {
            val count = state.counts.getOrElse(i) { 0 }
            if (count < bestCount) {
                bestIndex = i
                bestCount = count
            }
        }
        return bestIndex
    }

    private fun hubFromPlayer(player: ServerPlayer): RespawnHub =
        RespawnHub(
            dim = dimensionId(player.serverLevel()),
            x = player.blockX,
            y = player.blockY,
            z = player.blockZ
        )

    private fun sameHub(left: RespawnHub, right: RespawnHub): Boolean =
        left.dim == right.dim && left.x == right.x && left.y == right.y && left.z == right.z

    private fun placeCryingObsidianBelow(level: ServerLevel, hub: RespawnHub) {
        level.setBlockAndUpdate(BlockPos(hub.x, hub.y - 1, hub.z), Blocks.CRYING_OBSIDIAN.defaultBlockState())
    }

    private fun playHubSoundForPlayer(server: MinecraftServer, player: ServerPlayer, hub: RespawnHub) {
        val x = hub.x + 0.5
        val y = hub.y + 1.0
        val z = hub.z + 0.5
        val name = player.scoreboardName
        runSilentServerCommand(
            server,
            "playsound minecraft:block.bell.use master $name $x $y $z ${SOUND_VOL * 0.75} $SOUND_PITCH_BELL $SOUND_MINVOL"
        )
        runSilentServerCommand(
            server,
            "playsound minecraft:block.end_portal.spawn master $name $x $y $z ${SOUND_VOL * 0.85} $SOUND_PITCH_PORTAL $SOUND_MINVOL"
        )
        runSilentServerCommand(
            server,
            "playsound minecraft:entity.warden.ambient master $name $x $y $z ${SOUND_VOL * 0.45} $SOUND_PITCH_WARDEN $SOUND_MINVOL"
        )
        runSilentServerCommand(
            server,
            "playsound minecraft:entity.evoker.prepare_summon master $name $x $y $z ${SOUND_VOL * 0.55} $SOUND_PITCH_EVOKER $SOUND_MINVOL"
        )
    }

    private fun spawnHubParticlesForPlayer(server: MinecraftServer, player: ServerPlayer, hub: RespawnHub) {
        val pulses = (FX_DURATION_TICKS / FX_PULSE_EVERY_TICKS).coerceAtLeast(1)
        repeat(pulses.toInt()) { pulseIndex ->
            schedule(server, pulseIndex * FX_PULSE_EVERY_TICKS) {
                emitHubParticlesOnce(server, player, hub)
            }
        }
    }

    private fun emitHubParticlesOnce(server: MinecraftServer, player: ServerPlayer, hub: RespawnHub) {
        val x = hub.x + 0.5
        val y = hub.y + 1.0
        val z = hub.z + 0.5
        val targetName = player.scoreboardName
        val prefix = "execute in ${hub.dim} run particle"
        runSilentServerCommand(
            server,
            "$prefix minecraft:sculk_soul $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_SOUL_PER_PULSE force $targetName"
        )
        runSilentServerCommand(
            server,
            "$prefix minecraft:sculk_charge $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_CHARGE_PER_PULSE force $targetName"
        )
        runSilentServerCommand(
            server,
            "$prefix minecraft:sculk_charge_pop $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_POP_PER_PULSE force $targetName"
        )
        runSilentServerCommand(
            server,
            "$prefix minecraft:soul_fire_flame $x $y $z $FX_SPREAD $FX_SPREAD $FX_SPREAD 0 $FX_COUNT_BLUE_PER_PULSE force $targetName"
        )
    }

    private fun runSilentServerCommand(server: MinecraftServer, command: String) {
        val source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        server.commands.performPrefixedCommand(source, command)
    }

    private fun resolveLevel(server: MinecraftServer, dimensionId: String): ServerLevel? {
        val location = ResourceLocation.tryParse(dimensionId) ?: return null
        val key = ResourceKey.create(Registries.DIMENSION, location)
        return server.getLevel(key)
    }

    private fun dimensionId(level: ServerLevel): String = level.dimension().location().toString()

    private fun prefix(message: String): Component = Component.literal("[rrhubs] $message")

    private fun broadcast(server: MinecraftServer, message: String) {
        server.playerList.broadcastSystemMessage(prefix(message), false)
    }
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID)
object RespawnHubEvents {
    @JvmStatic
    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        RespawnHubService.sendSpectatorHint(player)
        RespawnHubService.onVotingEligibilityChanged(player.server)
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        RespawnHubService.onVotingEligibilityChanged(player.server)
    }

    @JvmStatic
    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        RespawnHubService.handleRespawn(player)
    }

    @JvmStatic
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        RespawnHubService.tick(event.server)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher

        dispatcher.register(
            Commands.literal("rrhubs")
                .then(
                    Commands.literal("enable")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            RespawnHubService.setEnabled(ctx.source.server, true)
                            1
                        }
                )
                .then(
                    Commands.literal("disable")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            RespawnHubService.setEnabled(ctx.source.server, false)
                            1
                        }
                )
                .then(
                    Commands.literal("status")
                        .executes { ctx ->
                            RespawnHubService.sendStatus(runCatching { ctx.source.playerOrException }.getOrNull(), ctx.source.server)
                            1
                        }
                )
                .then(
                    Commands.literal("list")
                        .executes { ctx ->
                            val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                            RespawnHubService.sendHubListTo(player)
                            1
                        }
                )
                .then(
                    Commands.literal("proposals")
                        .executes { ctx ->
                            val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                            RespawnHubService.sendProposalListTo(player)
                            1
                        }
                )
                .then(
                    Commands.literal("vote")
                        .then(
                            Commands.argument("proposal", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                                    RespawnHubService.castVote(player, IntegerArgumentType.getInteger(ctx, "proposal"))
                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("suggest_here")
                        .executes { ctx ->
                            val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                            RespawnHubService.suggestHere(player)
                            1
                        }
                )
                .then(
                    Commands.literal("tp")
                        .requires { it.hasPermission(2) }
                        .then(
                            Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes { ctx ->
                                    val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                                    val state = RespawnHubSavedData.get(ctx.source.server)
                                    val index = IntegerArgumentType.getInteger(ctx, "index")
                                    val hub = state.hubs.getOrNull(index) ?: return@executes 0
                                    RespawnHubService.teleportPlayerToHubAndFx(ctx.source.server, player, hub)
                                    1
                                }
                        )
                )
                .then(
                    Commands.literal("add_here")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                            RespawnHubService.addHubAtPlayer(ctx.source.server, player)
                            1
                        }
                )
                .then(
                    Commands.literal("remove_here")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            val player = runCatching { ctx.source.playerOrException }.getOrNull() ?: return@executes 0
                            RespawnHubService.removeHubAtPlayer(ctx.source.server, player)
                            1
                        }
                )
                .then(
                    Commands.literal("remove_index")
                        .requires { it.hasPermission(2) }
                        .then(
                            Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes { ctx ->
                                    if (RespawnHubService.removeHubByIndex(ctx.source.server, IntegerArgumentType.getInteger(ctx, "index"))) 1 else 0
                                }
                        )
                )
                .then(
                    Commands.literal("clear")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            RespawnHubService.clearAll(ctx.source.server)
                            1
                        }
                )
        )
    }
}
