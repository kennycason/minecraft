package com.kenny.supermetroid

import java.util.Random

enum class Maze3dVoxel {
    WALL,
    AIR,
    CLIMBABLE,
    TREASURE_CHEST
}

data class Maze3dRenderPlan(
    val blockWidth: Int,
    val blockHeight: Int,
    val blockDepth: Int,
    val minRelativeY: Int,
    val maxRelativeY: Int,
    val lootSeed: Long,
    private val voxels: ByteArray
) {
    init {
        require(blockWidth > 0) { "blockWidth must be greater than zero" }
        require(blockHeight > 0) { "blockHeight must be greater than zero" }
        require(blockDepth > 0) { "blockDepth must be greater than zero" }
        require(maxRelativeY - minRelativeY + 1 == blockHeight) {
            "relative Y range must match blockHeight"
        }
        require(voxels.size == blockWidth * blockHeight * blockDepth) {
            "voxel count does not match rendered dimensions"
        }
    }

    val volume: Int get() = voxels.size

    val wallCount: Int by lazy { voxels.count { it.toInt() == Maze3dVoxel.WALL.ordinal } }
    val airCount: Int by lazy { voxels.count { it.toInt() == Maze3dVoxel.AIR.ordinal } }
    val climbableCount: Int by lazy { voxels.count { it.toInt() == Maze3dVoxel.CLIMBABLE.ordinal } }
    val treasureChestCount: Int by lazy { voxels.count { it.toInt() == Maze3dVoxel.TREASURE_CHEST.ordinal } }

    fun relativeY(localY: Int): Int {
        require(localY in 0 until blockHeight) { "localY is outside rendered height: $localY" }
        return minRelativeY + localY
    }

    fun voxelAt(localX: Int, relativeY: Int, localZ: Int): Maze3dVoxel {
        require(localX in 0 until blockWidth) { "localX is outside rendered width: $localX" }
        require(relativeY in minRelativeY..maxRelativeY) {
            "relativeY is outside rendered height: $relativeY"
        }
        require(localZ in 0 until blockDepth) { "localZ is outside rendered depth: $localZ" }
        val localY = relativeY - minRelativeY
        return Maze3dVoxel.entries[voxels[indexOf(localX, localY, localZ)].toInt()]
    }

    internal fun voxelAtIndex(index: Int): Maze3dVoxel {
        require(index in voxels.indices) { "voxel index is outside rendered volume: $index" }
        return Maze3dVoxel.entries[voxels[index].toInt()]
    }

    internal fun localXAtIndex(index: Int): Int = index % blockWidth

    internal fun localZAtIndex(index: Int): Int = (index / blockWidth) % blockDepth

    internal fun relativeYAtIndex(index: Int): Int {
        val localY = index / (blockWidth * blockDepth)
        return relativeY(localY)
    }

    private fun indexOf(localX: Int, localY: Int, localZ: Int): Int =
        (localY * blockDepth + localZ) * blockWidth + localX
}

data class Maze3dBlockDimensions(
    val width: Int,
    val height: Int,
    val depth: Int,
    val minRelativeY: Int,
    val maxRelativeY: Int,
    val volume: Int
)

private data class Maze3dChamber(
    val cell: Maze3dCell,
    val minX: Int,
    val maxX: Int,
    val footY: Int,
    val height: Int,
    val minZ: Int,
    val maxZ: Int,
    val anchorX: Int,
    val anchorZ: Int
) {
    val width: Int get() = maxX - minX + 1
    val depth: Int get() = maxZ - minZ + 1
}

class Maze3dVoxelRenderer(
    private val maxChamberSize: Int = DEFAULT_MAX_CHAMBER_SIZE,
    private val corridorHeight: Int = DEFAULT_CORRIDOR_HEIGHT
) {
    private val horizontalStride: Int
    private val maxChamberHeight: Int
    private val levelStride: Int

    init {
        require(maxChamberSize >= 1) { "maxChamberSize must be at least 1" }
        require(maxChamberSize < Int.MAX_VALUE) { "maxChamberSize is too large" }
        require(corridorHeight >= 2) { "corridorHeight must leave standing room for players" }

        horizontalStride = maxChamberSize + 1
        maxChamberHeight = maxOf(corridorHeight, maxChamberSize)
        levelStride = maxChamberHeight + 1
    }

    fun measure(dimensions: Maze3dDimensions): Maze3dBlockDimensions {
        val blockWidthLong = dimensions.width.toLong() * horizontalStride.toLong() + 1L
        val blockDepthLong = dimensions.depth.toLong() * horizontalStride.toLong() + 1L
        val maxRelativeY = maxChamberHeight
        val minRelativeYLong = -((dimensions.height.toLong() - 1L) * levelStride.toLong() + 1L)
        val blockHeightLong = maxRelativeY.toLong() - minRelativeYLong + 1L

        require(
            blockWidthLong <= Int.MAX_VALUE &&
                blockHeightLong <= Int.MAX_VALUE &&
                blockDepthLong <= Int.MAX_VALUE &&
                minRelativeYLong >= Int.MIN_VALUE
        ) {
            "rendered maze dimensions exceed integer coordinate limits"
        }

        val blockWidth = blockWidthLong.toInt()
        val blockDepth = blockDepthLong.toInt()
        val blockHeight = blockHeightLong.toInt()
        val minRelativeY = minRelativeYLong.toInt()
        val volumeLong = try {
            Math.multiplyExact(Math.multiplyExact(blockWidthLong, blockHeightLong), blockDepthLong)
        } catch (_: ArithmeticException) {
            null
        }
        require(volumeLong != null && volumeLong <= MAX_RENDER_VOXELS) {
            val volumeDescription = volumeLong?.toString() ?: "more than ${Long.MAX_VALUE}"
            "${dimensions.width}x${dimensions.height}x${dimensions.depth} logical cells with max chamber " +
                "$maxChamberSize render as ${blockWidth}x${blockHeight}x${blockDepth} blocks " +
                "($volumeDescription positions), exceeding the $MAX_RENDER_VOXELS-position in-memory render limit"
        }

        return Maze3dBlockDimensions(
            width = blockWidth,
            height = blockHeight,
            depth = blockDepth,
            minRelativeY = minRelativeY,
            maxRelativeY = maxRelativeY,
            volume = volumeLong.toInt()
        )
    }

    fun render(maze: Maze3d): Maze3dRenderPlan {
        val dimensions = maze.dimensions
        val rendered = measure(dimensions)
        val voxels = ByteArray(rendered.volume) { Maze3dVoxel.WALL.ordinal.toByte() }
        val writer = VoxelWriter(rendered.width, rendered.depth, rendered.minRelativeY, voxels)
        val chamberRandom = Random(maze.seed xor CHAMBER_SEED_SALT)
        val chambers = Array(dimensions.cellCount) { index ->
            createChamber(dimensions.cellAt(index), chamberRandom)
        }

        chambers.forEach { carveChamber(writer, it) }

        for (index in chambers.indices) {
            val chamber = chambers[index]
            if (maze.isOpen(index, Maze3dDirection.EAST)) {
                val neighbor = dimensions.neighborIndex(index, Maze3dDirection.EAST)
                    ?: error("east passage had no neighbor")
                carveHorizontalConnector(writer, chamber, chambers[neighbor])
            }
            if (maze.isOpen(index, Maze3dDirection.SOUTH)) {
                val neighbor = dimensions.neighborIndex(index, Maze3dDirection.SOUTH)
                    ?: error("south passage had no neighbor")
                carveHorizontalConnector(writer, chamber, chambers[neighbor])
            }
            if (maze.isOpen(index, Maze3dDirection.DOWN)) {
                val neighbor = dimensions.neighborIndex(index, Maze3dDirection.DOWN)
                    ?: error("down passage had no neighbor")
                val shaftVoxel = if (maze.isClimbableConnection(index, Maze3dDirection.DOWN)) {
                    Maze3dVoxel.CLIMBABLE
                } else {
                    Maze3dVoxel.AIR
                }
                carveVerticalConnector(writer, chamber, chambers[neighbor], shaftVoxel)
            }
        }

        carveOpening(writer, chambers[dimensions.indexOf(maze.entrance.cell)], maze.entrance.direction)
        carveOpening(writer, chambers[dimensions.indexOf(maze.exit.cell)], maze.exit.direction)
        placeTreasureChests(writer, maze, chambers, chamberRandom)

        return Maze3dRenderPlan(
            blockWidth = rendered.width,
            blockHeight = rendered.height,
            blockDepth = rendered.depth,
            minRelativeY = rendered.minRelativeY,
            maxRelativeY = rendered.maxRelativeY,
            lootSeed = maze.seed xor LOOT_SEED_SALT,
            voxels = voxels
        )
    }

    private fun createChamber(cell: Maze3dCell, random: Random): Maze3dChamber {
        val width = randomSize(random, 1, maxChamberSize)
        val depth = randomSize(random, 1, maxChamberSize)
        val height = randomSize(random, corridorHeight, maxChamberHeight)
        val slotMinX = cell.x * horizontalStride + 1
        val slotMinZ = cell.z * horizontalStride + 1
        val minX = slotMinX + (maxChamberSize - width) / 2
        val minZ = slotMinZ + (maxChamberSize - depth) / 2

        return Maze3dChamber(
            cell = cell,
            minX = minX,
            maxX = minX + width - 1,
            footY = cellFootRelativeY(cell),
            height = height,
            minZ = minZ,
            maxZ = minZ + depth - 1,
            anchorX = slotMinX + (maxChamberSize - 1) / 2,
            anchorZ = slotMinZ + (maxChamberSize - 1) / 2
        )
    }

    private fun randomSize(random: Random, minimum: Int, maximum: Int): Int =
        if (minimum == maximum) minimum else minimum + random.nextInt(maximum - minimum + 1)

    private fun carveChamber(writer: VoxelWriter, chamber: Maze3dChamber) {
        for (relativeY in chamber.footY until chamber.footY + chamber.height) {
            for (localZ in chamber.minZ..chamber.maxZ) {
                for (localX in chamber.minX..chamber.maxX) {
                    writer.set(localX, relativeY, localZ, Maze3dVoxel.AIR)
                }
            }
        }
    }

    private fun carveHorizontalConnector(
        writer: VoxelWriter,
        first: Maze3dChamber,
        second: Maze3dChamber
    ) {
        require(first.footY == second.footY) { "horizontal chambers must share a floor" }

        if (first.anchorX != second.anchorX) {
            for (localX in minOf(first.anchorX, second.anchorX)..maxOf(first.anchorX, second.anchorX)) {
                for (dy in 0 until corridorHeight) {
                    writer.set(localX, first.footY + dy, first.anchorZ, Maze3dVoxel.AIR)
                }
            }
        } else {
            for (localZ in minOf(first.anchorZ, second.anchorZ)..maxOf(first.anchorZ, second.anchorZ)) {
                for (dy in 0 until corridorHeight) {
                    writer.set(first.anchorX, first.footY + dy, localZ, Maze3dVoxel.AIR)
                }
            }
        }
    }

    private fun carveVerticalConnector(
        writer: VoxelWriter,
        upper: Maze3dChamber,
        lower: Maze3dChamber,
        shaftVoxel: Maze3dVoxel
    ) {
        require(upper.anchorX == lower.anchorX && upper.anchorZ == lower.anchorZ) {
            "vertical chambers must share a shaft anchor"
        }
        require(shaftVoxel == Maze3dVoxel.CLIMBABLE || shaftVoxel == Maze3dVoxel.AIR) {
            "vertical shaft must be climbable or open air"
        }

        val upperHeadY = upper.footY + corridorHeight - 1
        for (relativeY in lower.footY..upperHeadY) {
            writer.set(upper.anchorX, relativeY, upper.anchorZ, shaftVoxel)
        }
    }

    private fun carveOpening(
        writer: VoxelWriter,
        chamber: Maze3dChamber,
        direction: Maze3dDirection
    ) {
        for (dy in 0 until corridorHeight) {
            when (direction) {
                Maze3dDirection.WEST ->
                    for (localX in chamber.cell.x * horizontalStride until chamber.minX) {
                        writer.set(localX, chamber.footY + dy, chamber.anchorZ, Maze3dVoxel.AIR)
                    }
                Maze3dDirection.EAST ->
                    for (localX in chamber.maxX + 1..(chamber.cell.x + 1) * horizontalStride) {
                        writer.set(localX, chamber.footY + dy, chamber.anchorZ, Maze3dVoxel.AIR)
                    }
                Maze3dDirection.NORTH ->
                    for (localZ in chamber.cell.z * horizontalStride until chamber.minZ) {
                        writer.set(chamber.anchorX, chamber.footY + dy, localZ, Maze3dVoxel.AIR)
                    }
                Maze3dDirection.SOUTH ->
                    for (localZ in chamber.maxZ + 1..(chamber.cell.z + 1) * horizontalStride) {
                        writer.set(chamber.anchorX, chamber.footY + dy, localZ, Maze3dVoxel.AIR)
                    }
                Maze3dDirection.UP,
                Maze3dDirection.DOWN -> error("external openings must be horizontal")
            }
        }
    }

    private fun placeTreasureChests(
        writer: VoxelWriter,
        maze: Maze3d,
        chambers: Array<Maze3dChamber>,
        random: Random
    ) {
        val eligible = chambers.filter {
            it.width >= MIN_TREASURE_CHAMBER_SIDE &&
                it.depth >= MIN_TREASURE_CHAMBER_SIDE &&
                it.cell != maze.entrance.cell &&
                it.cell != maze.exit.cell
        }
        if (eligible.isEmpty()) return

        var placed = 0
        for (chamber in eligible) {
            if (random.nextDouble() < TREASURE_CHANCE && placeTreasureChest(writer, chamber, random)) {
                placed++
            }
        }

        if (placed == 0) {
            val start = random.nextInt(eligible.size)
            for (offset in eligible.indices) {
                if (placeTreasureChest(writer, eligible[(start + offset) % eligible.size], random)) {
                    break
                }
            }
        }
    }

    private fun placeTreasureChest(
        writer: VoxelWriter,
        chamber: Maze3dChamber,
        random: Random
    ): Boolean {
        val candidates = mutableListOf(
            chamber.minX to chamber.minZ,
            chamber.minX to chamber.maxZ,
            chamber.maxX to chamber.minZ,
            chamber.maxX to chamber.maxZ
        ).distinct().filter { (x, z) ->
            (x != chamber.anchorX || z != chamber.anchorZ) &&
                writer.get(x, chamber.footY, z) == Maze3dVoxel.AIR &&
                writer.get(x, chamber.footY + 1, z) == Maze3dVoxel.AIR
        }.toMutableList()

        while (candidates.isNotEmpty()) {
            val candidate = candidates.removeAt(random.nextInt(candidates.size))
            writer.set(candidate.first, chamber.footY, candidate.second, Maze3dVoxel.TREASURE_CHEST)
            return true
        }
        return false
    }

    private fun cellFootRelativeY(cell: Maze3dCell): Int = -(cell.y * levelStride)

    private class VoxelWriter(
        private val blockWidth: Int,
        private val blockDepth: Int,
        private val minRelativeY: Int,
        private val voxels: ByteArray
    ) {
        fun get(localX: Int, relativeY: Int, localZ: Int): Maze3dVoxel =
            Maze3dVoxel.entries[voxels[indexOf(localX, relativeY, localZ)].toInt()]

        fun set(localX: Int, relativeY: Int, localZ: Int, voxel: Maze3dVoxel) {
            voxels[indexOf(localX, relativeY, localZ)] = voxel.ordinal.toByte()
        }

        private fun indexOf(localX: Int, relativeY: Int, localZ: Int): Int {
            val localY = relativeY - minRelativeY
            return (localY * blockDepth + localZ) * blockWidth + localX
        }
    }

    companion object {
        const val DEFAULT_MAX_CHAMBER_SIZE = 1
        const val DEFAULT_LEVEL_STRIDE = 3
        const val DEFAULT_CORRIDOR_HEIGHT = 2
        const val MAX_RENDER_VOXELS = 100_000_000L
        private const val MIN_TREASURE_CHAMBER_SIDE = 3
        private const val TREASURE_CHANCE = 0.08
        private const val CHAMBER_SEED_SALT = 0x4348414D42455253L
        private const val LOOT_SEED_SALT = 0x5452454153555245L
    }
}
