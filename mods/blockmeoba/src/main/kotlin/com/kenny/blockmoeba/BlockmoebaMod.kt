package com.kenny.blockmoeba

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.level.ServerLevel
import org.slf4j.LoggerFactory

object BlockmoebaMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("blockmoeba")

    override fun onInitialize() {
        logger.info("Blockmoeba loading...")

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BlockmoebaCommand.register(dispatcher)
        }

        ServerTickEvents.END_SERVER_TICK.register(BlockmoebaSimulation::tick)

        PlayerBlockBreakEvents.BEFORE.register { world, _, pos, _, _ ->
            if (world is ServerLevel) {
                BlockmoebaSimulation.onBlockBroken(world, pos)
            }
            true
        }

        logger.info("Blockmoeba loaded! Use /blockmoeba spawn [type] [size] [loyal] [speed] [sneaky] [aggro_range]")
    }
}
