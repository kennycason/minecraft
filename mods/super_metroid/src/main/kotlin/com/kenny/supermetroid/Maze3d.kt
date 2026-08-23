package com.kenny.supermetroid

import java.util.ArrayDeque
import java.util.Random

data class Maze3dDimensions(
    val width: Int,
    val height: Int,
    val depth: Int
) {
    val cellCountLong: Long = width.toLong() * height.toLong() * depth.toLong()
    val cellCount: Int = cellCountLong.toInt()

    init {
        require(width > 0) { "width must be greater than zero" }
        require(height > 0) { "height must be greater than zero" }
        require(depth > 0) { "depth must be greater than zero" }
        require(cellCountLong <= Int.MAX_VALUE) { "maze has too many cells" }
    }

    fun contains(cell: Maze3dCell): Boolean =
        cell.x in 0 until width &&
            cell.y in 0 until height &&
            cell.z in 0 until depth

    fun indexOf(cell: Maze3dCell): Int {
        require(contains(cell)) { "cell is outside maze dimensions: $cell" }
        return indexOf(cell.x, cell.y, cell.z)
    }

    fun indexOf(x: Int, y: Int, z: Int): Int {
        require(x in 0 until width) { "x is outside maze dimensions: $x" }
        require(y in 0 until height) { "y is outside maze dimensions: $y" }
        require(z in 0 until depth) { "z is outside maze dimensions: $z" }
        return (y * depth + z) * width + x
    }

    fun cellAt(index: Int): Maze3dCell {
        require(index in 0 until cellCount) { "cell index is outside maze dimensions: $index" }
        val x = index % width
        val yz = index / width
        val z = yz % depth
        val y = yz / depth
        return Maze3dCell(x, y, z)
    }

    fun neighborIndex(index: Int, direction: Maze3dDirection): Int? {
        val cell = cellAt(index)
        val nx = cell.x + direction.dx
        val ny = cell.y + direction.dy
        val nz = cell.z + direction.dz
        if (nx !in 0 until width || ny !in 0 until height || nz !in 0 until depth) {
            return null
        }
        return indexOf(nx, ny, nz)
    }
}

data class Maze3dCell(
    val x: Int,
    val y: Int,
    val z: Int
)

enum class Maze3dDirection(
    val dx: Int,
    val dy: Int,
    val dz: Int
) {
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    UP(0, -1, 0),
    DOWN(0, 1, 0);

    val bit: Int get() = 1 shl ordinal

    val opposite: Maze3dDirection
        get() = when (this) {
            WEST -> EAST
            EAST -> WEST
            NORTH -> SOUTH
            SOUTH -> NORTH
            UP -> DOWN
            DOWN -> UP
        }
}

data class Maze3dOpening(
    val cell: Maze3dCell,
    val direction: Maze3dDirection
) {
    init {
        require(direction != Maze3dDirection.UP && direction != Maze3dDirection.DOWN) {
            "external openings must be horizontal"
        }
    }
}

class Maze3d internal constructor(
    val dimensions: Maze3dDimensions,
    private val connectionMasks: IntArray,
    val entrance: Maze3dOpening,
    val exit: Maze3dOpening,
    val seed: Long
) {
    init {
        require(connectionMasks.size == dimensions.cellCount) {
            "connection mask count does not match maze dimensions"
        }
        require(dimensions.contains(entrance.cell)) { "entrance cell is outside maze dimensions" }
        require(dimensions.contains(exit.cell)) { "exit cell is outside maze dimensions" }
    }

    fun isOpen(cell: Maze3dCell, direction: Maze3dDirection): Boolean =
        isOpen(dimensions.indexOf(cell), direction)

    fun isOpen(index: Int, direction: Maze3dDirection): Boolean {
        require(index in connectionMasks.indices) { "cell index is outside maze dimensions: $index" }
        return connectionMasks[index] and direction.bit != 0
    }

    fun openDirections(cell: Maze3dCell): List<Maze3dDirection> =
        openDirections(dimensions.indexOf(cell))

    fun openDirections(index: Int): List<Maze3dDirection> {
        require(index in connectionMasks.indices) { "cell index is outside maze dimensions: $index" }
        val mask = connectionMasks[index]
        return Maze3dDirection.entries.filter { mask and it.bit != 0 }
    }

    fun connectionCount(): Int {
        var directedCount = 0
        for (mask in connectionMasks) {
            directedCount += Integer.bitCount(mask)
        }
        return directedCount / 2
    }

    fun reachableCellCountFromStart(): Int {
        val visited = BooleanArray(dimensions.cellCount)
        val queue = ArrayDeque<Int>()
        val start = dimensions.indexOf(entrance.cell)
        visited[start] = true
        queue.add(start)

        var count = 0
        while (!queue.isEmpty()) {
            val current = queue.removeFirst()
            count++
            for (direction in openDirections(current)) {
                val neighbor = dimensions.neighborIndex(current, direction) ?: continue
                if (!visited[neighbor]) {
                    visited[neighbor] = true
                    queue.add(neighbor)
                }
            }
        }

        return count
    }

    internal fun connectionMask(index: Int): Int = connectionMasks[index]
}

class Maze3dGenerator(
    private val seed: Long = Random().nextLong()
) {
    private val random = Random(seed)

    fun generate(dimensions: Maze3dDimensions): Maze3d {
        val connections = IntArray(dimensions.cellCount)
        val visited = BooleanArray(dimensions.cellCount)
        val stack = IntArray(dimensions.cellCount)

        val start = dimensions.indexOf(0, 0, 0)
        var stackSize = 1
        stack[0] = start
        visited[start] = true

        val candidates = IntArray(Maze3dDirection.entries.size)
        while (stackSize > 0) {
            val current = stack[stackSize - 1]
            var candidateCount = 0

            for (direction in Maze3dDirection.entries) {
                val neighbor = dimensions.neighborIndex(current, direction) ?: continue
                if (!visited[neighbor]) {
                    candidates[candidateCount++] = direction.ordinal
                }
            }

            if (candidateCount == 0) {
                stackSize--
                continue
            }

            val direction = Maze3dDirection.entries[candidates[random.nextInt(candidateCount)]]
            val neighbor = dimensions.neighborIndex(current, direction)
                ?: error("candidate direction had no neighbor")

            connections[current] = connections[current] or direction.bit
            connections[neighbor] = connections[neighbor] or direction.opposite.bit
            visited[neighbor] = true
            stack[stackSize++] = neighbor
        }

        val entrance = Maze3dOpening(Maze3dCell(0, 0, 0), Maze3dDirection.WEST)
        val exitCell = farthestHorizontalBoundaryCell(dimensions, connections, start)
        val exit = Maze3dOpening(exitCell, exitDirectionFor(dimensions, exitCell))

        return Maze3d(
            dimensions = dimensions,
            connectionMasks = connections,
            entrance = entrance,
            exit = exit,
            seed = seed
        )
    }

    private fun farthestHorizontalBoundaryCell(
        dimensions: Maze3dDimensions,
        connections: IntArray,
        start: Int
    ): Maze3dCell {
        val distances = IntArray(dimensions.cellCount) { -1 }
        val queue = ArrayDeque<Int>()
        distances[start] = 0
        queue.add(start)

        var farthestIndex = start
        var farthestDistance = -1

        while (!queue.isEmpty()) {
            val current = queue.removeFirst()
            val currentCell = dimensions.cellAt(current)
            val currentDistance = distances[current]

            if (currentCell.isHorizontalBoundary(dimensions) && currentDistance > farthestDistance) {
                farthestIndex = current
                farthestDistance = currentDistance
            }

            val mask = connections[current]
            for (direction in Maze3dDirection.entries) {
                if (mask and direction.bit == 0) continue
                val neighbor = dimensions.neighborIndex(current, direction) ?: continue
                if (distances[neighbor] == -1) {
                    distances[neighbor] = currentDistance + 1
                    queue.add(neighbor)
                }
            }
        }

        return dimensions.cellAt(farthestIndex)
    }

    private fun Maze3dCell.isHorizontalBoundary(dimensions: Maze3dDimensions): Boolean =
        x == 0 || x == dimensions.width - 1 || z == 0 || z == dimensions.depth - 1

    private fun exitDirectionFor(dimensions: Maze3dDimensions, cell: Maze3dCell): Maze3dDirection =
        when {
            cell.x == dimensions.width - 1 -> Maze3dDirection.EAST
            cell.z == dimensions.depth - 1 -> Maze3dDirection.SOUTH
            cell.z == 0 -> Maze3dDirection.NORTH
            cell.x == 0 -> Maze3dDirection.WEST
            else -> Maze3dDirection.EAST
        }
}
