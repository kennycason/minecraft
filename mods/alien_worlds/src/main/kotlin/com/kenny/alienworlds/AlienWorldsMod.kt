package com.kenny.alienworlds

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory

object AlienWorldsMod : ModInitializer {
    const val MOD_ID = "alien_worlds"
    val logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("Alien Worlds loading...")

        AlienBlocks.register()
        AlienChunkGenerator.register()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            AlienWorldCommand.register(dispatcher)
        }

        logger.info("Alien Worlds loaded! Use /alienworld to explore.")
    }
}
