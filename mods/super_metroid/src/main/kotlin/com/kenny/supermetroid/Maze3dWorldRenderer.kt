package com.kenny.supermetroid

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory
import java.util.Random

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
    val climbableCount: Int,
    val treasureChestCount: Int
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
    private val lootRandom = Random(plan.lootSeed)

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

            val voxel = plan.voxelAtIndex(cursor)
            val state = when (voxel) {
                Maze3dVoxel.WALL -> wallState
                Maze3dVoxel.AIR -> Blocks.AIR.defaultBlockState()
                Maze3dVoxel.CLIMBABLE -> climbableState
                Maze3dVoxel.TREASURE_CHEST -> Blocks.CHEST.defaultBlockState()
            }

            mutablePos.set(origin.x + localX, origin.y + relativeY, origin.z + localZ)
            world.setBlock(mutablePos, state, BLOCK_UPDATE_FLAGS)
            if (voxel == Maze3dVoxel.TREASURE_CHEST) {
                populateTreasureChest(mutablePos)
            }
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
            climbableCount = plan.climbableCount,
            treasureChestCount = plan.treasureChestCount
        )

    private fun populateTreasureChest(pos: BlockPos) {
        val chest = world.getBlockEntity(pos) as? ChestBlockEntity ?: return
        val supplies = mutableListOf(
            ItemStack(Items.TORCH, 8 + lootRandom.nextInt(17)),
            ItemStack(Items.LADDER, 4 + lootRandom.nextInt(13)),
            ItemStack(Items.SCAFFOLDING, 4 + lootRandom.nextInt(13)),
            ItemStack(Items.LEAD, 1 + lootRandom.nextInt(3)),
            ItemStack(Items.BREAD, 2 + lootRandom.nextInt(5))
        )
        if (lootRandom.nextDouble() < 0.45) {
            supplies.add(ItemStack(Items.COMPASS))
        }
        if (lootRandom.nextDouble() < 0.25) {
            supplies.add(ItemStack(Items.ENDER_PEARL, 1 + lootRandom.nextInt(3)))
        }
        if (lootRandom.nextDouble() < 0.10) {
            supplies.add(ItemStack(Items.GOLDEN_APPLE))
        }

        val slots = (0 until chest.containerSize).toMutableList()
        shuffle(slots, lootRandom)
        chest.clearContent()
        supplies.forEachIndexed { index, stack -> chest.setItem(slots[index], stack) }
        chest.setChanged()
    }

    private fun <T> shuffle(values: MutableList<T>, random: Random) {
        for (index in values.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[other]
            values[other] = value
        }
    }

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
                        "${summary.bounds.width}x${summary.bounds.height}x${summary.bounds.depth} volume, " +
                        "${summary.treasureChestCount} treasure chests"
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
