package com.kenny.blockmoeba

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import org.slf4j.LoggerFactory

object BlockmoebaSimulation {
    private val logger = LoggerFactory.getLogger("blockmoeba")
    private val amoebas = mutableListOf<Amoeba>()
    private val races = mutableListOf<AmoebaRace>()
    private var tickCounter = 0

    fun spawn(
        world: ServerLevel,
        origin: BlockPos,
        color: AmoebaColor,
        maxSize: Int,
        loyal: Boolean,
        speed: Int = Amoeba.DEFAULT_SPEED,
        sneaky: Boolean = false,
        aggroRange: Int = Amoeba.DEFAULT_AGGRO_RANGE,
        lavaStomach: Boolean = false,
        customBlock: Block? = null
    ): Amoeba {
        val amoeba = Amoeba(
            color = color,
            world = world,
            centerPos = origin,
            maxSize = maxSize,
            speed = speed,
            loyal = loyal,
            sneaky = sneaky,
            aggroRange = aggroRange,
            lavaStomach = lavaStomach,
            customBlock = customBlock
        )
        amoeba.spawn()
        amoebas.add(amoeba)
        logger.info("Spawned ${amoeba.name} at $origin (max: $maxSize, speed: $speed, loyal: $loyal, sneaky: $sneaky, aggroRange: $aggroRange, lavaStomach: $lavaStomach)")
        return amoeba
    }

    fun startRace(
        world: ServerLevel,
        startPos: BlockPos,
        distance: Int,
        racerCount: Int,
        maxSize: Int
    ): AmoebaRace {
        val race = AmoebaRace(world, startPos, distance, racerCount, maxSize)
        race.start()
        races.add(race)
        return race
    }

    fun onBlockBroken(world: ServerLevel, pos: BlockPos) {
        for (amoeba in amoebas) {
            if (amoeba.isAlive && amoeba.world == world && amoeba.containsBlock(pos)) {
                amoeba.notifyBlockBroken(pos)
                if (!amoeba.isAlive) {
                    val message = Component.literal("${amoeba.name} has been destroyed!")
                    world.players().forEach { it.sendSystemMessage(message) }
                }
                return
            }
        }
        for (race in races) {
            for (racer in race.racers) {
                if (racer.isAlive && racer.world == world && racer.containsBlock(pos)) {
                    racer.notifyBlockBroken(pos)
                    return
                }
            }
        }
    }

    fun clearAll(): Int {
        var count = 0
        amoebas.forEach { it.remove(); count++ }
        amoebas.clear()
        races.forEach { race ->
            race.racers.forEach { it.remove(); count++ }
        }
        races.clear()
        return count
    }

    fun clearNearby(world: ServerLevel, center: BlockPos, radius: Int): Int {
        val radiusSq = radius.toLong() * radius.toLong()
        var count = 0

        val toRemove = amoebas.filter {
            it.world == world && it.origin.distSqr(center) <= radiusSq
        }
        toRemove.forEach { it.remove(); count++ }
        amoebas.removeAll(toRemove.toSet())

        val racesToRemove = races.filter { race ->
            race.racers.any { it.world == world && it.origin.distSqr(center) <= radiusSq }
        }
        racesToRemove.forEach { race ->
            race.racers.forEach { it.remove(); count++ }
        }
        races.removeAll(racesToRemove.toSet())

        return count
    }

    fun status(): String {
        val lines = mutableListOf<String>()
        if (amoebas.isEmpty() && races.isEmpty()) {
            return "No active blockmoebas."
        }
        if (amoebas.isNotEmpty()) {
            lines.add("Active blockmoebas: ${amoebas.size}")
            amoebas.forEach { a ->
                val loyalText = if (a.loyal) "loyal" else "hostile"
                val sneakyText = if (a.sneaky) ", sneaky" else ""
                val aggroText = if (!a.loyal) ", aggro ${a.aggroRange}" else ""
                lines.add("  ${a.name}: ${a.size}/${a.maxSize} blocks, speed ${a.speed} ($loyalText$sneakyText$aggroText)")
            }
        }
        if (races.isNotEmpty()) {
            lines.add("Active races: ${races.size}")
            races.forEach { race ->
                lines.add("  Race (${race.racers.size} racers, distance ${race.distance}): ${race.statusText()}")
            }
        }
        return lines.joinToString("\n")
    }

    fun tick(server: MinecraftServer) {
        tickCounter++
        if (tickCounter % TICK_INTERVAL != 0) return

        val deadAmoebas = mutableListOf<Amoeba>()
        amoebas.forEach { amoeba ->
            if (amoeba.isAlive) {
                amoeba.tick()
            }
            if (!amoeba.isAlive) {
                deadAmoebas.add(amoeba)
            }
        }
        amoebas.removeAll(deadAmoebas.toSet())

        val finishedRaces = mutableListOf<AmoebaRace>()
        races.forEach { race ->
            race.tick()
            if (race.isFinished) {
                finishedRaces.add(race)
            }
        }
        races.removeAll(finishedRaces.toSet())
    }

    private const val TICK_INTERVAL = 3
}
