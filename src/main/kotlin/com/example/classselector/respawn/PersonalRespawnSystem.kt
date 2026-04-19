package com.example.classselector.respawn

import com.example.classselector.ClassSelectorMod
import com.example.classselector.ClassSelectorScope
import com.example.classselector.kit.KitApplicator
import com.mojang.brigadier.Command
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
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

data class PersonalRespawnPoint(val dim: String, val x: Int, val y: Int, val z: Int)

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
        setRespawnPoint(player, point)
        return point
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

    fun setRespawnPoint(player: ServerPlayer, point: PersonalRespawnPoint) {
        saveRespawnPoint(player, point)
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
