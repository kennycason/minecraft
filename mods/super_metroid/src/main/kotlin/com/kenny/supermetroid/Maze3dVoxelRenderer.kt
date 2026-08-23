package com.kenny.supermetroid

enum class Maze3dVoxel {
    WALL,
    AIR,
    CLIMBABLE
}

data class Maze3dRenderPlan(
    val blockWidth: Int,
    val blockHeight: Int,
    val blockDepth: Int,
    val minRelativeY: Int,
    val maxRelativeY: Int,
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

class Maze3dVoxelRenderer(
    private val levelStride: Int = DEFAULT_LEVEL_STRIDE,
    private val corridorHeight: Int = DEFAULT_CORRIDOR_HEIGHT
) {
    init {
        require(levelStride >= corridorHeight + 1) {
            "levelStride must leave at least one block of material between maze levels"
        }
        require(corridorHeight >= 2) {
            "corridorHeight must leave standing room for players"
        }
    }

    fun measure(dimensions: Maze3dDimensions): Maze3dBlockDimensions {
        val blockWidthLong = dimensions.width.toLong() * 2L + 1L
        val blockDepthLong = dimensions.depth.toLong() * 2L + 1L
        val maxRelativeY = corridorHeight - 1
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
            "${dimensions.width}x${dimensions.height}x${dimensions.depth} logical cells render as " +
                "${blockWidth}x${blockHeight}x${blockDepth} blocks ($volumeDescription positions), exceeding " +
                "the $MAX_RENDER_VOXELS-position in-memory render limit"
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
        val blockWidth = rendered.width
        val blockDepth = rendered.depth
        val minRelativeY = rendered.minRelativeY
        val writer = VoxelWriter(blockWidth, blockDepth, minRelativeY, voxels)

        for (index in 0 until dimensions.cellCount) {
            val cell = dimensions.cellAt(index)
            carveCell(writer, cell)

            for (direction in maze.openDirections(index)) {
                when (direction) {
                    Maze3dDirection.EAST,
                    Maze3dDirection.WEST,
                    Maze3dDirection.NORTH,
                    Maze3dDirection.SOUTH -> carveHorizontalConnector(writer, cell, direction)
                    Maze3dDirection.DOWN,
                    Maze3dDirection.UP -> Unit
                }
            }
        }

        for (index in 0 until dimensions.cellCount) {
            val cell = dimensions.cellAt(index)
            if (maze.isOpen(index, Maze3dDirection.DOWN)) {
                carveVerticalConnector(writer, cell)
            }
        }

        carveOpening(writer, maze.entrance)
        carveOpening(writer, maze.exit)

        return Maze3dRenderPlan(
            blockWidth = blockWidth,
            blockHeight = rendered.height,
            blockDepth = blockDepth,
            minRelativeY = minRelativeY,
            maxRelativeY = rendered.maxRelativeY,
            voxels = voxels
        )
    }

    private fun carveCell(writer: VoxelWriter, cell: Maze3dCell) {
        val x = cellLocalX(cell)
        val z = cellLocalZ(cell)
        val footY = cellFootRelativeY(cell)
        for (dy in 0 until corridorHeight) {
            writer.set(x, footY + dy, z, Maze3dVoxel.AIR)
        }
    }

    private fun carveHorizontalConnector(
        writer: VoxelWriter,
        cell: Maze3dCell,
        direction: Maze3dDirection
    ) {
        val x = cellLocalX(cell) + direction.dx
        val z = cellLocalZ(cell) + direction.dz
        val footY = cellFootRelativeY(cell)
        for (dy in 0 until corridorHeight) {
            writer.set(x, footY + dy, z, Maze3dVoxel.AIR)
        }
    }

    private fun carveVerticalConnector(writer: VoxelWriter, cell: Maze3dCell) {
        val x = cellLocalX(cell)
        val z = cellLocalZ(cell)
        val upperHeadY = cellFootRelativeY(cell) + corridorHeight - 1
        val lowerFootY = cellFootRelativeY(Maze3dCell(cell.x, cell.y + 1, cell.z))
        for (relativeY in lowerFootY..upperHeadY) {
            writer.set(x, relativeY, z, Maze3dVoxel.CLIMBABLE)
        }
    }

    private fun carveOpening(writer: VoxelWriter, opening: Maze3dOpening) {
        val x = cellLocalX(opening.cell) + opening.direction.dx
        val z = cellLocalZ(opening.cell) + opening.direction.dz
        val footY = cellFootRelativeY(opening.cell)
        for (dy in 0 until corridorHeight) {
            writer.set(x, footY + dy, z, Maze3dVoxel.AIR)
        }
    }

    private fun cellLocalX(cell: Maze3dCell): Int = cell.x * 2 + 1

    private fun cellLocalZ(cell: Maze3dCell): Int = cell.z * 2 + 1

    private fun cellFootRelativeY(cell: Maze3dCell): Int = -(cell.y * levelStride)

    private class VoxelWriter(
        private val blockWidth: Int,
        private val blockDepth: Int,
        private val minRelativeY: Int,
        private val voxels: ByteArray
    ) {
        fun set(localX: Int, relativeY: Int, localZ: Int, voxel: Maze3dVoxel) {
            val localY = relativeY - minRelativeY
            val index = (localY * blockDepth + localZ) * blockWidth + localX
            voxels[index] = voxel.ordinal.toByte()
        }
    }

    companion object {
        const val DEFAULT_LEVEL_STRIDE = 3
        const val DEFAULT_CORRIDOR_HEIGHT = 2
        const val MAX_RENDER_VOXELS = 100_000_000L
    }
}
