package com.kenny.supermetroid

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.util.Random
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil

object Maze3dCommand {
    private const val DEFAULT_WALL_BLOCK_ID = "minecraft:reinforced_deepslate"

    private val suggestedWallBlocks = listOf(
        DEFAULT_WALL_BLOCK_ID,
        "minecraft:netherite_block",
        "minecraft:obsidian",
        "minecraft:crying_obsidian",
        "minecraft:deepslate_bricks",
        "minecraft:blackstone",
        "minecraft:glass",
        "minecraft:tinted_glass",
        "minecraft:white_stained_glass",
        "minecraft:cyan_stained_glass",
        "minecraft:red_stained_glass",
        "minecraft:iron_block",
        "minecraft:copper_block",
        "minecraft:stone_bricks",
        "minecraft:deepslate_tiles"
    )

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("maze3d")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    literal("status")
                        .executes(::executeStatus)
                )
                .then(
                    argument("width", IntegerArgumentType.integer(1))
                        .then(
                            argument("height", IntegerArgumentType.integer(1))
                                .then(
                                    argument("depth", IntegerArgumentType.integer(1))
                                        .executes { executeGenerate(it, null, null, null, null) }
                                        .then(
                                            argument("wall_block", IdentifierArgument.id())
                                                .suggests(::suggestWallBlocks)
                                                .executes {
                                                    executeGenerate(
                                                        it,
                                                        IdentifierArgument.getId(it, "wall_block"),
                                                        null,
                                                        null,
                                                        null
                                                    )
                                                }
                                                .then(
                                                    argument("max_chamber_size", IntegerArgumentType.integer(1))
                                                        .executes {
                                                            executeGenerate(
                                                                it,
                                                                IdentifierArgument.getId(it, "wall_block"),
                                                                IntegerArgumentType.getInteger(it, "max_chamber_size"),
                                                                null,
                                                                null
                                                            )
                                                        }
                                                        .then(
                                                            argument(
                                                                "cycles",
                                                                IntegerArgumentType.integer(
                                                                    Maze3dGenerator.MIN_CYCLE_INTENSITY,
                                                                    Maze3dGenerator.MAX_CYCLE_INTENSITY
                                                                )
                                                            )
                                                                .executes {
                                                                    executeGenerate(
                                                                        it,
                                                                        IdentifierArgument.getId(it, "wall_block"),
                                                                        IntegerArgumentType.getInteger(it, "max_chamber_size"),
                                                                        IntegerArgumentType.getInteger(it, "cycles"),
                                                                        null
                                                                    )
                                                                }
                                                                .then(
                                                                    argument("seed", LongArgumentType.longArg())
                                                                        .executes {
                                                                            executeGenerate(
                                                                                it,
                                                                                IdentifierArgument.getId(it, "wall_block"),
                                                                                IntegerArgumentType.getInteger(it, "max_chamber_size"),
                                                                                IntegerArgumentType.getInteger(it, "cycles"),
                                                                                LongArgumentType.getLong(it, "seed")
                                                                            )
                                                                        }
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        )
    }

    private fun executeStatus(context: CommandContext<CommandSourceStack>): Int {
        context.source.sendSuccess({
            Component.literal(Maze3dBuildQueue.status())
        }, false)
        return 1
    }

    private fun executeGenerate(
        context: CommandContext<CommandSourceStack>,
        wallBlockArg: Identifier?,
        maxChamberSizeArg: Int?,
        cycleIntensityArg: Int?,
        seedArg: Long?
    ): Int {
        val source = context.source
        val player = source.getPlayerOrException()
        val dimensions = Maze3dDimensions(
            width = IntegerArgumentType.getInteger(context, "width"),
            height = IntegerArgumentType.getInteger(context, "height"),
            depth = IntegerArgumentType.getInteger(context, "depth")
        )

        val wallBlock = try {
            parseWallBlock(wallBlockArg ?: Identifier.parse(DEFAULT_WALL_BLOCK_ID))
        } catch (e: IllegalArgumentException) {
            source.sendFailure(Component.literal(e.message ?: "Invalid wall block."))
            return 0
        }

        val maxChamberSize = maxChamberSizeArg ?: Maze3dVoxelRenderer.DEFAULT_MAX_CHAMBER_SIZE
        val cycleIntensity = cycleIntensityArg ?: Maze3dGenerator.DEFAULT_CYCLE_INTENSITY
        val voxelRenderer = Maze3dVoxelRenderer(maxChamberSize)
        try {
            voxelRenderer.measure(dimensions)
        } catch (e: IllegalArgumentException) {
            source.sendFailure(Component.literal(e.message ?: "Maze is too large to render."))
            return 0
        }

        val seed = seedArg ?: Random().nextLong()
        val maze = Maze3dGenerator(seed, cycleIntensity).generate(dimensions)
        val plan = try {
            voxelRenderer.render(maze)
        } catch (e: IllegalArgumentException) {
            source.sendFailure(Component.literal(e.message ?: "Maze is too large to render."))
            return 0
        }

        val origin = player.blockPosition().offset(0, 0, -1)
        val renderer = Maze3dWorldRenderer(source.level, origin, wallBlock)
        val job = try {
            renderer.createJob(plan) { summary ->
                source.sendSuccess({
                    Component.literal(
                        "3D maze complete: ${summary.bounds.width}x${summary.bounds.height}x${summary.bounds.depth} " +
                            "blocks, ${summary.totalBlocks} positions written, " +
                            "${summary.treasureChestCount} treasure chests, ${maze.cycleCount()} cycles."
                    )
                }, true)
            }
        } catch (e: IllegalArgumentException) {
            source.sendFailure(Component.literal(e.message ?: "Maze does not fit world height."))
            return 0
        }

        Maze3dBuildQueue.enqueue(job)

        val summary = job.summary()
        val estimatedTicks = ceil(summary.totalBlocks.toDouble() / Maze3dBuildQueue.BLOCKS_PER_TICK).toInt()
        val estimatedSeconds = ceil(estimatedTicks / 20.0).toInt()

        source.sendSuccess({
            Component.literal(
                "Queued 3D maze ${dimensions.width}x${dimensions.height}x${dimensions.depth} cells " +
                    "using ${BuiltInRegistries.BLOCK.getKey(wallBlock)}, max chamber $maxChamberSize, " +
                    "cycle intensity $cycleIntensity (${maze.cycleCount()} cycles). " +
                    "Volume ${summary.bounds.width}x${summary.bounds.height}x${summary.bounds.depth}, " +
                    "${summary.totalBlocks} positions, ${summary.treasureChestCount} treasure chests, " +
                    "seed $seed, about ${estimatedSeconds}s."
            )
        }, true)

        return 1
    }

    private fun parseWallBlock(id: Identifier): Block {
        val block = BuiltInRegistries.BLOCK.getValue(id)

        require(block != Blocks.AIR) {
            "Unknown or unsupported wall block: $id"
        }
        require(block != Blocks.VOID_AIR && block != Blocks.CAVE_AIR) {
            "Wall block cannot be air: $id"
        }

        return block
    }

    private fun suggestWallBlocks(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        suggestedWallBlocks.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }
}
