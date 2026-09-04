package com.kenny.blockmoeba

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.concurrent.CompletableFuture

object BlockmoebaCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("blockmoeba")
                .then(
                    literal("spawn")
                        .executes { executeSpawn(it, null, null, null, null, null, null, null) }
                        .then(
                            argument("type", StringArgumentType.string())
                                .suggests(::suggestTypes)
                                .executes { executeSpawn(it, StringArgumentType.getString(it, "type"), null, null, null, null, null, null) }
                                .then(
                                    argument("max_size", IntegerArgumentType.integer(5, 50000))
                                        .executes {
                                            executeSpawn(it, StringArgumentType.getString(it, "type"),
                                                IntegerArgumentType.getInteger(it, "max_size"), null, null, null, null, null)
                                        }
                                        .then(
                                            argument("loyal", BoolArgumentType.bool())
                                                .executes {
                                                    executeSpawn(it, StringArgumentType.getString(it, "type"),
                                                        IntegerArgumentType.getInteger(it, "max_size"),
                                                        BoolArgumentType.getBool(it, "loyal"), null, null, null, null)
                                                }
                                                .then(
                                                    argument("speed", IntegerArgumentType.integer(1, 500))
                                                        .executes {
                                                            executeSpawn(it, StringArgumentType.getString(it, "type"),
                                                                IntegerArgumentType.getInteger(it, "max_size"),
                                                                BoolArgumentType.getBool(it, "loyal"),
                                                                IntegerArgumentType.getInteger(it, "speed"), null, null, null)
                                                        }
                                                        .then(
                                                            argument("sneaky", BoolArgumentType.bool())
                                                                .executes {
                                                                    executeSpawn(it, StringArgumentType.getString(it, "type"),
                                                                        IntegerArgumentType.getInteger(it, "max_size"),
                                                                        BoolArgumentType.getBool(it, "loyal"),
                                                                        IntegerArgumentType.getInteger(it, "speed"),
                                                                        BoolArgumentType.getBool(it, "sneaky"), null, null)
                                                                }
                                                                .then(
                                                                    argument("aggro_range", IntegerArgumentType.integer(10, 1000))
                                                                        .executes {
                                                                            executeSpawn(it, StringArgumentType.getString(it, "type"),
                                                                                IntegerArgumentType.getInteger(it, "max_size"),
                                                                                BoolArgumentType.getBool(it, "loyal"),
                                                                                IntegerArgumentType.getInteger(it, "speed"),
                                                                                BoolArgumentType.getBool(it, "sneaky"),
                                                                                IntegerArgumentType.getInteger(it, "aggro_range"), null)
                                                                        }
                                                                        .then(
                                                                            argument("lava_stomach", BoolArgumentType.bool())
                                                                                .executes {
                                                                                    executeSpawn(it, StringArgumentType.getString(it, "type"),
                                                                                        IntegerArgumentType.getInteger(it, "max_size"),
                                                                                        BoolArgumentType.getBool(it, "loyal"),
                                                                                        IntegerArgumentType.getInteger(it, "speed"),
                                                                                        BoolArgumentType.getBool(it, "sneaky"),
                                                                                        IntegerArgumentType.getInteger(it, "aggro_range"),
                                                                                        BoolArgumentType.getBool(it, "lava_stomach"))
                                                                                }
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    literal("race")
                        .executes { executeRace(it, null, null, null) }
                        .then(
                            argument("racers", IntegerArgumentType.integer(2, 8))
                                .executes { executeRace(it, IntegerArgumentType.getInteger(it, "racers"), null, null) }
                                .then(
                                    argument("distance", IntegerArgumentType.integer(20, 200))
                                        .executes {
                                            executeRace(it, IntegerArgumentType.getInteger(it, "racers"),
                                                IntegerArgumentType.getInteger(it, "distance"), null)
                                        }
                                        .then(
                                            argument("max_size", IntegerArgumentType.integer(5, 50000))
                                                .executes {
                                                    executeRace(it, IntegerArgumentType.getInteger(it, "racers"),
                                                        IntegerArgumentType.getInteger(it, "distance"),
                                                        IntegerArgumentType.getInteger(it, "max_size"))
                                                }
                                        )
                                )
                        )
                )
                .then(
                    literal("clear")
                        .executes { executeClear(it, null) }
                        .then(
                            argument("radius", IntegerArgumentType.integer(1, 500))
                                .executes { executeClear(it, IntegerArgumentType.getInteger(it, "radius")) }
                        )
                )
                .then(
                    literal("status")
                        .executes(::executeStatus)
                )
        )
    }

    private fun executeSpawn(
        context: CommandContext<CommandSourceStack>,
        typeArg: String?,
        maxSizeArg: Int?,
        loyalArg: Boolean?,
        speedArg: Int?,
        sneakyArg: Boolean?,
        aggroRangeArg: Int?,
        lavaStomachArg: Boolean?
    ): Int {
        val source = context.source
        val player = source.getPlayerOrException()
        val world = source.level

        var color: AmoebaColor = AmoebaColor.random(java.util.Random())
        var customBlock: Block? = null

        if (typeArg != null) {
            val parsedColor = AmoebaColor.fromName(typeArg)
            if (parsedColor != null) {
                color = parsedColor
            } else {
                val blockId = if (typeArg.contains(":")) typeArg else "minecraft:$typeArg"
                val block = try {
                    val resolved = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId))
                    val resolvedId = BuiltInRegistries.BLOCK.getKey(resolved).toString()
                    if (resolvedId == "minecraft:air" && blockId != "minecraft:air") null else resolved
                } catch (_: Exception) {
                    null
                }
                if (block == null) {
                    source.sendFailure(Component.literal(
                        "Unknown type: $typeArg. Use a color (${AmoebaColor.entries.joinToString(", ") { it.name.lowercase() }}) " +
                            "or a block ID (e.g. netherite_block, lava, amethyst_block)"
                    ))
                    return 0
                }
                customBlock = block
            }
        }

        val maxSize = maxSizeArg ?: DEFAULT_SIZE
        val loyal = loyalArg ?: true
        val speed = speedArg ?: Amoeba.DEFAULT_SPEED
        val sneaky = sneakyArg ?: false
        val aggroRange = aggroRangeArg ?: Amoeba.DEFAULT_AGGRO_RANGE
        val lavaStomach = lavaStomachArg ?: false

        // Raycast to where the player is looking, up to MAX_SPAWN_RANGE blocks
        val pos = raycastSpawnPos(player) ?: run {
            source.sendFailure(Component.literal("Look at a block to spawn the blockmoeba there (max ${MAX_SPAWN_RANGE} blocks)."))
            return 0
        }

        val amoeba = BlockmoebaSimulation.spawn(world, pos, color, maxSize, loyal, speed, sneaky, aggroRange, lavaStomach, customBlock)

        val traits = mutableListOf<String>()
        traits.add(if (loyal) "loyal" else "HOSTILE")
        if (sneaky) traits.add("sneaky")
        if (lavaStomach) traits.add("lava stomach")
        val traitText = traits.joinToString(", ")

        source.sendSuccess({
            Component.literal(
                "Spawned ${amoeba.name}! ${amoeba.size} blocks, speed $speed, $traitText. " +
                    when {
                        sneaky && !loyal -> "It only moves when you're not looking... and it's hostile!"
                        sneaky -> "It only moves when you're not looking..."
                        !loyal -> "Watch out - it's coming for you!"
                        else -> "It will roam peacefully."
                    }
            )
        }, true)

        return 1
    }

    private fun executeRace(
        context: CommandContext<CommandSourceStack>,
        racerCountArg: Int?,
        distanceArg: Int?,
        maxSizeArg: Int?
    ): Int {
        val source = context.source
        val player = source.getPlayerOrException()
        val world = source.level
        val pos = player.blockPosition().above()

        val racerCount = racerCountArg ?: DEFAULT_RACERS
        val distance = distanceArg ?: DEFAULT_RACE_DISTANCE
        val maxSize = maxSizeArg ?: RACE_MAX_SIZE

        val race = BlockmoebaSimulation.startRace(world, pos, distance, racerCount, maxSize)

        source.sendSuccess({
            Component.literal(
                "Race started! $racerCount blockmoebas racing $distance blocks! " +
                    "Racers: ${race.racers.joinToString(", ") { it.name }}. " +
                    "Gold = start, Diamond = finish!"
            )
        }, true)

        return 1
    }

    private fun executeClear(context: CommandContext<CommandSourceStack>, radiusArg: Int?): Int {
        val source = context.source

        val count = if (radiusArg != null) {
            val player = source.getPlayerOrException()
            BlockmoebaSimulation.clearNearby(source.level, player.blockPosition(), radiusArg)
        } else {
            BlockmoebaSimulation.clearAll()
        }

        source.sendSuccess({
            Component.literal("Cleared $count blockmoeba(s).")
        }, true)

        return 1
    }

    private fun executeStatus(context: CommandContext<CommandSourceStack>): Int {
        context.source.sendSuccess({
            Component.literal(BlockmoebaSimulation.status())
        }, false)
        return 1
    }

    private fun suggestTypes(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        AmoebaColor.entries.forEach { builder.suggest(it.name.lowercase()) }
        SUGGESTED_BLOCKS.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private val SUGGESTED_BLOCKS = listOf(
        "netherite_block",
        "lava",
        "magma_block",
        "fire_coral_block",
        "amethyst_block",
        "sculk",
        "obsidian",
        "crying_obsidian",
        "glowstone",
        "sea_lantern",
        "prismarine",
        "copper_block",
        "gold_block",
        "diamond_block",
        "emerald_block",
        "redstone_block",
        "slime_block",
        "honey_block",
        "ice",
        "packed_ice",
        "blue_ice",
        "ancient_debris",
        "end_stone",
        "purpur_block",
        "tnt"
    )

    private fun raycastSpawnPos(player: ServerPlayer): BlockPos? {
        val eyePos = player.getEyePosition(1.0f)
        val lookVec = player.lookAngle
        val endPos = eyePos.add(
            lookVec.x * MAX_SPAWN_RANGE,
            lookVec.y * MAX_SPAWN_RANGE,
            lookVec.z * MAX_SPAWN_RANGE
        )

        val clipContext = ClipContext(
            eyePos, endPos,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        )
        val hitResult = player.level().clip(clipContext)

        if (hitResult.type == HitResult.Type.BLOCK) {
            val blockHit = hitResult as BlockHitResult
            // Spawn on top of the hit block
            return blockHit.blockPos.relative(blockHit.direction)
        }

        // Looking at sky - place at the end of the ray on the ground
        val targetX = (eyePos.x + lookVec.x * MAX_SPAWN_RANGE).toInt()
        val targetZ = (eyePos.z + lookVec.z * MAX_SPAWN_RANGE).toInt()
        val groundY = player.level().getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
            targetX, targetZ
        )
        return BlockPos(targetX, groundY, targetZ)
    }

    private const val MAX_SPAWN_RANGE = 100.0
    private const val DEFAULT_SIZE = 3000
    private const val DEFAULT_RACERS = 4
    private const val DEFAULT_RACE_DISTANCE = 50
    private const val RACE_MAX_SIZE = 1000
}
