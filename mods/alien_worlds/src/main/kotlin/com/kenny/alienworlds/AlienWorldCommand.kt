package com.kenny.alienworlds

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

object AlienWorldCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("alienworld")
                .then(
                    literal("tp")
                        .executes { ctx -> teleportToAlienWorld(ctx) }
                )
                .then(
                    literal("back")
                        .executes { ctx -> teleportToOverworld(ctx) }
                )
                .then(
                    literal("generate")
                        .executes { ctx -> generateAlienTerrain(ctx, 64) }
                        .then(
                            argument("radius", IntegerArgumentType.integer(8, 128))
                                .executes { ctx ->
                                    generateAlienTerrain(ctx, IntegerArgumentType.getInteger(ctx, "radius"))
                                }
                        )
                )
                .then(
                    literal("info")
                        .executes(::showInfo)
                )
        )
    }

    private fun teleportToAlienWorld(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.playerOrException
        val server = source.server
        val alienWorld = server.getLevel(AlienDimension.ALIEN_WORLD_KEY)

        if (alienWorld == null) {
            source.sendFailure(Component.literal("Alien world dimension not found!"))
            return 0
        }

        val spawnY = AlienChunkGenerator.getTerrainHeight(0, 0) + 2
        player.teleportTo(alienWorld, 0.5, spawnY.toDouble(), 0.5, setOf(), player.yRot, player.xRot, false)
        source.sendSuccess({ Component.literal("Teleported to the Alien World!") }, true)
        return 1
    }

    private fun teleportToOverworld(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.playerOrException
        val overworld = source.server.overworld()

        player.teleportTo(overworld, 0.5, 100.0, 0.5, setOf(), player.yRot, player.xRot, false)
        source.sendSuccess({ Component.literal("Returned to the Overworld.") }, true)
        return 1
    }

    private fun generateAlienTerrain(ctx: CommandContext<CommandSourceStack>, radius: Int): Int {
        val source = ctx.source
        val player = source.playerOrException
        val world = source.level
        val center = player.blockPosition()

        source.sendSuccess({ Component.literal("Generating alien terrain (radius=$radius)...") }, true)

        val seed = center.hashCode().toLong()

        for (dx in -radius until radius) {
            for (dz in -radius until radius) {
                val worldX = center.x + dx
                val worldZ = center.z + dz
                val terrainHeight = AlienChunkGenerator.getTerrainHeight(worldX, worldZ, seed)

                for (y in terrainHeight + 1..terrainHeight + 30) {
                    setBlock(world, BlockPos(worldX, y, worldZ), Blocks.AIR.defaultBlockState())
                }

                setBlock(world, BlockPos(worldX, terrainHeight, worldZ), AlienBlocks.ALIEN_SOIL.defaultBlockState())

                for (y in terrainHeight - 4 until terrainHeight) {
                    setBlock(world, BlockPos(worldX, y, worldZ), AlienBlocks.ALIEN_STONE.defaultBlockState())
                }

                for (y in terrainHeight - 12 until terrainHeight - 4) {
                    setBlock(world, BlockPos(worldX, y, worldZ), AlienBlocks.ALIEN_DEEPROCK.defaultBlockState())
                }

                val desertNoise = smoothNoise(worldX.toDouble(), worldZ.toDouble(), 50.0, seed + 400)
                if (desertNoise > 0.4) {
                    setBlock(world, BlockPos(worldX, terrainHeight, worldZ), AlienBlocks.ALIEN_SAND.defaultBlockState())
                }

                val mossNoise = smoothNoise(worldX.toDouble(), worldZ.toDouble(), 8.0, seed + 300)
                if (mossNoise > 0.3 && desertNoise <= 0.4) {
                    setBlock(world, BlockPos(worldX, terrainHeight + 1, worldZ), AlienBlocks.ALIEN_MOSS.defaultBlockState())
                }

                val spireNoise = smoothNoise(worldX.toDouble(), worldZ.toDouble(), 32.0, seed + 200)
                if (spireNoise > 0.85) {
                    val spireHeight = (8 + smoothNoise(worldX.toDouble(), worldZ.toDouble(), 16.0, seed + 201) * 12).toInt()
                    for (y in terrainHeight + 1..terrainHeight + spireHeight) {
                        setBlock(world, BlockPos(worldX, y, worldZ), AlienBlocks.ALIEN_CRYSTAL.defaultBlockState())
                    }
                }
            }
        }

        source.sendSuccess({
            Component.literal("Alien terrain generated! ${radius * 2}x${radius * 2} blocks.")
        }, true)
        return 1
    }

    private fun showInfo(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.sendSuccess({
            Component.literal(
                """
                |Alien Worlds Mod
                |Commands:
                |  /alienworld tp - Teleport to alien dimension (infinite alien world!)
                |  /alienworld back - Return to overworld
                |  /alienworld generate [radius] - Generate alien terrain in current world
                |  /alienworld info - Show this help
                """.trimMargin()
            )
        }, false)
        return 1
    }

    private fun setBlock(world: ServerLevel, pos: BlockPos, state: BlockState) {
        world.setBlock(pos, state, 2)
    }

    private fun smoothNoise(x: Double, z: Double, scale: Double, seed: Long): Double {
        val sx = x / scale
        val sz = z / scale
        val ix = kotlin.math.floor(sx).toInt()
        val iz = kotlin.math.floor(sz).toInt()
        val fx = sx - ix
        val fz = sz - iz
        val smoothX = fx * fx * (3.0 - 2.0 * fx)
        val smoothZ = fz * fz * (3.0 - 2.0 * fz)
        val n00 = noise2D(ix.toDouble(), iz.toDouble(), seed)
        val n10 = noise2D((ix + 1).toDouble(), iz.toDouble(), seed)
        val n01 = noise2D(ix.toDouble(), (iz + 1).toDouble(), seed)
        val n11 = noise2D((ix + 1).toDouble(), (iz + 1).toDouble(), seed)
        val nx0 = n00 + smoothX * (n10 - n00)
        val nx1 = n01 + smoothX * (n11 - n01)
        return nx0 + smoothZ * (nx1 - nx0)
    }

    private fun noise2D(x: Double, z: Double, seed: Long): Double {
        val hash = seed xor (kotlin.math.floor(x).toLong() * 73856093L) xor (kotlin.math.floor(z).toLong() * 19349663L)
        return (hash * hash * hash * 60493 shr 13).toDouble() / Long.MAX_VALUE.toDouble()
    }
}
