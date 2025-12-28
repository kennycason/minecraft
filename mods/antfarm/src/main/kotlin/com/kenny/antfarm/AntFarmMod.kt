package com.kenny.antfarm

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory

object AntFarmMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("antfarm")

    override fun onInitialize() {
        logger.info("🐜 AntFarm Mod loading...")
        
        // Register the /antfarm command
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            AntFarmCommand.register(dispatcher)
        }
        
        logger.info("🐜 AntFarm Mod loaded! Use /antfarm create <width> <height> <depth>")
    }
}

