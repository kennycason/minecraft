package com.kenny.supermetroid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Maze3dVoxelRendererTest {
    @Test
    fun `renderer creates expected block volume from logical dimensions`() {
        val maze = Maze3dGenerator(seed = 42L).generate(Maze3dDimensions(width = 3, height = 2, depth = 4))
        val plan = Maze3dVoxelRenderer().render(maze)

        assertEquals(7, plan.blockWidth)
        assertEquals(6, plan.blockHeight)
        assertEquals(9, plan.blockDepth)
        assertEquals(-4, plan.minRelativeY)
        assertEquals(1, plan.maxRelativeY)
        assertEquals(7 * 6 * 9, plan.volume)
    }

    @Test
    fun `logical dimensions expand to two horizontal blocks and three vertical blocks per cell`() {
        val rendered = Maze3dVoxelRenderer().measure(Maze3dDimensions(width = 100, height = 125, depth = 125))

        assertEquals(201, rendered.width)
        assertEquals(375, rendered.height)
        assertEquals(251, rendered.depth)
        assertEquals(-373, rendered.minRelativeY)
        assertEquals(1, rendered.maxRelativeY)
        assertEquals(18_919_125, rendered.volume)
    }

    @Test
    fun `renderer accepts more than two million logical cells when rendered volume is safe`() {
        val dimensions = Maze3dDimensions(width = 129, height = 125, depth = 125)
        val rendered = Maze3dVoxelRenderer().measure(dimensions)

        assertEquals(2_015_625, dimensions.cellCount)
        assertEquals(24_378_375, rendered.volume)
    }

    @Test
    fun `renderer carves every cell with two blocks of standing room`() {
        val dimensions = Maze3dDimensions(width = 4, height = 3, depth = 4)
        val maze = Maze3dGenerator(seed = 56L).generate(dimensions)
        val plan = Maze3dVoxelRenderer().render(maze)

        for (y in 0 until dimensions.height) {
            val footY = -(y * Maze3dVoxelRenderer.DEFAULT_LEVEL_STRIDE)
            for (z in 0 until dimensions.depth) {
                for (x in 0 until dimensions.width) {
                    val localX = x * 2 + 1
                    val localZ = z * 2 + 1
                    assertTrue(plan.voxelAt(localX, footY, localZ) != Maze3dVoxel.WALL)
                    assertTrue(plan.voxelAt(localX, footY + 1, localZ) != Maze3dVoxel.WALL)
                }
            }
        }
    }

    @Test
    fun `renderer carves entrance and exit through outside wall only`() {
        val maze = Maze3dGenerator(seed = 99L).generate(Maze3dDimensions(width = 4, height = 2, depth = 4))
        val plan = Maze3dVoxelRenderer().render(maze)

        assertOpening(plan, maze.entrance)
        assertOpening(plan, maze.exit)
    }

    @Test
    fun `renderer turns vertical maze passages into climbable shafts`() {
        val maze = Maze3dGenerator(seed = 1L).generate(Maze3dDimensions(width = 1, height = 2, depth = 1))
        val plan = Maze3dVoxelRenderer().render(maze)

        for (relativeY in -3..1) {
            assertEquals(Maze3dVoxel.CLIMBABLE, plan.voxelAt(1, relativeY, 1))
        }
    }

    private fun assertOpening(plan: Maze3dRenderPlan, opening: Maze3dOpening) {
        val localX = opening.cell.x * 2 + 1 + opening.direction.dx
        val localZ = opening.cell.z * 2 + 1 + opening.direction.dz
        val footY = -(opening.cell.y * Maze3dVoxelRenderer.DEFAULT_LEVEL_STRIDE)

        assertEquals(Maze3dVoxel.AIR, plan.voxelAt(localX, footY, localZ))
        assertEquals(Maze3dVoxel.AIR, plan.voxelAt(localX, footY + 1, localZ))
    }
}
