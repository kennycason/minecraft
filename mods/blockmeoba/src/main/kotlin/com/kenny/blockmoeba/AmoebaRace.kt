package com.kenny.blockmoeba

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import java.util.Random

class AmoebaRace(
    private val world: ServerLevel,
    private val startPos: BlockPos,
    val distance: Int,
    private val racerCount: Int,
    private val maxSize: Int
) {
    val racers = mutableListOf<Amoeba>()
    private var winner: Amoeba? = null
    private var tickCount = 0
    var isFinished = false
        private set
    private val random = Random()

    fun start() {
        val finishX = startPos.x + distance

        val colors = AmoebaColor.entries.filter { !it.isCamo }
            .shuffled(kotlin.random.Random(random.nextLong()))
            .take(racerCount.coerceAtMost(AmoebaColor.entries.size - 1))

        val spacing = 5
        val goldState = blockFor("minecraft:gold_block").defaultBlockState()
        val diamondState = blockFor("minecraft:diamond_block").defaultBlockState()

        for (i in 0 until racerCount) {
            val z = startPos.z + i * spacing
            world.setBlock(BlockPos(startPos.x - 1, startPos.y - 1, z), goldState, 2)
            world.setBlock(BlockPos(startPos.x - 1, startPos.y, z), goldState, 2)
        }

        for (i in 0 until racerCount) {
            val z = startPos.z + i * spacing
            for (dy in -1..2) {
                world.setBlock(BlockPos(finishX, startPos.y + dy, z), diamondState, 2)
            }
        }

        colors.forEachIndexed { index, color ->
            val spawnPos = BlockPos(startPos.x, startPos.y, startPos.z + index * spacing)
            val targetPos = BlockPos(finishX, startPos.y, startPos.z + index * spacing)
            val amoeba = Amoeba(
                color = color,
                world = world,
                centerPos = spawnPos,
                maxSize = maxSize,
                targetPos = targetPos,
                name = "${color.displayName} Racer"
            )
            amoeba.spawn()
            racers.add(amoeba)
        }
    }

    fun tick() {
        if (isFinished) return
        tickCount++

        racers.forEach { racer ->
            if (racer.isAlive) {
                racer.tick()
            }
        }

        if (winner == null) {
            val finisher = racers.find { it.reachedTarget }
            if (finisher != null) {
                winner = finisher
                announceWinner(finisher)
                isFinished = true
            }
        }

        if (tickCount > 600) {
            if (winner == null) {
                announceTimeout()
            }
            isFinished = true
        }
    }

    fun statusText(): String {
        if (winner != null) return "Winner: ${winner!!.name}!"
        return racers.joinToString(", ") { "${it.name}: ${it.size} blocks" }
    }

    private fun announceWinner(amoeba: Amoeba) {
        val message = Component.literal("${amoeba.name} wins the race!")
        world.players().forEach { player ->
            player.sendSystemMessage(message)
        }

        val winPos = amoeba.origin
        val glowState = blockFor("minecraft:glowstone").defaultBlockState()
        for (dy in 0..3) {
            world.setBlock(BlockPos(winPos.x, winPos.y + dy + 2, winPos.z), glowState, 2)
        }
    }

    private fun announceTimeout() {
        val biggest = racers.maxByOrNull { it.size }
        val message = if (biggest != null) {
            Component.literal("Race timed out! ${biggest.name} was biggest with ${biggest.size} blocks!")
        } else {
            Component.literal("Race timed out!")
        }
        world.players().forEach { player ->
            player.sendSystemMessage(message)
        }
    }

    companion object {
        private fun blockFor(id: String): Block =
            BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))
    }
}
