package com.kenny.antfarm

import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.state.property.Properties
import kotlin.random.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Underground Ant Farm Generator - Digs realistic branching tunnels into terrain
 */
class UndergroundAntFarmGenerator(
    private val world: ServerWorld,
    private val origin: BlockPos,
    private val width: Int,
    private val depth: Int,
    private val maxDepth: Int
) {
    private val random = Random(System.currentTimeMillis())
    private val carvedPositions = mutableSetOf<BlockPos>()
    private val tunnelEndpoints = mutableListOf<BlockPos>()
    private val chamberPositions = mutableListOf<BlockPos>()
    
    companion object {
        val COMMON_LOOT = listOf(
            Items.TORCH to 25, Items.BREAD to 20, Items.COAL to 15,
            Items.STONE_PICKAXE to 10, Items.STONE_SWORD to 10,
            Items.LEATHER_BOOTS to 10, Items.APPLE to 10
        )
        val UNCOMMON_LOOT = listOf(
            Items.IRON_SWORD to 15, Items.IRON_PICKAXE to 15,
            Items.IRON_HELMET to 12, Items.IRON_BOOTS to 12,
            Items.SHIELD to 10, Items.BOW to 10, Items.ARROW to 8,
            Items.GOLDEN_APPLE to 5, Items.IRON_INGOT to 8
        )
        val RARE_LOOT = listOf(
            Items.DIAMOND_SWORD to 12, Items.DIAMOND_PICKAXE to 10,
            Items.DIAMOND_HELMET to 8, Items.DIAMOND_CHESTPLATE to 6,
            Items.DIAMOND to 15, Items.EMERALD to 12,
            Items.ENCHANTED_GOLDEN_APPLE to 3, Items.TOTEM_OF_UNDYING to 2,
            Items.TRIDENT to 4, Items.NETHERITE_SCRAP to 5
        )
    }
    
    fun generate(): Int {
        var tunnelCount = 0
        
        // 1. Create entrance
        createEntrance()
        
        // 2. Start recursive tree branching from entrance
        tunnelCount += digBranch(
            x = origin.x.toDouble(),
            y = (origin.y - 5).toDouble(),
            z = origin.z.toDouble(),
            dirX = 0.0,
            dirY = -0.8,  // Mostly down
            dirZ = 0.0,
            depth = 0,
            maxRecursion = 6
        )
        
        // 3. Create chambers at some endpoints
        createChambers()
        
        // 4. Add hazards
        addWaterPools()
        addLavaPits()
        addSpikeTraps()
        
        // 5. Add lighting
        addLighting()
        
        // 6. Add chests (on solid ground only!)
        addChests()
        
        // 7. Spawn monsters
        spawnMonsters()
        
        // 8. Add cobwebs
        addCobwebs()
        
        return tunnelCount
    }
    
    private fun <T> getWeightedRandom(items: List<Pair<T, Int>>): T {
        val total = items.sumOf { it.second }
        var remaining = random.nextInt(total)
        for ((item, weight) in items) {
            remaining -= weight
            if (remaining < 0) return item
        }
        return items.first().first
    }
    
    // ==================== ENTRANCE ====================
    
    private fun createEntrance() {
        // Small entrance hole
        for (dx in -1..1) {
            for (dz in -1..1) {
                for (dy in 0 downTo -5) {
                    carveAt(origin.add(dx, dy, dz))
                }
            }
        }
        
        // Ladder
        for (dy in 0 downTo -5) {
            val ladderPos = origin.add(-1, dy, 0)
            world.setBlockState(ladderPos, Blocks.LADDER.defaultState
                .with(Properties.HORIZONTAL_FACING, Direction.EAST), 3)
        }
        
        // Starter chest on solid ground
        val chestPos = origin.add(1, -4, 0)
        placeChestOnGround(chestPos, listOf(
            ItemStack(Items.STONE_PICKAXE, 1),
            ItemStack(Items.STONE_SWORD, 1),
            ItemStack(Items.TORCH, 32),
            ItemStack(Items.BREAD, 16)
        ))
        
        tunnelEndpoints.add(origin.down(5))
    }
    
    // ==================== RECURSIVE BRANCHING ====================
    
    private fun digBranch(
        x: Double, y: Double, z: Double,
        dirX: Double, dirY: Double, dirZ: Double,
        depth: Int, maxRecursion: Int
    ): Int {
        if (depth >= maxRecursion) return 0
        if (y < origin.y - maxDepth || y > origin.y) return 0
        
        var tunnelCount = 1
        var cx = x
        var cy = y
        var cz = z
        var dx = dirX
        var dy = dirY
        var dz = dirZ
        
        // Tunnel gets narrower with depth
        val radius = when {
            depth <= 1 -> 2
            depth <= 3 -> 2
            else -> 1
        }
        
        // Branch length varies
        val branchLength = random.nextInt(15, 35)
        
        for (step in 0 until branchLength) {
            // Check bounds
            if (cx < origin.x - width / 2 || cx > origin.x + width / 2) break
            if (cz < origin.z - this.depth / 2 || cz > origin.z + this.depth / 2) break
            if (cy < origin.y - maxDepth) break
            
            // Carve tunnel cross-section
            val ix = cx.toInt()
            val iy = cy.toInt()
            val iz = cz.toInt()
            
            for (rdx in -radius..radius) {
                for (rdy in -radius..radius) {
                    for (rdz in -radius..radius) {
                        if (rdx * rdx + rdy * rdy + rdz * rdz <= radius * radius + 1) {
                            carveAt(BlockPos(ix + rdx, iy + rdy, iz + rdz))
                        }
                    }
                }
            }
            
            // Move along direction
            cx += dx
            cy += dy
            cz += dz
            
            // Curve the tunnel naturally
            if (random.nextFloat() < 0.2f) {
                dx += (random.nextDouble() - 0.5) * 0.4
                dz += (random.nextDouble() - 0.5) * 0.4
                dy += (random.nextDouble() - 0.5) * 0.2
                
                // Normalize horizontal
                val hMag = sqrt(dx * dx + dz * dz)
                if (hMag > 1.2) {
                    dx /= hMag
                    dz /= hMag
                }
                dy = dy.coerceIn(-0.6, 0.3)
            }
            
            // SPAWN CHILD BRANCHES at intervals
            if (step > 5 && step % 8 == 0 && depth < maxRecursion - 1) {
                // Branch off in new direction
                val branchAngle = random.nextDouble() * Math.PI * 2
                val branchDirX = cos(branchAngle) * 0.9
                val branchDirZ = sin(branchAngle) * 0.9
                val branchDirY = (random.nextDouble() - 0.4) * 0.5 // Slight downward bias
                
                tunnelCount += digBranch(
                    cx, cy, cz,
                    branchDirX, branchDirY, branchDirZ,
                    depth + 1, maxRecursion
                )
            }
        }
        
        // Mark endpoint
        tunnelEndpoints.add(BlockPos(cx.toInt(), cy.toInt(), cz.toInt()))
        
        // Continue main trunk if not too deep
        if (depth < 2 && cy > origin.y - maxDepth + 10) {
            // Main trunk continues down with slight offset
            tunnelCount += digBranch(
                cx, cy, cz,
                (random.nextDouble() - 0.5) * 0.3,
                -0.7,
                (random.nextDouble() - 0.5) * 0.3,
                depth,
                maxRecursion
            )
        }
        
        return tunnelCount
    }
    
    // ==================== CHAMBERS ====================
    
    private fun createChambers() {
        // Create chambers at some tunnel endpoints
        val chamberCount = (tunnelEndpoints.size / 5).coerceIn(3, 12)
        val used = mutableSetOf<Int>()
        
        repeat(chamberCount) {
            if (tunnelEndpoints.isEmpty()) return
            
            var idx = random.nextInt(tunnelEndpoints.size)
            var attempts = 0
            while (used.contains(idx) && attempts < 20) {
                idx = random.nextInt(tunnelEndpoints.size)
                attempts++
            }
            used.add(idx)
            
            val center = tunnelEndpoints[idx]
            chamberPositions.add(center)
            
            val rx = random.nextInt(4, 8)
            val ry = random.nextInt(3, 6)
            val rz = random.nextInt(4, 8)
            
            for (dx in -rx..rx) {
                for (dy in -ry..ry) {
                    for (dz in -rz..rz) {
                        val dist = (dx.toFloat() / rx).let { it * it } +
                                   (dy.toFloat() / ry).let { it * it } +
                                   (dz.toFloat() / rz).let { it * it }
                        if (dist <= 1.0f) {
                            carveAt(center.add(dx, dy, dz))
                        }
                    }
                }
            }
        }
    }
    
    // ==================== HAZARDS ====================
    
    private fun addWaterPools() {
        val poolCount = (width / 25).coerceIn(2, 6)
        repeat(poolCount) {
            if (tunnelEndpoints.isEmpty()) return
            val center = tunnelEndpoints[random.nextInt(tunnelEndpoints.size)]
            
            val rx = random.nextInt(2, 5)
            val rz = random.nextInt(2, 5)
            
            // Carve pool area
            for (dx in -rx..rx) {
                for (dz in -rz..rz) {
                    if (dx * dx + dz * dz <= rx * rz) {
                        carveAt(center.add(dx, 0, dz))
                        carveAt(center.add(dx, 1, dz))
                        carveAt(center.add(dx, 2, dz))
                        // Water at bottom
                        world.setBlockState(center.add(dx, 0, dz), Blocks.WATER.defaultState, 3)
                    }
                }
            }
        }
    }
    
    private fun addLavaPits() {
        val pitCount = (maxDepth / 20).coerceIn(1, 4)
        repeat(pitCount) {
            // Only in deep areas
            val deepEndpoints = tunnelEndpoints.filter { it.y < origin.y - maxDepth * 0.6 }
            if (deepEndpoints.isEmpty()) return
            
            val center = deepEndpoints[random.nextInt(deepEndpoints.size)]
            val rx = random.nextInt(2, 4)
            val rz = random.nextInt(2, 4)
            
            for (dx in -rx..rx) {
                for (dz in -rz..rz) {
                    if (dx * dx + dz * dz <= rx * rz) {
                        carveAt(center.add(dx, 0, dz))
                        carveAt(center.add(dx, 1, dz))
                        world.setBlockState(center.add(dx, 0, dz), Blocks.LAVA.defaultState, 3)
                    }
                }
            }
            
            // Obsidian warning border
            for (dx in -(rx + 1)..(rx + 1)) {
                for (dz in -(rz + 1)..(rz + 1)) {
                    val dist = dx * dx + dz * dz
                    if (dist > rx * rz && dist <= (rx + 1) * (rz + 1) + 2) {
                        world.setBlockState(center.add(dx, 0, dz), Blocks.OBSIDIAN.defaultState, 3)
                    }
                }
            }
        }
    }
    
    private fun addSpikeTraps() {
        val trapCount = (width / 15).coerceIn(3, 10)
        repeat(trapCount) {
            for (attempt in 0 until 15) {
                if (tunnelEndpoints.isEmpty()) return
                val pos = tunnelEndpoints[random.nextInt(tunnelEndpoints.size)]
                
                if (carvedPositions.contains(pos)) {
                    // Dig pit below
                    for (dy in 1..4) {
                        for (dx in -1..1) {
                            for (dz in -1..1) {
                                carveAt(pos.add(dx, -dy, dz))
                            }
                        }
                    }
                    
                    // Spikes at bottom
                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            try {
                                world.setBlockState(
                                    pos.add(dx, -3, dz),
                                    Blocks.POINTED_DRIPSTONE.defaultState
                                        .with(Properties.VERTICAL_DIRECTION, Direction.UP), 3
                                )
                            } catch (_: Exception) {}
                        }
                    }
                    world.setBlockState(pos.down(4), Blocks.MAGMA_BLOCK.defaultState, 3)
                    break
                }
            }
        }
    }
    
    // ==================== LIGHTING ====================
    
    private fun addLighting() {
        // Torches in tunnels
        for (pos in carvedPositions) {
            if (random.nextFloat() < 0.02f) {
                val below = pos.down()
                if (!carvedPositions.contains(below)) {
                    world.setBlockState(pos, Blocks.TORCH.defaultState, 3)
                }
            }
        }
        
        // Lanterns in chambers
        for (chamber in chamberPositions) {
            if (carvedPositions.contains(chamber.up(2))) {
                world.setBlockState(chamber.up(2), Blocks.LANTERN.defaultState, 3)
            }
        }
    }
    
    // ==================== CHESTS ====================
    
    private fun addChests() {
        // Chests in chambers
        for (chamber in chamberPositions) {
            val depthRatio = (origin.y - chamber.y).toFloat() / maxDepth
            val chestCount = when {
                depthRatio > 0.7f -> random.nextInt(2, 4)
                depthRatio > 0.4f -> random.nextInt(1, 3)
                else -> 1
            }
            
            repeat(chestCount) {
                val cx = chamber.x + random.nextInt(-3, 4)
                val cz = chamber.z + random.nextInt(-3, 4)
                
                // Find floor
                for (cy in chamber.y downTo chamber.y - 5) {
                    val pos = BlockPos(cx, cy, cz)
                    val below = pos.down()
                    
                    if (carvedPositions.contains(pos) && !carvedPositions.contains(below)) {
                        placeDepthLoot(pos, depthRatio)
                        break
                    }
                }
            }
        }
        
        // Random chests at tunnel endpoints
        for (endpoint in tunnelEndpoints) {
            if (random.nextFloat() < 0.15f) {
                // Find floor near endpoint
                for (cy in endpoint.y downTo endpoint.y - 3) {
                    val pos = BlockPos(endpoint.x, cy, endpoint.z)
                    val below = pos.down()
                    
                    if (carvedPositions.contains(pos) && !carvedPositions.contains(below)) {
                        val depthRatio = (origin.y - pos.y).toFloat() / maxDepth
                        placeDepthLoot(pos, depthRatio)
                        break
                    }
                }
            }
        }
    }
    
    private fun placeChestOnGround(pos: BlockPos, items: List<ItemStack>) {
        world.setBlockState(pos, Blocks.CHEST.defaultState, 3)
        val be = world.getBlockEntity(pos)
        if (be is ChestBlockEntity) {
            items.forEachIndexed { i, stack -> be.setStack(i, stack) }
        }
    }
    
    private fun placeDepthLoot(pos: BlockPos, depthRatio: Float) {
        world.setBlockState(pos, Blocks.CHEST.defaultState, 3)
        val be = world.getBlockEntity(pos)
        if (be is ChestBlockEntity) {
            val itemCount = when {
                depthRatio > 0.7f -> random.nextInt(5, 9)
                depthRatio > 0.4f -> random.nextInt(3, 6)
                else -> random.nextInt(2, 4)
            }
            
            val usedSlots = mutableSetOf<Int>()
            repeat(itemCount) {
                var slot = random.nextInt(27)
                while (usedSlots.contains(slot)) slot = random.nextInt(27)
                usedSlots.add(slot)
                
                val item = when {
                    depthRatio > 0.7f -> getWeightedRandom(RARE_LOOT)
                    depthRatio > 0.4f -> getWeightedRandom(UNCOMMON_LOOT)
                    else -> getWeightedRandom(COMMON_LOOT)
                }
                
                val count = when (item) {
                    Items.TORCH -> random.nextInt(8, 24)
                    Items.ARROW -> random.nextInt(8, 32)
                    Items.BREAD, Items.APPLE -> random.nextInt(2, 8)
                    Items.IRON_INGOT -> random.nextInt(1, 6)
                    Items.DIAMOND, Items.EMERALD -> random.nextInt(1, 4)
                    else -> 1
                }
                
                be.setStack(slot, ItemStack(item, count))
            }
        }
    }
    
    // ==================== MONSTERS ====================
    
    private fun spawnMonsters() {
        // In chambers
        for (chamber in chamberPositions) {
            val depthRatio = (origin.y - chamber.y).toFloat() / maxDepth
            val count = when {
                depthRatio > 0.7f -> random.nextInt(3, 6)
                depthRatio > 0.4f -> random.nextInt(2, 4)
                else -> random.nextInt(1, 2)
            }
            
            repeat(count) {
                val spawnPos = chamber.add(random.nextInt(-3, 4), 0, random.nextInt(-3, 4))
                if (carvedPositions.contains(spawnPos)) {
                    spawnMonster(spawnPos, depthRatio)
                }
            }
        }
    }
    
    private fun spawnMonster(pos: BlockPos, depthRatio: Float) {
        val entityType = when {
            depthRatio > 0.7f && random.nextFloat() < 0.3f -> EntityType.SKELETON
            depthRatio > 0.5f && random.nextFloat() < 0.4f -> EntityType.SPIDER
            else -> EntityType.CAVE_SPIDER
        }
        
        val entity = entityType.create(world, null, pos, SpawnReason.COMMAND, false, false)
        if (entity != null) {
            entity.refreshPositionAndAngles(
                pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
                random.nextFloat() * 360f, 0f
            )
            world.spawnEntityAndPassengers(entity)
        }
    }
    
    // ==================== DECORATION ====================
    
    private fun addCobwebs() {
        for (pos in carvedPositions) {
            if (random.nextFloat() < 0.006f) {
                world.setBlockState(pos, Blocks.COBWEB.defaultState, 3)
            }
        }
    }
    
    // ==================== UTILITY ====================
    
    private fun carveAt(pos: BlockPos) {
        if (!carvedPositions.contains(pos)) {
            carvedPositions.add(pos)
            world.setBlockState(pos, Blocks.AIR.defaultState, 3)
        }
    }
}
