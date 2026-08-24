package com.kenny.supermetroid

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.slf4j.LoggerFactory

object SuperMetroidMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("super_metroid")

    override fun onInitialize() {
        logger.info("Super Metroid Rooms loading...")

        SmTileBlocks.register()
        logger.info("Registered 1024 SM tile blocks")

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SuperMetroidCommand.register(dispatcher)
            Maze3dCommand.register(dispatcher)
        }

        ServerTickEvents.END_SERVER_TICK.register(Maze3dBuildQueue::tick)

        logger.info(
            "Super Metroid Rooms loaded! Use /sm create <room_name> or " +
                "/maze3d <width> <height> <depth> [wall_block] [max_chamber_size] [cycles] [seed]"
        )
    }
}
