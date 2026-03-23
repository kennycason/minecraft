package com.kenny.supermetroid

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory

object SuperMetroidMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("super_metroid")

    override fun onInitialize() {
        logger.info("Super Metroid Rooms loading...")

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SuperMetroidCommand.register(dispatcher)
        }

        logger.info("Super Metroid Rooms loaded! Use /sm create <room_name>")
    }
}
