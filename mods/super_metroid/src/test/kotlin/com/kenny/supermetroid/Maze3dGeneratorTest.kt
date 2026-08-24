package com.kenny.supermetroid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Maze3dGeneratorTest {
    @Test
    fun `generator creates a connected perfect maze`() {
        val dimensions = Maze3dDimensions(width = 4, height = 3, depth = 5)
        val maze = Maze3dGenerator(seed = 1234L).generate(dimensions)

        assertEquals(dimensions.cellCount, maze.reachableCellCountFromStart())
        assertEquals(dimensions.cellCount - 1, maze.connectionCount())
        assertEquals(0, maze.cycleCount())
    }

    @Test
    fun `all generated passages are bidirectional and in bounds`() {
        val dimensions = Maze3dDimensions(width = 5, height = 4, depth = 3)
        val maze = Maze3dGenerator(seed = 9876L).generate(dimensions)

        for (index in 0 until dimensions.cellCount) {
            for (direction in maze.openDirections(index)) {
                val neighbor = dimensions.neighborIndex(index, direction)
                assertNotNull(neighbor, "open passage $direction from ${dimensions.cellAt(index)} left the maze")
                assertTrue(
                    maze.isOpen(neighbor, direction.opposite),
                    "passage $direction from ${dimensions.cellAt(index)} was not mirrored by ${direction.opposite}"
                )
            }
        }
    }

    @Test
    fun `maze has one entrance and one horizontal boundary exit`() {
        val dimensions = Maze3dDimensions(width = 6, height = 2, depth = 6)
        val maze = Maze3dGenerator(seed = 111L).generate(dimensions)

        assertEquals(Maze3dCell(0, 0, 0), maze.entrance.cell)
        assertEquals(Maze3dDirection.WEST, maze.entrance.direction)

        assertTrue(maze.exit.direction != Maze3dDirection.UP && maze.exit.direction != Maze3dDirection.DOWN)
        assertTrue(
            maze.exit.cell.x == 0 ||
                maze.exit.cell.x == dimensions.width - 1 ||
                maze.exit.cell.z == 0 ||
                maze.exit.cell.z == dimensions.depth - 1
        )
        assertNotEquals(maze.entrance, maze.exit)
    }

    @Test
    fun `cycle zero still branches immediately and throughout the maze`() {
        val dimensions = Maze3dDimensions(width = 12, height = 4, depth = 12)
        val maze = Maze3dGenerator(seed = 4321L, cycleIntensity = 0).generate(dimensions)

        assertEquals(3, maze.openDirections(maze.entrance.cell).size)
        assertTrue(maze.branchingCellCount() >= dimensions.cellCount / 10)
        assertEquals(0, maze.cycleCount())
    }

    @Test
    fun `cycle ten opens every adjacent cell connection`() {
        val dimensions = Maze3dDimensions(width = 4, height = 3, depth = 5)
        val maze = Maze3dGenerator(seed = 2468L, cycleIntensity = 10).generate(dimensions)
        val maximumConnections =
            (dimensions.width - 1) * dimensions.height * dimensions.depth +
                (dimensions.height - 1) * dimensions.width * dimensions.depth +
                (dimensions.depth - 1) * dimensions.width * dimensions.height

        assertEquals(maximumConnections, maze.connectionCount())
        assertEquals(maximumConnections - dimensions.cellCount + 1, maze.cycleCount())
        assertEquals(dimensions.cellCount, maze.reachableCellCountFromStart())
    }

    @Test
    fun `extra vertical cycle connections mix climbable and open shafts`() {
        val dimensions = Maze3dDimensions(width = 8, height = 4, depth = 8)
        val maze = Maze3dGenerator(seed = 97531L, cycleIntensity = 10).generate(dimensions)
        var climbableShafts = 0

        for (index in 0 until dimensions.cellCount) {
            if (
                maze.isOpen(index, Maze3dDirection.DOWN) &&
                maze.isClimbableConnection(index, Maze3dDirection.DOWN)
            ) {
                climbableShafts++
            }
        }

        assertTrue(climbableShafts > 0)
        assertTrue(maze.unassistedVerticalConnectionCount() > 0)
    }

    @Test
    fun `cycle intensity is constrained to zero through ten`() {
        assertFailsWith<IllegalArgumentException> { Maze3dGenerator(seed = 1L, cycleIntensity = -1) }
        assertFailsWith<IllegalArgumentException> { Maze3dGenerator(seed = 1L, cycleIntensity = 11) }
    }
}
