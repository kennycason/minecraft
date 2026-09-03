package com.kenny.blockmoeba

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.Random

class Amoeba(
    val color: AmoebaColor,
    val world: ServerLevel,
    private var centerPos: BlockPos,
    val maxSize: Int,
    val speed: Int = DEFAULT_SPEED,
    val targetPos: BlockPos? = null,
    val loyal: Boolean = true,
    val sneaky: Boolean = false,
    val customBlock: Block? = null,
    private val seed: Long = Random().nextLong(),
    val name: String = buildName(color, customBlock)
) {
    private val random = Random(seed)
    private val cells = LinkedHashSet<BlockPos>()
    private val edgeCells = LinkedHashSet<BlockPos>()
    private val savedBlocks = HashMap<BlockPos, BlockState>()
    private var age = 0
    private var breathPhase = 0.0
    var isAlive = true
        private set
    var reachedTarget = false
        private set

    val size: Int get() = cells.size
    val origin: BlockPos get() = centerPos

    fun spawn() {
        // Place all blocks at once as a giant blob
        placeCell(centerPos, isCore = true)

        // Grow outward in waves until we reach maxSize
        var frontier = mutableListOf(centerPos)
        while (cells.size < maxSize && frontier.isNotEmpty()) {
            val nextFrontier = mutableListOf<BlockPos>()
            frontier.shuffle(kotlin.random.Random(random.nextLong()))
            for (pos in frontier) {
                if (cells.size >= maxSize) break
                for (offset in GROWTH_OFFSETS) {
                    if (cells.size >= maxSize) break
                    val neighbor = pos.offset(offset[0], offset[1], offset[2])
                    if (!cells.contains(neighbor) && canGrowInto(neighbor)) {
                        // Gravity bias during spawn too
                        if (offset[1] == 1 && random.nextDouble() > 0.3) continue
                        placeCell(neighbor, isCore = false)
                        nextFrontier.add(neighbor)
                    }
                }
            }
            frontier = nextFrontier
        }

        updateCenter()
        refreshEdges()
    }

    fun tick() {
        if (!isAlive) return
        age++
        breathPhase += 0.15

        checkForBrokenCells()
        if (!isAlive) return

        // Weeping angel mode: freeze when any player is looking at us
        if (sneaky && isBeingWatched()) return

        fluidMove()

        if (age % 5 == 0) {
            breathe()
        }

        if (!loyal && age % 3 == 0) {
            huntNearestPlayer()
        }

        if (targetPos != null && !reachedTarget) {
            checkTargetReached()
        }
    }

    fun remove() {
        isAlive = false
        cells.forEach { pos ->
            val saved = savedBlocks[pos]
            if (saved != null) {
                world.setBlock(pos, saved, 2)
            } else {
                world.setBlock(pos, AIR_STATE, 2)
            }
        }
        cells.clear()
        edgeCells.clear()
        savedBlocks.clear()
    }

    fun notifyBlockBroken(pos: BlockPos) {
        if (cells.remove(pos)) {
            edgeCells.remove(pos)
            savedBlocks.remove(pos)
            refreshEdges()
            if (cells.isEmpty()) {
                isAlive = false
            }
        }
    }

    fun containsBlock(pos: BlockPos): Boolean = cells.contains(pos)

    private fun isBeingWatched(): Boolean {
        val minX = cells.minOf { it.x } - 1
        val minY = cells.minOf { it.y } - 1
        val minZ = cells.minOf { it.z } - 1
        val maxX = cells.maxOf { it.x } + 1
        val maxY = cells.maxOf { it.y } + 1
        val maxZ = cells.maxOf { it.z } + 1
        val cx = (minX + maxX) / 2.0
        val cy = (minY + maxY) / 2.0
        val cz = (minZ + maxZ) / 2.0
        val radius = kotlin.math.sqrt(
            ((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY) + (maxZ - minZ) * (maxZ - minZ)).toDouble()
        ) / 2.0 + 2.0

        for (player in world.players()) {
            val eyePos = player.getEyePosition(1.0f)
            val lookVec = player.lookAngle
            val toCenter = Vec3(cx - eyePos.x, cy - eyePos.y, cz - eyePos.z)
            val dist = toCenter.length()

            if (dist > 80.0) continue // too far to see
            if (dist < 1.0) return true // standing inside it

            val normalized = toCenter.normalize()
            val dot = lookVec.dot(normalized)

            // Player is looking roughly toward the amoeba (within ~45 degree cone)
            if (dot > 0.7) return true
        }
        return false
    }

    private fun checkForBrokenCells() {
        val broken = mutableListOf<BlockPos>()
        for (pos in cells) {
            val state = world.getBlockState(pos)
            if (!isAmoebaBlock(state)) {
                broken.add(pos)
            }
        }
        if (broken.isNotEmpty()) {
            broken.forEach { pos ->
                cells.remove(pos)
                edgeCells.remove(pos)
                savedBlocks.remove(pos)
            }
            refreshEdges()
        }
        if (cells.isEmpty()) {
            isAlive = false
        }
    }

    private fun isAmoebaBlock(state: BlockState): Boolean {
        val block = state.block
        if (customBlock != null) return block == customBlock
        if (color.isCamo) return true
        return block == color.bodyBlock || block == color.coreBlock
    }

    private fun fluidMove() {
        if (cells.size <= 6) return

        val moveCount = speed.coerceAtMost(cells.size / 3).coerceAtLeast(1)
        val moveTarget = currentMoveTarget()

        // Source cells: pick from the REAR (farthest from target) - creates rolling effect
        val movable = mutableListOf<BlockPos>()
        for (pos in cells) {
            if (isEdge(pos)) movable.add(pos)
        }
        movable.sortByDescending { it.distSqr(moveTarget) }
        val toMove = movable.take(moveCount)

        if (toMove.isEmpty()) return

        // Destination: growth spots closest to target + downward bias
        val growthSpots = mutableListOf<Pair<BlockPos, Double>>()
        for (edge in edgeCells) {
            for (offset in GROWTH_OFFSETS) {
                val neighbor = edge.offset(offset[0], offset[1], offset[2])
                if (!cells.contains(neighbor) && canGrowInto(neighbor)) {
                    var score = -neighbor.distSqr(moveTarget).toDouble() * 0.01
                    if (neighbor.y < edge.y) score += 50.0
                    if (neighbor.y > edge.y) score -= 30.0
                    growthSpots.add(neighbor to score)
                }
            }
        }
        if (growthSpots.isEmpty()) return

        growthSpots.sortByDescending { it.second }
        // Deduplicate destinations
        val destinations = growthSpots.map { it.first }.distinct()

        val teleportCount = minOf(toMove.size, destinations.size)

        for (i in 0 until teleportCount) {
            val fromPos = toMove[i]
            val toPos = destinations[i]

            cells.remove(fromPos)
            edgeCells.remove(fromPos)
            val saved = savedBlocks.remove(fromPos)
            if (saved != null) {
                world.setBlock(fromPos, saved, 2)
            } else {
                world.setBlock(fromPos, AIR_STATE, 2)
            }

            placeCell(toPos, isCore = false)
        }

        updateCenter()
        refreshEdges()
    }

    private fun currentMoveTarget(): BlockPos {
        if (!loyal) {
            val nearest = findNearestPlayer()
            if (nearest != null) {
                // Target the player's position directly - don't stop until we're centered on them
                return nearest.blockPosition()
            }
        }
        if (targetPos != null) return targetPos
        return centerPos.offset(
            random.nextInt(11) - 5,
            -2,
            random.nextInt(11) - 5
        )
    }

    private fun huntNearestPlayer() {
        // Hostile mode: always aggressively move toward the player
        // The fluidMove already handles this via currentMoveTarget()
        // This method provides EXTRA movement pulses for aggressive pursuit
        val player = findNearestPlayer() ?: return
        val playerPos = player.blockPosition()
        val dist = centerPos.distSqr(playerPos)

        // Close range: extra aggressive, move more blocks
        if (dist < 225) { // within 15 blocks
            fluidMove()
            fluidMove()
        } else if (dist < 900) { // within 30 blocks
            fluidMove()
        }
    }

    private fun findNearestPlayer(): ServerPlayer? {
        return world.players()
            .filter { it.blockPosition().distSqr(centerPos) < 10000 } // within 100 blocks
            .minByOrNull { it.blockPosition().distSqr(centerPos) }
    }

    private fun breathe() {
        val pulse = kotlin.math.sin(breathPhase)
        if (pulse > 0.5 && cells.size < maxSize + 10) {
            // Grow a few cells outward
            repeat(3) { growOne() }
        } else if (pulse < -0.5 && cells.size > maxSize - 10) {
            // Retract a few edge cells
            val edgeList = edgeCells.toList()
            val picked = edgeList.shuffled(kotlin.random.Random(random.nextLong())).take(3)
            picked.forEach { candidate ->
                if (cells.size <= 6) return
                cells.remove(candidate)
                edgeCells.remove(candidate)
                val saved = savedBlocks.remove(candidate)
                if (saved != null) {
                    world.setBlock(candidate, saved, 2)
                } else {
                    world.setBlock(candidate, AIR_STATE, 2)
                }
            }
            refreshEdges()
        }
    }

    private fun growOne() {
        if (edgeCells.isEmpty()) return
        val edge = edgeCells.random(kotlin.random.Random(random.nextLong()))
        for (offset in GROWTH_OFFSETS.toList().shuffled(kotlin.random.Random(random.nextLong()))) {
            val neighbor = edge.offset(offset[0], offset[1], offset[2])
            if (!cells.contains(neighbor) && canGrowInto(neighbor)) {
                placeCell(neighbor, isCore = false)
                return
            }
        }
    }

    private fun updateCenter() {
        if (cells.isEmpty()) return
        var sx = 0L; var sy = 0L; var sz = 0L
        cells.forEach { pos ->
            sx += pos.x; sy += pos.y; sz += pos.z
        }
        centerPos = BlockPos(
            (sx / cells.size).toInt(),
            (sy / cells.size).toInt(),
            (sz / cells.size).toInt()
        )
    }

    private fun placeCell(pos: BlockPos, isCore: Boolean) {
        if (!cells.contains(pos)) {
            val existing = world.getBlockState(pos)
            if (!existing.isAir) {
                savedBlocks[pos] = existing
            }
        }
        cells.add(pos)
        val block = chooseBlock(pos, isCore)
        world.setBlock(pos, block.defaultBlockState(), 2)
        updateEdgesAround(pos)
    }

    private fun chooseBlock(pos: BlockPos, isCore: Boolean): Block {
        if (customBlock != null) return customBlock
        if (color.isCamo) return chooseCamoBlock(pos)
        return if (isCore) color.coreBlock else color.bodyBlock
    }

    private fun chooseCamoBlock(pos: BlockPos): Block {
        val neighbors = mutableMapOf<Block, Int>()
        for (offset in SAMPLE_OFFSETS) {
            val neighbor = pos.offset(offset[0], offset[1], offset[2])
            if (cells.contains(neighbor)) continue
            val state = world.getBlockState(neighbor)
            if (state.isAir) continue
            neighbors[state.block] = (neighbors[state.block] ?: 0) + 1
        }

        if (neighbors.isEmpty()) {
            return when {
                pos.y < 0 -> AmoebaColor.blockFor("minecraft:deepslate")
                pos.y < 60 -> AmoebaColor.blockFor("minecraft:stone")
                pos.y < 64 -> AmoebaColor.blockFor("minecraft:dirt")
                else -> AmoebaColor.blockFor("minecraft:grass_block")
            }
        }

        return neighbors.maxByOrNull { it.value }?.key ?: color.coreBlock
    }

    private fun updateEdgesAround(pos: BlockPos) {
        if (isEdge(pos)) edgeCells.add(pos) else edgeCells.remove(pos)
        for (offset in GROWTH_OFFSETS) {
            val neighbor = pos.offset(offset[0], offset[1], offset[2])
            if (cells.contains(neighbor)) {
                if (isEdge(neighbor)) edgeCells.add(neighbor) else edgeCells.remove(neighbor)
            }
        }
    }

    private fun refreshEdges() {
        edgeCells.clear()
        cells.forEach { pos ->
            if (isEdge(pos)) edgeCells.add(pos)
        }
    }

    private fun isEdge(pos: BlockPos): Boolean {
        for (offset in GROWTH_OFFSETS) {
            val neighbor = pos.offset(offset[0], offset[1], offset[2])
            if (!cells.contains(neighbor)) return true
        }
        return false
    }

    private fun canGrowInto(pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        if (state.isAir) return true
        val blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        return blockId.contains("grass") && !blockId.contains("block") ||
            blockId.contains("fern") ||
            blockId.contains("snow") && !blockId.contains("snow_block") ||
            blockId.contains("flower") ||
            blockId.contains("mushroom") && !blockId.contains("block") ||
            blockId.contains("vine") ||
            blockId.contains("dead_bush") ||
            blockId.contains("seagrass") ||
            blockId.contains("kelp") ||
            blockId.contains("carpet")
    }

    private fun checkTargetReached() {
        for (cell in cells) {
            if (cell.distSqr(targetPos!!) <= TARGET_REACH_DISTANCE_SQ) {
                reachedTarget = true
                return
            }
        }
    }

    companion object {
        const val DEFAULT_SPEED = 15
        private const val TARGET_REACH_DISTANCE_SQ = 9L

        private val AIR_STATE by lazy {
            BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:air")).defaultBlockState()
        }

        private val CARDINAL_OFFSETS = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
        )

        private val GROWTH_OFFSETS = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1),
            intArrayOf(0, 1, 0), intArrayOf(0, -1, 0)
        )

        private val SAMPLE_OFFSETS = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1),
            intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),
            intArrayOf(2, 0, 0), intArrayOf(-2, 0, 0),
            intArrayOf(0, 0, 2), intArrayOf(0, 0, -2),
            intArrayOf(0, 2, 0), intArrayOf(0, -2, 0),
            intArrayOf(1, 0, 1), intArrayOf(-1, 0, -1),
            intArrayOf(1, 0, -1), intArrayOf(-1, 0, 1),
            intArrayOf(3, 0, 0), intArrayOf(-3, 0, 0),
            intArrayOf(0, 0, 3), intArrayOf(0, 0, -3)
        )

        private fun buildName(color: AmoebaColor, customBlock: Block?): String {
            if (customBlock != null) {
                val blockId = BuiltInRegistries.BLOCK.getKey(customBlock).toString()
                val shortName = blockId.removePrefix("minecraft:").replace('_', ' ')
                return "$shortName Blockmoeba"
            }
            return "${color.displayName} Blockmoeba"
        }
    }
}
