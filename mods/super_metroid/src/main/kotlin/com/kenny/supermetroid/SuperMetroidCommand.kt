package com.kenny.supermetroid

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component

object SuperMetroidCommand {

    // Legacy image-based rooms
    private val LEGACY_ROOMS = listOf(
        "east_cactus_alley_room",
        "crab_maze"
    )

    // Tile-based rooms loaded from JSON (roomId -> display name)
    private val TILE_ROOMS = mapOf(
        "92FD" to "Parlor and Alcatraz"
    )

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("sm")
                .then(
                    literal("create")
                        .then(
                            argument("room", StringArgumentType.string())
                                .suggests { _, builder -> suggestRooms(builder) }
                                .executes(::executeCreate)
                        )
                )
                .then(
                    literal("rooms")
                        .executes(::executeListRooms)
                )
        )
    }

    private fun suggestRooms(builder: SuggestionsBuilder): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        LEGACY_ROOMS.forEach { builder.suggest(it) }
        TILE_ROOMS.keys.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun executeCreate(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val player = source.getPlayerOrException()
        val world = source.level
        val pos = player.blockPosition()
        val roomArg = StringArgumentType.getString(context, "room")

        // Check if it's a tile-based room (by room ID)
        if (roomArg.uppercase() in TILE_ROOMS) {
            val roomId = roomArg.uppercase()
            val roomName = TILE_ROOMS[roomId]!!

            source.sendSuccess({
                Component.literal("Generating Super Metroid room: $roomName ($roomId) [tile-based]...")
            }, true)

            val generator = TileRoomGenerator(world, pos, roomId)
            val result = generator.generate()

            source.sendSuccess({
                Component.literal("Room created! ${result.tilesWide}x${result.tilesTall} tiles, ${result.solidCount} solid, ${result.airCount} air.")
            }, true)

            return 1
        }

        // Legacy image-based rooms
        if (roomArg in LEGACY_ROOMS) {
            source.sendSuccess({
                Component.literal("Generating Super Metroid room: $roomArg [legacy]...")
            }, true)

            val generator = RoomGenerator(world, pos, roomArg)
            val result = generator.generate()

            source.sendSuccess({
                Component.literal("Room created! ${result.tilesWide}x${result.tilesTall} tiles, ${result.solidCount} solid blocks, ${result.airCount} air spaces.")
            }, true)

            return 1
        }

        source.sendSuccess({
            Component.literal("Unknown room: $roomArg. Use /sm rooms to see available rooms.")
        }, false)
        return 0
    }

    private fun executeListRooms(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val lines = mutableListOf<String>()
        lines.add("--- Tile-based rooms ---")
        TILE_ROOMS.forEach { (id, name) -> lines.add("  $id - $name") }
        lines.add("--- Legacy rooms ---")
        LEGACY_ROOMS.forEach { lines.add("  $it") }
        source.sendSuccess({
            Component.literal(lines.joinToString("\n"))
        }, false)
        return 1
    }
}
