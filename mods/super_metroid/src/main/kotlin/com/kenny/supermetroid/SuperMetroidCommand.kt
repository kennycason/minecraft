package com.kenny.supermetroid

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object SuperMetroidCommand {

    private val AVAILABLE_ROOMS = listOf(
        "east_cactus_alley_room",
        "crab_maze"
    )

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
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
        AVAILABLE_ROOMS.forEach { builder.suggest(it) }
        return builder.buildFuture()
    }

    private fun executeCreate(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val world = source.world
        val pos = player.blockPos
        val roomName = StringArgumentType.getString(context, "room")

        if (roomName !in AVAILABLE_ROOMS) {
            source.sendFeedback({
                Text.literal("Unknown room: $roomName. Use /sm rooms to see available rooms.")
            }, false)
            return 0
        }

        source.sendFeedback({
            Text.literal("Generating Super Metroid room: $roomName...")
        }, true)

        val generator = RoomGenerator(world, pos, roomName)
        val result = generator.generate()

        source.sendFeedback({
            Text.literal("Room created! ${result.tilesWide}x${result.tilesTall} tiles, ${result.solidCount} solid blocks, ${result.airCount} air spaces.")
        }, true)

        return 1
    }

    private fun executeListRooms(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        source.sendFeedback({
            Text.literal("Available rooms: ${AVAILABLE_ROOMS.joinToString(", ")}")
        }, false)
        return 1
    }
}
