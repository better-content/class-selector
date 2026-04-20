package com.example.classselector.respawn

import com.example.classselector.ClassSelectorMod
import com.example.classselector.ClassSelectorScope
import com.example.classselector.kit.KitApplicator
import com.mojang.brigadier.Command
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import kotlin.math.abs
import java.util.TreeMap

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
private const val RESPAWN_SNAP_RADIUS = 16
private const val RESPAWN_VERTICAL_WEIGHT = 3

data class PersonalRespawnPoint(val dim: String, val x: Int, val y: Int, val z: Int)
data class PreparedRespawnPoint(val point: PersonalRespawnPoint, val sitePrepared: Boolean, val locationAdjusted: Boolean)

object PersonalRespawnService {
    private const val RESPAWN_DIM_TAG = "classselector:respawn_dim"
    private const val RESPAWN_X_TAG = "classselector:respawn_x"
    private const val RESPAWN_Y_TAG = "classselector:respawn_y"
    private const val RESPAWN_Z_TAG = "classselector:respawn_z"

    private val scheduledTasks: MutableMap<Long, MutableList<(MinecraftServer) -> Unit>> = TreeMap()

    fun schedule(server: MinecraftServer, delayTicks: Long, task: (MinecraftServer) -> Unit) {
        val executeAt = server.overworld().gameTime + delayTicks.coerceAtLeast(0)
        scheduledTasks.computeIfAbsent(executeAt) { mutableListOf() }.add(task)
    }

    fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        scheduledTasks.remove(now)?.forEach { it(server) }
    }

    fun assignCurrentLocation(player: ServerPlayer): PersonalRespawnPoint {
        val point = PersonalRespawnPoint(
            dim = dimensionId(player.serverLevel()),
            x = player.blockX,
            y = player.blockY,
            z = player.blockZ
        )
        return setRespawnPoint(player, point).point
    }

    fun hasRespawnPoint(player: ServerPlayer): Boolean =
        player.persistentData.contains(RESPAWN_DIM_TAG) &&
            player.persistentData.contains(RESPAWN_X_TAG) &&
            player.persistentData.contains(RESPAWN_Y_TAG) &&
            player.persistentData.contains(RESPAWN_Z_TAG)

    fun getRespawnPoint(player: ServerPlayer): PersonalRespawnPoint? {
        if (!hasRespawnPoint(player)) return null
        return PersonalRespawnPoint(
            dim = player.persistentData.getString(RESPAWN_DIM_TAG),
            x = player.persistentData.getInt(RESPAWN_X_TAG),
            y = player.persistentData.getInt(RESPAWN_Y_TAG),
            z = player.persistentData.getInt(RESPAWN_Z_TAG)
        )
    }

    fun setRespawnPoint(player: ServerPlayer, point: PersonalRespawnPoint): PreparedRespawnPoint {
        val preparedPoint = prepareRespawnPoint(player.server, point)
        saveRespawnPoint(player, preparedPoint.point)
        return preparedPoint
    }

    fun clearRespawnPoint(player: ServerPlayer) {
        player.persistentData.remove(RESPAWN_DIM_TAG)
        player.persistentData.remove(RESPAWN_X_TAG)
        player.persistentData.remove(RESPAWN_Y_TAG)
        player.persistentData.remove(RESPAWN_Z_TAG)
        clearVanillaRespawnPosition(player)
    }

    fun releasePlayerFromSpectator(player: ServerPlayer): Boolean {
        if (!ClassSelectorScope.isActiveIn(player.server)) return false
        if (!KitApplicator.hasSelectedClass(player)) return false
        val point = getRespawnPoint(player) ?: return false
        if (!player.isSpectator) return true

        player.setGameMode(GameType.SURVIVAL)
        teleportPlayerToRespawnPoint(player.server, player, point)
        return true
    }

    fun handleRespawn(player: ServerPlayer) {
        if (!ClassSelectorScope.isActiveIn(player.server)) return
        if (!KitApplicator.hasSelectedClass(player)) return
        val point = getRespawnPoint(player) ?: return

        schedule(player.server, 1) { server ->
            val currentPlayer = server.playerList.getPlayer(player.uuid) ?: return@schedule
            teleportPlayerToRespawnPoint(server, currentPlayer, point)
        }
    }

    fun copyRespawnPoint(from: ServerPlayer, to: ServerPlayer) {
        val point = getRespawnPoint(from) ?: return
        saveRespawnPoint(to, point)
    }

    fun sendRespawnResetMessage(player: ServerPlayer) {
        player.sendSystemMessage(Component.literal("Your permanent class respawn point was cleared by an admin."))
    }

    private fun saveRespawnPoint(player: ServerPlayer, point: PersonalRespawnPoint) {
        player.persistentData.putString(RESPAWN_DIM_TAG, point.dim)
        player.persistentData.putInt(RESPAWN_X_TAG, point.x)
        player.persistentData.putInt(RESPAWN_Y_TAG, point.y)
        player.persistentData.putInt(RESPAWN_Z_TAG, point.z)

        val location = ResourceLocation.tryParse(point.dim) ?: return
        val levelKey = ResourceKey.create(Registries.DIMENSION, location)
        player.setRespawnPosition(levelKey, BlockPos(point.x, point.y, point.z), player.yRot, true, false)
    }

    internal fun prepareRespawnPoint(server: MinecraftServer, requestedPoint: PersonalRespawnPoint): PreparedRespawnPoint {
        val level = resolveLevel(server, requestedPoint.dim)
            ?: return PreparedRespawnPoint(requestedPoint, sitePrepared = false, locationAdjusted = false)

        val origin = clampFeetPos(level, BlockPos(requestedPoint.x, requestedPoint.y, requestedPoint.z))
        val targetFeetPos = if (isValidFeetPos(level, origin)) origin else findNearestValidFeetPos(level, origin) ?: origin
        val point = PersonalRespawnPoint(requestedPoint.dim, targetFeetPos.x, targetFeetPos.y, targetFeetPos.z)
        val sitePrepared = prepareRespawnSite(level, targetFeetPos)

        return PreparedRespawnPoint(
            point = point,
            sitePrepared = sitePrepared,
            locationAdjusted = point != requestedPoint
        )
    }

    private fun clampFeetPos(level: ServerLevel, pos: BlockPos): BlockPos {
        val clampedY = pos.y.coerceIn(level.minBuildHeight + 1, level.maxBuildHeight - 2)
        return BlockPos(pos.x, clampedY, pos.z)
    }

    private fun isValidFeetPos(level: ServerLevel, feetPos: BlockPos): Boolean {
        val basePos = feetPos.below()
        val headPos = feetPos.above()
        if (feetPos.y < level.minBuildHeight + 1 || headPos.y >= level.maxBuildHeight) return false
        if (!level.isInWorldBounds(basePos) || !level.isInWorldBounds(feetPos) || !level.isInWorldBounds(headPos)) return false

        val baseState = level.getBlockState(basePos)
        val feetState = level.getBlockState(feetPos)
        val headState = level.getBlockState(headPos)

        return baseState.isFaceSturdy(level, basePos, Direction.UP) &&
            feetState.isAir &&
            headState.isAir
    }

    private fun findNearestValidFeetPos(level: ServerLevel, origin: BlockPos): BlockPos? {
        var best: BlockPos? = null
        var bestScore = Int.MAX_VALUE
        var bestVerticalDelta = Int.MAX_VALUE
        var bestDistance = Int.MAX_VALUE

        for (dx in -RESPAWN_SNAP_RADIUS..RESPAWN_SNAP_RADIUS) {
            for (dy in -RESPAWN_SNAP_RADIUS..RESPAWN_SNAP_RADIUS) {
                for (dz in -RESPAWN_SNAP_RADIUS..RESPAWN_SNAP_RADIUS) {
                    if (dx == 0 && dy == 0 && dz == 0) continue

                    val candidate = BlockPos(origin.x + dx, origin.y + dy, origin.z + dz)
                    if (!isValidFeetPos(level, candidate)) continue

                    val horizontalDistance = abs(dx) + abs(dz)
                    val weightedScore = (abs(dy) * RESPAWN_VERTICAL_WEIGHT) + horizontalDistance
                    val distance = dx * dx + dy * dy + dz * dz
                    val verticalDelta = abs(dy)
                    if (
                        best == null ||
                        weightedScore < bestScore ||
                        (weightedScore == bestScore && verticalDelta < bestVerticalDelta) ||
                        (weightedScore == bestScore && verticalDelta == bestVerticalDelta && distance < bestDistance) ||
                        (weightedScore == bestScore && verticalDelta == bestVerticalDelta && distance == bestDistance && compareCandidate(candidate, best) < 0)
                    ) {
                        best = candidate
                        bestScore = weightedScore
                        bestDistance = distance
                        bestVerticalDelta = verticalDelta
                    }
                }
            }
        }

        return best ?: findNearestValidFeetPosInColumn(level, origin)
    }

    private fun findNearestValidFeetPosInColumn(level: ServerLevel, origin: BlockPos): BlockPos? {
        var best: BlockPos? = null
        var bestVerticalDelta = Int.MAX_VALUE

        for (y in (level.minBuildHeight + 1)..(level.maxBuildHeight - 2)) {
            val candidate = BlockPos(origin.x, y, origin.z)
            if (!isValidFeetPos(level, candidate)) continue

            val verticalDelta = abs(y - origin.y)
            if (best == null || verticalDelta < bestVerticalDelta) {
                best = candidate
                bestVerticalDelta = verticalDelta
            }
        }

        return best
    }

    private fun compareCandidate(left: BlockPos, right: BlockPos): Int {
        if (left.y != right.y) return left.y.compareTo(right.y)
        if (left.x != right.x) return left.x.compareTo(right.x)
        return left.z.compareTo(right.z)
    }

    private fun prepareRespawnSite(level: ServerLevel, feetPos: BlockPos): Boolean {
        val basePos = feetPos.below()
        val headPos = feetPos.above()
        var changed = false

        if (!level.getBlockState(basePos).`is`(Blocks.CRYING_OBSIDIAN)) {
            level.setBlockAndUpdate(basePos, Blocks.CRYING_OBSIDIAN.defaultBlockState())
            changed = true
        }
        if (!level.getBlockState(feetPos).isAir) {
            level.setBlockAndUpdate(feetPos, Blocks.AIR.defaultBlockState())
            changed = true
        }
        if (!level.getBlockState(headPos).isAir) {
            level.setBlockAndUpdate(headPos, Blocks.AIR.defaultBlockState())
            changed = true
        }

        return changed
    }

    private fun clearVanillaRespawnPosition(player: ServerPlayer) {
        val method = ServerPlayer::class.java.getMethod(
            "setRespawnPosition",
            ResourceKey::class.java,
            BlockPos::class.java,
            java.lang.Float.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE
        )
        method.invoke(player, null, null, 0f, false, false)
    }

    private fun teleportPlayerToRespawnPoint(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val level = resolveLevel(server, point.dim) ?: player.serverLevel()
        val feetPos = BlockPos(point.x, point.y, point.z)
        if (!isValidFeetPos(level, feetPos)) {
            prepareRespawnSite(level, feetPos)
        }
        player.teleportTo(level, point.x + 0.5, point.y.toDouble(), point.z + 0.5, player.yRot, player.xRot)
        schedule(server, 1) {
            playRespawnSoundForPlayer(server, player, point)
            spawnRespawnParticlesForPlayer(server, player, point)
        }
    }

    private fun playRespawnSoundForPlayer(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val x = point.x + 0.5
        val y = point.y + 1.0
        val z = point.z + 0.5
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

    private fun spawnRespawnParticlesForPlayer(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val pulses = (FX_DURATION_TICKS / FX_PULSE_EVERY_TICKS).coerceAtLeast(1)
        repeat(pulses.toInt()) { pulseIndex ->
            schedule(server, pulseIndex * FX_PULSE_EVERY_TICKS) {
                emitRespawnParticlesOnce(server, player, point)
            }
        }
    }

    private fun emitRespawnParticlesOnce(server: MinecraftServer, player: ServerPlayer, point: PersonalRespawnPoint) {
        val x = point.x + 0.5
        val y = point.y + 1.0
        val z = point.z + 0.5
        val targetName = player.scoreboardName
        val prefix = "execute in ${point.dim} run particle"
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
}

@Mod.EventBusSubscriber(modid = ClassSelectorMod.MOD_ID)
object PersonalRespawnEvents {
    @JvmStatic
    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        PersonalRespawnService.handleRespawn(player)
    }

    @JvmStatic
    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        PersonalRespawnService.tick(event.server)
    }

    @JvmStatic
    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("classselector")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("resetrespawn")
                        .then(
                            Commands.argument("targets", EntityArgument.players())
                                .executes { ctx ->
                                    val targets = EntityArgument.getPlayers(ctx, "targets")
                                    targets.forEach { player ->
                                        PersonalRespawnService.clearRespawnPoint(player)
                                        PersonalRespawnService.sendRespawnResetMessage(player)
                                    }
                                    ctx.source.sendSuccess(
                                        { Component.literal("Cleared permanent respawn for ${targets.size} player(s).") },
                                        true
                                    )
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
        )
    }
}
