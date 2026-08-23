package com.kenny.supermetroid

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory

data class Maze3dWorldBounds(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
    val depth: Int get() = maxZ - minZ + 1
}

data class Maze3dBuildSummary(
    val bounds: Maze3dWorldBounds,
    val totalBlocks: Int,
    val wallCount: Int,
    val airCount: Int,
    val climbableCount: Int
)

class Maze3dWorldRenderer(
    private val world: ServerLevel,
    private val origin: BlockPos,
    private val wallBlock: Block = Blocks.REINFORCED_DEEPSLATE,
    private val climbableBlock: Block = Blocks.SCAFFOLDING
) {
    fun validate(plan: Maze3dRenderPlan): Maze3dWorldBounds {
        val bounds = boundsFor(plan)
        require(bounds.minY >= world.minY) {
            "maze is ${plan.blockHeight} blocks tall and builds downward from entrance Y ${origin.y}; " +
                "bottom would be Y ${bounds.minY}, below world minimum Y ${world.minY}. " +
                "Reduce the second /maze3d argument (height) or start higher."
        }
        require(bounds.maxY <= world.maxY) {
            "maze is ${plan.blockHeight} blocks tall; top would be Y ${bounds.maxY}, " +
                "above world maximum Y ${world.maxY}. Start lower."
        }
        return bounds
    }

    fun createJob(
        plan: Maze3dRenderPlan,
        onComplete: ((Maze3dBuildSummary) -> Unit)? = null
    ): Maze3dBuildJob {
        val bounds = validate(plan)
        return Maze3dBuildJob(
            world = world,
            origin = origin,
            plan = plan,
            bounds = bounds,
            wallState = wallBlock.defaultBlockState(),
            climbableState = climbableBlock.defaultBlockState(),
            onComplete = onComplete
        )
    }

    private fun boundsFor(plan: Maze3dRenderPlan): Maze3dWorldBounds =
        Maze3dWorldBounds(
            minX = origin.x,
            minY = origin.y + plan.minRelativeY,
            minZ = origin.z,
            maxX = origin.x + plan.blockWidth - 1,
            maxY = origin.y + plan.maxRelativeY,
            maxZ = origin.z + plan.blockDepth - 1
        )
}

class Maze3dBuildJob internal constructor(
    private val world: ServerLevel,
    private val origin: BlockPos,
    private val plan: Maze3dRenderPlan,
    private val bounds: Maze3dWorldBounds,
    private val wallState: BlockState,
    private val climbableState: BlockState,
    private val onComplete: ((Maze3dBuildSummary) -> Unit)?
) {
    private var cursor = 0

    val totalBlocks: Int get() = plan.volume
    val placedBlocks: Int get() = cursor
    val remainingBlocks: Int get() = totalBlocks - cursor
    val isComplete: Boolean get() = cursor >= totalBlocks

    fun tick(maxBlocks: Int): Int {
        var processed = 0
        val mutablePos = BlockPos.MutableBlockPos()
        while (processed < maxBlocks && cursor < totalBlocks) {
            val localX = plan.localXAtIndex(cursor)
            val relativeY = plan.relativeYAtIndex(cursor)
            val localZ = plan.localZAtIndex(cursor)

            val state = when (plan.voxelAtIndex(cursor)) {
                Maze3dVoxel.WALL -> wallState
                Maze3dVoxel.AIR -> Blocks.AIR.defaultBlockState()
                Maze3dVoxel.CLIMBABLE -> climbableState
            }

            mutablePos.set(origin.x + localX, origin.y + relativeY, origin.z + localZ)
            world.setBlock(mutablePos, state, BLOCK_UPDATE_FLAGS)
            cursor++
            processed++
        }

        if (isComplete) {
            onComplete?.invoke(summary())
        }

        return processed
    }

    fun summary(): Maze3dBuildSummary =
        Maze3dBuildSummary(
            bounds = bounds,
            totalBlocks = totalBlocks,
            wallCount = plan.wallCount,
            airCount = plan.airCount,
            climbableCount = plan.climbableCount
        )

    companion object {
        private const val BLOCK_UPDATE_FLAGS = 2
    }
}

object Maze3dBuildQueue {
    private val logger = LoggerFactory.getLogger("super_metroid")
    private val jobs = ArrayDeque<Maze3dBuildJob>()
    const val BLOCKS_PER_TICK = 5_000

    fun enqueue(job: Maze3dBuildJob) {
        jobs.addLast(job)
    }

    fun tick(server: MinecraftServer) {
        var remainingBudget = BLOCKS_PER_TICK
        while (remainingBudget > 0 && jobs.isNotEmpty()) {
            val job = jobs.first()
            val processed = job.tick(remainingBudget)
            remainingBudget -= processed

            if (job.isComplete) {
                val summary = job.summary()
                logger.info(
                    "Finished 3D maze build: ${summary.totalBlocks} blocks, " +
                        "${summary.bounds.width}x${summary.bounds.height}x${summary.bounds.depth} volume"
                )
                jobs.removeFirst()
            } else {
                break
            }
        }
    }

    fun status(): String {
        if (jobs.isEmpty()) return "No active 3D maze builds."
        val first = jobs.first()
        return "3D maze builds queued: ${jobs.size}. Current: ${first.placedBlocks}/${first.totalBlocks} blocks placed."
    }
}
