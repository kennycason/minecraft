package com.kenny.antfarm

import net.minecraft.block.Block
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
import kotlin.math.abs

class AntFarmGenerator(
    private val world: ServerWorld,
    private val origin: BlockPos,
    private val width: Int,
    private val height: Int,
    private val depth: Int
) {
    
    companion object {
        val FRAME_BLOCK = Blocks.BEDROCK
        val WALL_BLOCK = Blocks.GLASS
        val TUNNEL_BLOCK = Blocks.AIR
        val FLOOR_BLOCK = Blocks.BEDROCK
        val CEILING_BLOCK = Blocks.BEDROCK
        val COBWEB = Blocks.COBWEB
        val VINE = Blocks.VINE
        val MAGMA_BLOCK = Blocks.MAGMA_BLOCK
        val SOUL_SAND = Blocks.SOUL_SAND
        val SPIKE_BLOCK = Blocks.POINTED_DRIPSTONE
        
        val FILL_BLOCKS = listOf(
            Blocks.DIRT to 55, Blocks.COARSE_DIRT to 15, Blocks.ROOTED_DIRT to 10,
            Blocks.MUD to 8, Blocks.CLAY to 7, Blocks.GRAVEL to 5
        )
        val SURFACE_BLOCKS = listOf(
            Blocks.GRASS_BLOCK to 50, Blocks.MOSS_BLOCK to 25,
            Blocks.PODZOL to 15, Blocks.MYCELIUM to 10
        )
        val PLANT_BLOCKS = listOf(
            Blocks.SHORT_GRASS to 40, Blocks.TALL_GRASS to 15, Blocks.FERN to 10,
            Blocks.DANDELION to 10, Blocks.POPPY to 10, Blocks.AZURE_BLUET to 5,
            Blocks.RED_MUSHROOM to 5, Blocks.BROWN_MUSHROOM to 5
        )
        val WEAPON_LOOT = listOf(
            Items.IRON_SWORD to 30, Items.STONE_SWORD to 25, Items.DIAMOND_SWORD to 5,
            Items.BOW to 15, Items.CROSSBOW to 10, Items.TRIDENT to 2, Items.IRON_AXE to 13
        )
        val ARMOR_LOOT = listOf(
            Items.IRON_HELMET to 20, Items.IRON_CHESTPLATE to 15, Items.IRON_LEGGINGS to 15,
            Items.IRON_BOOTS to 20, Items.DIAMOND_HELMET to 5, Items.DIAMOND_CHESTPLATE to 3,
            Items.CHAINMAIL_CHESTPLATE to 12, Items.LEATHER_BOOTS to 10
        )
        val TOOL_LOOT = listOf(
            Items.IRON_PICKAXE to 25, Items.DIAMOND_PICKAXE to 5, Items.STONE_PICKAXE to 30,
            Items.TORCH to 20, Items.SHIELD to 15, Items.FISHING_ROD to 5
        )
        val FOOD_LOOT = listOf(
            Items.BREAD to 30, Items.COOKED_BEEF to 20, Items.GOLDEN_APPLE to 5,
            Items.COOKED_PORKCHOP to 20, Items.APPLE to 15, Items.COOKIE to 10
        )
        val VALUABLE_LOOT = listOf(
            Items.GOLD_INGOT to 30, Items.IRON_INGOT to 35, Items.DIAMOND to 10,
            Items.EMERALD to 15, Items.LAPIS_LAZULI to 10
        )
    }
    
    private val tunnelPositions = mutableSetOf<BlockPos>()
    private val chestPositions = mutableListOf<BlockPos>()
    private val trapPositions = mutableListOf<BlockPos>()
    private val monsterRoomCenters = mutableListOf<Triple<Int, Int, Int>>()
    private val random = Random(System.currentTimeMillis())
    
    // Track tunnel endpoints for connecting
    private val tunnelEndpoints = mutableListOf<Triple<Int, Int, Int>>()
    
    fun generate(): Int {
        createFrame()
        createGlassWalls()
        fillWithDirt()
        val tunnelCount = carveVietCongTunnels()
        addSurfaceVegetation()
        createEntrance()
        addSideLadder()
        addCobwebsAndVines()
        addTorchLighting() // Prevent Endermen spawns!
        addTraps()
        addLootChests()
        addEarlyGameChests() // Extra chests in upper areas!
        spawnMonsters()
        reinforceGlassWalls() // Protect glass AFTER all tunneling!
        return tunnelCount
    }
    
    // Extra chests in the upper/easier areas to help get started
    private fun addEarlyGameChests() {
        val upperThird = height - (height / 3)
        val earlyChestCount = (width / 10).coerceIn(5, 15)
        
        repeat(earlyChestCount) {
            for (attempt in 0 until 40) {
                val x = random.nextInt(5, width - 5)
                val y = random.nextInt(upperThird, height - 8) // Upper third only
                val z = random.nextInt(2, depth - 2)
                
                val pos = origin.add(x, y, z)
                val belowPos = origin.add(x, y - 1, z)
                
                if (tunnelPositions.contains(pos) && !tunnelPositions.contains(belowPos)) {
                    // Place chest with early-game loot
                    world.setBlockState(pos, Blocks.CHEST.defaultState, 3)
                    val blockEntity = world.getBlockEntity(pos)
                    if (blockEntity is ChestBlockEntity) {
                        val items = listOf(
                            Items.TORCH, Items.BREAD, Items.APPLE, Items.STONE_PICKAXE,
                            Items.STONE_SWORD, Items.LEATHER_BOOTS, Items.IRON_INGOT, Items.COAL
                        )
                        val itemCount = random.nextInt(2, 5)
                        repeat(itemCount) { i ->
                            val item = items[random.nextInt(items.size)]
                            val count = if (item == Items.TORCH) random.nextInt(4, 12) else random.nextInt(1, 4)
                            blockEntity.setStack(i, ItemStack(item, count))
                        }
                    }
                    break
                }
            }
        }
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
    
    private fun createFrame() {
        for (x in 0 until width) {
            for (z in 0 until depth) {
                setBlock(x, 0, z, FLOOR_BLOCK)
                setBlock(x, height - 1, z, CEILING_BLOCK)
            }
        }
        for (y in 0 until height) {
            for (z in 0 until depth) {
                setBlock(0, y, z, FRAME_BLOCK)
                setBlock(width - 1, y, z, FRAME_BLOCK)
            }
        }
    }
    
    private fun createGlassWalls() {
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                setBlock(x, y, 0, WALL_BLOCK)
                setBlock(x, y, depth - 1, WALL_BLOCK)
            }
        }
        // NOTE: Barriers are placed AFTER tunneling in reinforceGlassWalls()
    }
    
    // Called AFTER all tunneling to protect glass from explosions
    private fun reinforceGlassWalls() {
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                // Re-place glass (in case tunnels touched it)
                val frontGlass = origin.add(x, y, 0)
                val backGlass = origin.add(x, y, depth - 1)
                world.setBlockState(frontGlass, WALL_BLOCK.defaultState, 3)
                world.setBlockState(backGlass, WALL_BLOCK.defaultState, 3)
                
                // Barrier layer just inside - prevents breaking/explosions
                val frontBarrier = origin.add(x, y, 1)
                val backBarrier = origin.add(x, y, depth - 2)
                world.setBlockState(frontBarrier, Blocks.BARRIER.defaultState, 3)
                world.setBlockState(backBarrier, Blocks.BARRIER.defaultState, 3)
            }
        }
    }
    
    private fun fillWithDirt() {
        val fillTopY = height - 5
        for (x in 1 until width - 1) {
            for (y in 1..fillTopY) {
                for (z in 1 until depth - 1) {
                    setBlock(x, y, z, getWeightedRandom(FILL_BLOCKS))
                }
            }
        }
    }
    
    private fun addSurfaceVegetation() {
        val surfaceY = height - 5
        for (x in 2 until width - 2) {
            for (z in 1 until depth - 1) {
                setBlock(x, surfaceY, z, getWeightedRandom(SURFACE_BLOCKS))
            }
        }
        for (y in (surfaceY + 1) until (height - 1)) {
            for (x in 2 until width - 2) {
                for (z in 1 until depth - 1) {
                    setBlock(x, y, z, TUNNEL_BLOCK)
                }
            }
        }
        for (x in 2 until width - 2) {
            for (z in 1 until depth - 1) {
                if (random.nextFloat() < 0.25f) {
                    setBlock(x, surfaceY + 1, z, getWeightedRandom(PLANT_BLOCKS))
                }
            }
        }
    }
    
    private fun createEntrance() {
        // MULTIPLE ENTRANCES like a real ant farm!
        val entranceCount = (width / 25).coerceIn(2, 5)
        
        for (i in 0 until entranceCount) {
            val entranceX = (width / (entranceCount + 1)) * (i + 1)
            val entranceZ = depth / 2 + random.nextInt(-1, 2)
            
            // Create entrance hole
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val x = entranceX + dx
                    val z = entranceZ + dz
                    if (x in 1 until width - 1 && z in 1 until depth - 1) {
                        setBlock(x, height - 1, z, TUNNEL_BLOCK)
                        setBlock(x, height, z, TUNNEL_BLOCK)
                    }
                }
            }
            
            // Winding entrance shaft going down
            var ex = entranceX
            var ez = entranceZ
            for (y in (height - 12) until height) {
                for (dy in 0..2) {
                    carveBlock(ex, y - dy, ez)
                    if (ez + 1 < depth - 1) carveBlock(ex, y - dy, ez + 1)
                }
                // Slight wandering as it goes down
                if (random.nextFloat() < 0.2f) {
                    ex += if (random.nextBoolean()) 1 else -1
                    ex = ex.coerceIn(4, width - 5)
                }
            }
            tunnelEndpoints.add(Triple(ex, height - 12, ez))
            
            // STARTER CHEST at each entrance with basic supplies!
            placeStarterChest(origin.add(entranceX, height - 3, entranceZ))
        }
    }
    
    private fun placeStarterChest(pos: BlockPos) {
        world.setBlockState(pos, Blocks.CHEST.defaultState, 3)
        
        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity is ChestBlockEntity) {
            // Essential starter gear
            blockEntity.setStack(0, ItemStack(Items.STONE_PICKAXE, 1))
            blockEntity.setStack(1, ItemStack(Items.STONE_SWORD, 1))
            blockEntity.setStack(2, ItemStack(Items.TORCH, 16))
            blockEntity.setStack(3, ItemStack(Items.BREAD, 8))
            blockEntity.setStack(4, ItemStack(Items.LEATHER_HELMET, 1))
            blockEntity.setStack(5, ItemStack(Items.LEATHER_BOOTS, 1))
        }
    }
    
    private fun addSideLadder() {
        // OUTSIDE ladders - to climb UP to the top of the ant farm!
        // Left side - on the outside of the glass (z = -1 from structure, attached to z=0)
        for (y in 0 until height + 5) {
            // Place a block to attach ladder to, then the ladder
            val attachPos = origin.add(1, y, -1)
            val ladderPos = origin.add(1, y, 0)
            world.setBlockState(attachPos, Blocks.STONE_BRICKS.defaultState, 3)
            // Ladder on the glass side facing outward
        }
        for (y in 0 until height + 5) {
            val ladderPos = origin.add(0, y, 0)
            world.setBlockState(ladderPos, Blocks.LADDER.defaultState
                .with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 3)
        }
        
        // Right side
        for (y in 0 until height + 5) {
            val attachPos = origin.add(width - 2, y, -1)
            world.setBlockState(attachPos, Blocks.STONE_BRICKS.defaultState, 3)
        }
        for (y in 0 until height + 5) {
            val ladderPos = origin.add(width - 1, y, 0)
            world.setBlockState(ladderPos, Blocks.LADDER.defaultState
                .with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 3)
        }
    }
    
    // ==================== VIET CONG TUNNEL SYSTEM ====================
    
    private fun carveVietCongTunnels(): Int {
        var count = 0
        
        // 1. Create a few "peek" tunnels that touch the glass (so you can see SOMETHING)
        val peekTunnelCount = (width / 30).coerceIn(2, 5)
        repeat(peekTunnelCount) {
            carvePeekTunnel()
            count++
        }
        
        // 2. MAIN MAZE: Winding tunnels - MORE TUNNELING!
        val mazeStartPoints = (width * height) / 120
        repeat(mazeStartPoints.coerceIn(15, 40)) {
            carveWindingTunnel(random.nextInt(6, width - 6), random.nextInt(5, height - 10), false)
            count++
        }
        
        // 3. Deep hidden tunnels - increased density
        val hiddenTunnelCount = (width * height) / 100
        repeat(hiddenTunnelCount.coerceIn(15, 50)) {
            carveDeepHiddenTunnel()
            count++
        }
        
        // 4. Vertical shafts (hidden, connecting levels) - MORE for better connectivity
        val shaftCount = (width / 7).coerceIn(8, 20)
        repeat(shaftCount) {
            carveHiddenVerticalShaft()
            count++
        }
        
        // 4b. Horizontal cross-connections between vertical areas
        val crossConnections = (width / 12).coerceIn(4, 12)
        repeat(crossConnections) {
            carveHorizontalCrossConnection()
            count++
        }
        
        // 5. Small chambers (NOT glass-to-glass, hidden inside)
        val smallChamberCount = (width / 20).coerceIn(3, 8)
        repeat(smallChamberCount) {
            carveSmallChamber()
        }
        
        // 6. A couple medium rooms (partial depth, some visibility)
        val mediumRoomCount = (width / 40).coerceIn(1, 3)
        repeat(mediumRoomCount) {
            carveMediumRoom()
        }
        
        // 7. Monster dens (small, hidden)
        val monsterDenCount = (width / 25).coerceIn(2, 6)
        repeat(monsterDenCount) {
            carveMonsterDen()
        }
        
        // 8. Connect isolated tunnels (MORE connections!)
        connectTunnelNetwork()
        connectTunnelNetwork() // Do it twice for better connectivity
        count += 10
        
        // 9. LIGHT SHAFTS - 1x1 tunnels to glass for natural light (keeps endermen out!)
        // LOTS of light shafts for maximum light penetration!
        val lightShaftCount = (width / 3).coerceIn(25, 80)
        repeat(lightShaftCount) {
            carveLightShaft()
        }
        
        // 10. SPIRAL TUNNELS - adds organic variety
        val spiralCount = (width / 30).coerceIn(2, 5)
        repeat(spiralCount) {
            carveSpiralTunnel()
        }
        
        // 11. TREE STRUCTURES - branching from a central point
        val treeCount = (width / 40).coerceIn(1, 3)
        repeat(treeCount) {
            carveTreeStructure()
        }
        
        // 12. CURVED PATHS - smooth arcing tunnels
        val curveCount = (width / 20).coerceIn(3, 8)
        repeat(curveCount) {
            carveCurvedPath()
        }
        
        // 13. Add dead ends and alcoves (fewer dead ends now)
        val deadEndCount = (width / 25).coerceIn(3, 8)
        repeat(deadEndCount) {
            carveDeadEnd()
        }
        
        // 14. THE QUEEN'S CHAMBER - hellish bottom area!
        carveQueensChamber()
        
        return count
    }
    
    // THE QUEEN'S CHAMBER - a hellish bottom area with spikes and platforms
    private fun carveQueensChamber() {
        val chamberWidth = (width / 3).coerceIn(15, 40)
        val chamberHeight = (height / 5).coerceIn(8, 15)
        
        val cx = width / 2
        val cy = 4 + chamberHeight / 2  // Near the bottom!
        
        // Carve the main chamber (full depth for drama!)
        for (dx in -chamberWidth/2..chamberWidth/2) {
            for (dy in -chamberHeight/2..chamberHeight/2) {
                for (z in 1 until depth - 1) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 3 until width - 3 && y in 2 until height - 6) {
                        carveBlock(x, y, z)
                    }
                }
            }
        }
        
        // Hellish floor - magma and soul sand
        val floorY = cy - chamberHeight/2
        for (dx in -chamberWidth/2..chamberWidth/2) {
            for (z in 1 until depth - 1) {
                val x = cx + dx
                if (x in 3 until width - 3) {
                    val floorBlock = when {
                        random.nextFloat() < 0.4f -> MAGMA_BLOCK
                        random.nextFloat() < 0.5f -> SOUL_SAND
                        else -> Blocks.BLACKSTONE
                    }
                    setBlock(x, floorY, z, floorBlock)
                }
            }
        }
        
        // Spikes from ceiling!
        val ceilingY = cy + chamberHeight/2
        for (dx in -chamberWidth/2..chamberWidth/2) {
            for (z in 1 until depth - 1) {
                val x = cx + dx
                if (x in 3 until width - 3 && random.nextFloat() < 0.15f) {
                    // Hanging dripstone spike
                    try {
                        for (dy in 0 until random.nextInt(1, 4)) {
                            val spikePos = origin.add(x, ceilingY - dy, z)
                            world.setBlockState(spikePos, SPIKE_BLOCK.defaultState
                                .with(Properties.VERTICAL_DIRECTION, Direction.DOWN), 3)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        
        // Spikes from floor!
        for (dx in -chamberWidth/2..chamberWidth/2) {
            for (z in 1 until depth - 1) {
                val x = cx + dx
                if (x in 3 until width - 3 && random.nextFloat() < 0.12f) {
                    try {
                        for (dy in 1..random.nextInt(1, 3)) {
                            val spikePos = origin.add(x, floorY + dy, z)
                            world.setBlockState(spikePos, SPIKE_BLOCK.defaultState
                                .with(Properties.VERTICAL_DIRECTION, Direction.UP), 3)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        
        // Floating platforms to jump between!
        val platformCount = (chamberWidth / 6).coerceIn(4, 10)
        repeat(platformCount) {
            val px = cx + random.nextInt(-chamberWidth/2 + 3, chamberWidth/2 - 3)
            val py = floorY + random.nextInt(3, chamberHeight - 3)
            val pz = random.nextInt(2, depth - 2)
            
            // 3x3 or 4x4 platform
            val platformSize = random.nextInt(1, 3)
            val platformBlock = if (random.nextFloat() < 0.3f) Blocks.OBSIDIAN else Blocks.DEEPSLATE_BRICKS
            
            for (pdx in -platformSize..platformSize) {
                for (pdz in -platformSize..platformSize) {
                    val platX = px + pdx
                    val platZ = pz + pdz
                    if (platX in 3 until width - 3 && platZ in 1 until depth - 1) {
                        setBlock(platX, py, platZ, platformBlock)
                    }
                }
            }
            
            // Chance for chest on platform (good loot!)
            if (random.nextFloat() < 0.4f) {
                chestPositions.add(origin.add(px, py + 1, pz))
            }
        }
        
        // QUEEN'S TREASURE - multiple chests with best loot
        val treasureCount = random.nextInt(3, 6)
        repeat(treasureCount) {
            val tx = cx + random.nextInt(-chamberWidth/4, chamberWidth/4)
            val tz = depth / 2 + random.nextInt(-2, 3)
            chestPositions.add(origin.add(tx, floorY + 1, tz))
        }
        
        // Spawn lots of monsters in the Queen's Chamber!
        val monsterCount = random.nextInt(8, 15)
        repeat(monsterCount) {
            val mx = cx + random.nextInt(-chamberWidth/3, chamberWidth/3)
            val my = floorY + 2
            val mz = random.nextInt(2, depth - 2)
            
            // Mix of dangerous enemies
            val entityType = when {
                random.nextFloat() < 0.3f -> EntityType.SKELETON
                random.nextFloat() < 0.4f -> EntityType.SPIDER
                else -> EntityType.CAVE_SPIDER
            }
            
            val entity = entityType.create(world, null, origin.add(mx, my, mz), SpawnReason.COMMAND, false, false)
            if (entity != null) {
                entity.refreshPositionAndAngles(
                    (origin.x + mx).toDouble() + 0.5,
                    (origin.y + my).toDouble(),
                    (origin.z + mz).toDouble() + 0.5,
                    random.nextFloat() * 360f, 0f
                )
                world.spawnEntityAndPassengers(entity)
            }
        }
        
        // Connect chamber to tunnel network
        tunnelEndpoints.add(Triple(cx - chamberWidth/2, cy, depth/2))
        tunnelEndpoints.add(Triple(cx + chamberWidth/2, cy, depth/2))
        tunnelEndpoints.add(Triple(cx, cy + chamberHeight/2, depth/2))
    }
    
    // SPIRAL TUNNEL - corkscrews down through the dirt
    private fun carveSpiralTunnel() {
        val centerX = random.nextInt(10, width - 10)
        val startY = random.nextInt(height / 2, height - 10)
        val centerZ = depth / 2
        
        var angle = 0.0
        var radius = random.nextInt(3, 6)
        var y = startY
        
        val spiralLength = random.nextInt(15, 30)
        
        repeat(spiralLength) {
            val x = centerX + (radius * kotlin.math.cos(angle)).toInt()
            val z = centerZ + (radius * kotlin.math.sin(angle) * 0.5).toInt() // Compressed in Z
            
            if (x in 4 until width - 4 && z in 1 until depth - 1 && y in 3 until height - 6) {
                for (dy in 0..2) {
                    carveBlock(x, y + dy, z)
                    if (z + 1 < depth - 1) carveBlock(x, y + dy, z + 1)
                }
            }
            
            angle += 0.4
            y--
            
            // Occasionally change radius
            if (random.nextFloat() < 0.1f) {
                radius += if (random.nextBoolean()) 1 else -1
                radius = radius.coerceIn(2, 8)
            }
        }
        
        tunnelEndpoints.add(Triple(centerX, y, centerZ))
    }
    
    // TREE STRUCTURE - branches radiating from central point
    private fun carveTreeStructure() {
        val rootX = random.nextInt(15, width - 15)
        val rootY = random.nextInt(height / 3, height - 15)
        val rootZ = depth / 2
        
        // Create central chamber (the "trunk")
        for (dx in -2..2) {
            for (dy in -2..2) {
                for (dz in -1..1) {
                    if (rootZ + dz in 1 until depth - 1) {
                        carveBlock(rootX + dx, rootY + dy, rootZ + dz)
                    }
                }
            }
        }
        
        // Branch out in multiple directions
        val branchCount = random.nextInt(4, 7)
        repeat(branchCount) { i ->
            val angle = (i.toFloat() / branchCount) * 2 * kotlin.math.PI
            var bx = rootX
            var by = rootY
            var bz = rootZ
            
            val branchLength = random.nextInt(8, 20)
            val goingUp = random.nextBoolean()
            
            repeat(branchLength) {
                // Move in branch direction
                bx += (kotlin.math.cos(angle) * 1.5).toInt()
                if (goingUp) by += if (random.nextFloat() < 0.3f) 1 else 0
                else by -= if (random.nextFloat() < 0.3f) 1 else 0
                bz += (kotlin.math.sin(angle) * 0.3).toInt()
                
                bx = bx.coerceIn(4, width - 5)
                by = by.coerceIn(3, height - 6)
                bz = bz.coerceIn(1, depth - 2)
                
                for (dy in 0..2) {
                    carveBlock(bx, by + dy, bz)
                }
            }
            
            tunnelEndpoints.add(Triple(bx, by, bz))
        }
        
        // Chance for chest at root
        if (random.nextFloat() < 0.6f) {
            chestPositions.add(origin.add(rootX, rootY - 1, rootZ))
        }
    }
    
    // CURVED PATH - smooth arcing tunnel
    private fun carveCurvedPath() {
        val startX = random.nextInt(8, width - 8)
        val startY = random.nextInt(5, height - 10)
        val startZ = random.nextInt(2, depth - 2)
        
        var x = startX.toFloat()
        var y = startY.toFloat()
        var z = startZ.toFloat()
        
        // Random curve direction
        var dx = (random.nextFloat() - 0.5f) * 2
        var dy = (random.nextFloat() - 0.5f) * 0.5f
        val dz = (random.nextFloat() - 0.5f) * 0.3f
        
        // Curve acceleration
        val curveX = (random.nextFloat() - 0.5f) * 0.1f
        val curveY = (random.nextFloat() - 0.5f) * 0.05f
        
        val pathLength = random.nextInt(15, 35)
        
        repeat(pathLength) {
            val ix = x.toInt()
            val iy = y.toInt()
            val iz = z.toInt()
            
            if (ix in 4 until width - 4 && iy in 3 until height - 6 && iz in 1 until depth - 1) {
                for (cdy in 0..2) {
                    carveBlock(ix, iy + cdy, iz)
                    if (iz + 1 < depth - 1) carveBlock(ix, iy + cdy, iz + 1)
                }
            }
            
            x += dx
            y += dy
            z += dz
            
            // Apply curve
            dx += curveX
            dy += curveY
            
            // Clamp velocity
            dx = dx.coerceIn(-1.5f, 1.5f)
            dy = dy.coerceIn(-0.5f, 0.5f)
        }
        
        tunnelEndpoints.add(Triple(x.toInt().coerceIn(4, width - 5), y.toInt().coerceIn(3, height - 6), z.toInt().coerceIn(1, depth - 2)))
    }
    
    // Light shafts - narrow 1x1 tunnels to glass for natural light
    private fun carveLightShaft() {
        // Pick a random tunnel position
        if (tunnelEndpoints.isEmpty()) return
        
        val (startX, startY, startZ) = tunnelEndpoints[random.nextInt(tunnelEndpoints.size)]
        
        // Carve toward front or back glass
        val targetZ = if (random.nextBoolean()) 1 else depth - 2
        
        var z = startZ
        val direction = if (targetZ < z) -1 else 1
        
        while (z != targetZ && z in 1 until depth - 1) {
            // Just 1x1 light shaft
            carveBlock(startX, startY, z)
            carveBlock(startX, startY + 1, z)
            z += direction
        }
    }
    
    // Peek tunnels - short sections visible from glass
    private fun carvePeekTunnel() {
        val y = random.nextInt(5, height - 10)
        val startX = random.nextInt(8, width - 8)
        
        // Short horizontal section at glass level
        val length = random.nextInt(8, 20)
        var x = startX
        
        for (i in 0 until length) {
            // Only first 2 blocks from glass visible
            for (z in 1..2) {
                carveBlock(x, y, z)
                carveBlock(x, y + 1, z)
            }
            // Then tunnel curves inward and becomes hidden
            if (i > 3 && random.nextFloat() < 0.3f) {
                // Branch into hidden area
                carveWindingTunnel(x, y, true)
            }
            
            x += if (random.nextBoolean()) 1 else -1
            x = x.coerceIn(5, width - 5)
        }
    }
    
    // Winding tunnels - the core of the maze
    private fun carveWindingTunnel(startX: Int, startY: Int, startHidden: Boolean) {
        var x = startX
        var y = startY
        // Z position - stay away from glass walls!
        var z = if (startHidden) random.nextInt(3, depth - 3) else random.nextInt(2, depth - 2)
        
        val length = random.nextInt(20, 60)
        var dir = random.nextInt(6) // 0=+x, 1=-x, 2=+y, 3=-y, 4=+z, 5=-z
        
        val tunnelWidth = if (random.nextFloat() < 0.5f) 2 else 3 // Wider tunnels
        val tunnelHeight = if (random.nextFloat() < 0.6f) 3 else 2 // Taller tunnels
        
        repeat(length) { step ->
            // Carve tunnel segment
            for (dy in 0 until tunnelHeight) {
                for (dz in 0 until tunnelWidth) {
                    val az = z + dz
                    if (az in 2 until depth - 2) { // Stay away from glass!
                        carveBlock(x, y + dy, az)
                    }
                }
            }
            
            // Move in current direction
            when (dir) {
                0 -> x++
                1 -> x--
                2 -> y++
                3 -> y--
                4 -> z++
                5 -> z--
            }
            
            // Keep away from edges (but z CAN touch glass occasionally)
            x = x.coerceIn(4, width - 5)
            y = y.coerceIn(3, height - 8)
            z = z.coerceIn(1, depth - 2) // Can meander to glass now!
            
            // Frequently change direction (maze-like)
            if (random.nextFloat() < 0.25f) {
                dir = random.nextInt(6)
            }
            
            // Occasionally branch
            if (random.nextFloat() < 0.08f && step > 5) {
                tunnelEndpoints.add(Triple(x, y, z))
                if (random.nextFloat() < 0.5f) {
                    carveDeepHiddenTunnel() // Spawn a branch
                }
            }
        }
        
        tunnelEndpoints.add(Triple(x, y, z))
    }
    
    // Deep hidden tunnels - completely invisible from outside
    private fun carveDeepHiddenTunnel() {
        // Start in the middle depth (far from glass)
        val zCenter = depth / 2
        val zVariation = (depth / 4).coerceAtLeast(1)
        
        var x = random.nextInt(5, width - 5)
        var y = random.nextInt(4, height - 8)
        var z = zCenter + random.nextInt(-zVariation, zVariation + 1)
        
        val length = random.nextInt(15, 40)
        var dir = random.nextInt(4) // Only horizontal for these: 0=+x, 1=-x, 2=+z, 3=-z
        
        val tunnelHeight = if (random.nextFloat() < 0.6f) 3 else 2
        
        repeat(length) {
            // 3-high tunnel, wider
            for (dy in 0 until tunnelHeight) {
                carveBlock(x, y + dy, z)
                // Make it 2 blocks wide in Z
                if (z + 1 in 2 until depth - 2) {
                    carveBlock(x, y + dy, z + 1)
                }
            }
            
            // Sometimes widen more
            if (random.nextFloat() < 0.3f) {
                val dz = if (random.nextBoolean()) 2 else -1
                if (z + dz in 2 until depth - 2) {
                    for (dy in 0 until tunnelHeight) {
                        carveBlock(x, y + dy, z + dz)
                    }
                }
            }
            
            when (dir) {
                0 -> x++
                1 -> x--
                2 -> z++
                3 -> z--
            }
            
            x = x.coerceIn(4, width - 5)
            z = z.coerceIn(1, depth - 2) // Can touch glass occasionally
            
            // Go up or down sometimes
            if (random.nextFloat() < 0.1f) {
                y += if (random.nextBoolean()) 1 else -1
                y = y.coerceIn(3, height - 8)
            }
            
            // Change direction frequently
            if (random.nextFloat() < 0.3f) {
                dir = random.nextInt(4)
            }
        }
    }
    
    // Horizontal cross-connection - connects different vertical sections
    private fun carveHorizontalCrossConnection() {
        val y = random.nextInt(5, height - 8)
        val z = random.nextInt(2, depth - 2)
        
        // Pick start and end X positions
        val startX = random.nextInt(5, width / 2)
        val endX = random.nextInt(width / 2, width - 5)
        
        var currentY = y
        
        for (x in startX..endX) {
            for (dy in 0..2) {
                carveBlock(x, currentY + dy, z)
                if (z + 1 < depth - 1) carveBlock(x, currentY + dy, z + 1)
            }
            
            // Slight vertical variation
            if (random.nextFloat() < 0.08f) {
                currentY += if (random.nextBoolean()) 1 else -1
                currentY = currentY.coerceIn(4, height - 8)
            }
        }
        
        tunnelEndpoints.add(Triple(startX, y, z))
        tunnelEndpoints.add(Triple(endX, y, z))
    }
    
    // Hidden vertical shafts - bigger for better connectivity
    private fun carveHiddenVerticalShaft() {
        val x = random.nextInt(5, width - 5)
        val z = random.nextInt(3, depth - 3) // Away from glass
        
        val startY = random.nextInt(3, height / 2)
        val endY = random.nextInt(height / 2, height - 8)
        
        // Wider shaft (2x2 or 2x3)
        val shaftWidthX = if (random.nextBoolean()) 2 else 1
        val shaftWidthZ = if (random.nextBoolean()) 2 else 1
        
        for (y in startY..endY) {
            for (dx in 0 until shaftWidthX) {
                for (dz in 0 until shaftWidthZ) {
                    if (z + dz in 2 until depth - 2) {
                        carveBlock(x + dx, y, z + dz)
                    }
                }
            }
        }
        
        tunnelEndpoints.add(Triple(x, startY, z))
        tunnelEndpoints.add(Triple(x, endY, z))
    }
    
    // Small chambers - hidden alcoves
    private fun carveSmallChamber() {
        val cx = random.nextInt(6, width - 6)
        val cy = random.nextInt(5, height - 10)
        val cz = random.nextInt(3, depth - 3) // Hidden from glass
        
        val rx = random.nextInt(2, 4)
        val ry = random.nextInt(2, 3)
        val rz = random.nextInt(1, 3).coerceAtMost((depth - 4) / 2)
        
        for (dx in -rx..rx) {
            for (dy in -ry..ry) {
                for (dz in -rz..rz) {
                    val x = cx + dx
                    val y = cy + dy
                    val z = cz + dz
                    if (x in 3 until width - 3 && y in 2 until height - 6 && z in 2 until depth - 2) {
                        carveBlock(x, y, z)
                    }
                }
            }
        }
        
        // Always add chest in chambers!
        chestPositions.add(origin.add(cx, cy - ry + 1, cz))
        // Sometimes add a second chest
        if (random.nextFloat() < 0.4f) {
            chestPositions.add(origin.add(cx + 1, cy - ry + 1, cz))
        }
        
        tunnelEndpoints.add(Triple(cx, cy, cz))
    }
    
    // Medium rooms - partial visibility
    private fun carveMediumRoom() {
        val cx = random.nextInt(10, width - 10)
        val cy = random.nextInt(6, height - 12)
        
        // These can be closer to glass but not touching
        val cz = random.nextInt(2, depth - 2)
        
        val rx = random.nextInt(3, 5)
        val ry = random.nextInt(2, 4)
        // Depth limited - not glass to glass!
        val rz = random.nextInt(2, 4).coerceAtMost((depth - 4) / 2)
        
        for (dx in -rx..rx) {
            for (dy in -ry..ry) {
                for (dz in -rz..rz) {
                    // Ellipsoid shape
                    val dist = (dx.toFloat() / rx).let { it * it } +
                               (dy.toFloat() / ry).let { it * it } +
                               (dz.toFloat() / rz).let { it * it }
                    if (dist <= 1.2f) {
                        val x = cx + dx
                        val y = cy + dy
                        val z = cz + dz
                        if (x in 3 until width - 3 && y in 2 until height - 6 && z in 1 until depth - 1) {
                            carveBlock(x, y, z)
                        }
                    }
                }
            }
        }
        
        chestPositions.add(origin.add(cx, cy - ry + 1, cz))
        tunnelEndpoints.add(Triple(cx, cy, cz))
    }
    
    // Monster dens - small, dangerous
    private fun carveMonsterDen() {
        val cx = random.nextInt(8, width - 8)
        val cy = random.nextInt(5, height - 10)
        val cz = random.nextInt(3, depth - 3)
        
        monsterRoomCenters.add(Triple(cx, cy, cz))
        
        val rx = random.nextInt(2, 4)
        val ry = random.nextInt(2, 3)
        val rz = random.nextInt(1, 3)
        
        for (dx in -rx..rx) {
            for (dy in -ry..ry) {
                for (dz in -rz..rz) {
                    val x = cx + dx
                    val y = cy + dy
                    val z = cz + dz
                    if (x in 4 until width - 4 && y in 3 until height - 6 && z in 2 until depth - 2) {
                        carveBlock(x, y, z)
                    }
                }
            }
        }
    }
    
    // Connect isolated tunnel sections
    private fun connectTunnelNetwork() {
        if (tunnelEndpoints.size < 2) return
        
        // Try to connect some endpoints
        val connectionCount = (tunnelEndpoints.size / 3).coerceIn(3, 10)
        
        repeat(connectionCount) {
            if (tunnelEndpoints.size < 2) return
            
            val idx1 = random.nextInt(tunnelEndpoints.size)
            val idx2 = random.nextInt(tunnelEndpoints.size)
            if (idx1 == idx2) return@repeat
            
            val (x1, y1, z1) = tunnelEndpoints[idx1]
            val (x2, y2, z2) = tunnelEndpoints[idx2]
            
            // Only connect if not too far apart
            val dist = abs(x1 - x2) + abs(y1 - y2) + abs(z1 - z2)
            if (dist > 40) return@repeat
            
            // Carve connecting tunnel
            carveConnectingTunnel(x1, y1, z1, x2, y2, z2)
        }
    }
    
    private fun carveConnectingTunnel(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {
        var x = x1
        var y = y1
        var z = z1
        
        val maxSteps = 100
        var steps = 0
        
        while ((x != x2 || y != y2 || z != z2) && steps < maxSteps) {
            carveBlock(x, y, z)
            carveBlock(x, y + 1, z)
            
            // Move toward target with some randomness
            val dx = x2 - x
            val dy = y2 - y
            val dz = z2 - z
            
            when {
                abs(dx) > abs(dy) && abs(dx) > abs(dz) -> x += if (dx > 0) 1 else -1
                abs(dy) > abs(dz) -> y += if (dy > 0) 1 else -1
                dz != 0 -> z += if (dz > 0) 1 else -1
                else -> x += if (dx > 0) 1 else -1
            }
            
            // Add some randomness
            if (random.nextFloat() < 0.2f) {
                when (random.nextInt(3)) {
                    0 -> x += if (random.nextBoolean()) 1 else -1
                    1 -> y += if (random.nextBoolean()) 1 else -1
                    2 -> z += if (random.nextBoolean()) 1 else -1
                }
            }
            
            x = x.coerceIn(4, width - 5)
            y = y.coerceIn(3, height - 8)
            z = z.coerceIn(2, depth - 3)
            
            steps++
        }
    }
    
    // Dead ends - create exploration interest
    private fun carveDeadEnd() {
        if (tunnelEndpoints.isEmpty()) return
        
        val (startX, startY, startZ) = tunnelEndpoints[random.nextInt(tunnelEndpoints.size)]
        
        var x = startX
        var y = startY
        var z = startZ
        
        val length = random.nextInt(3, 10)
        val dir = random.nextInt(4)
        
        repeat(length) {
            carveBlock(x, y, z)
            carveBlock(x, y + 1, z)
            
            when (dir) {
                0 -> x++
                1 -> x--
                2 -> z++
                3 -> z--
            }
            
            x = x.coerceIn(4, width - 5)
            z = z.coerceIn(2, depth - 3)
        }
        
        // End with alcove/trap/chest
        when {
            random.nextFloat() < 0.3f -> chestPositions.add(origin.add(x, y, z))
            random.nextFloat() < 0.3f -> trapPositions.add(origin.add(x, y, z))
        }
    }
    
    // ==================== DECORATIONS & FEATURES ====================
    
    private fun addCobwebsAndVines() {
        for (pos in tunnelPositions) {
            // Reduced cobwebs by 75%
            if (random.nextFloat() < 0.012f) {
                world.setBlockState(pos, COBWEB.defaultState)
            } else if (random.nextFloat() < 0.01f) {
                world.setBlockState(pos, VINE.defaultState)
            }
        }
    }
    
    // Add torches throughout tunnels to prevent Endermen/hostile mob spawns
    private fun addTorchLighting() {
        // Place torches every ~8 blocks in tunnels
        val torchSpacing = 8
        var torchCount = 0
        
        for (pos in tunnelPositions) {
            val localX = pos.x - origin.x
            val localY = pos.y - origin.y
            val localZ = pos.z - origin.z
            
            // Place torch if on grid and there's a wall nearby to attach to
            if (localX % torchSpacing == 0 && localY % torchSpacing == 0) {
                // Check if this is floor level (block below is solid)
                val belowPos = pos.down()
                if (!tunnelPositions.contains(belowPos)) {
                    // Place torch on floor
                    if (random.nextFloat() < 0.7f) { // 70% chance
                        world.setBlockState(pos, Blocks.TORCH.defaultState, 3)
                        torchCount++
                    }
                }
            }
        }
        
        // Also embed some glowstone in the dirt for ambient light
        val glowstoneCount = (width * height) / 200
        repeat(glowstoneCount.coerceIn(10, 40)) {
            val x = random.nextInt(3, width - 3)
            val y = random.nextInt(2, height - 6)
            val z = random.nextInt(1, depth - 1)
            
            val pos = origin.add(x, y, z)
            // Only place in solid areas (not tunnels)
            if (!tunnelPositions.contains(pos)) {
                world.setBlockState(pos, Blocks.SHROOMLIGHT.defaultState, 3) // Warm glow
            }
        }
        
        // Place lanterns at tunnel intersections for extra light
        for ((ex, ey, ez) in tunnelEndpoints) {
            if (random.nextFloat() < 0.4f) {
                val lanternPos = origin.add(ex, ey + 2, ez)
                if (tunnelPositions.contains(lanternPos)) {
                    world.setBlockState(lanternPos, Blocks.LANTERN.defaultState, 3)
                }
            }
        }
    }
    
    private fun addTraps() {
        val pitCount = (width / 18).coerceIn(3, 10)
        repeat(pitCount) { createPitTrap() }
        
        for (pos in trapPositions) {
            for (dy in 0..3) {
                world.setBlockState(pos.down(dy), TUNNEL_BLOCK.defaultState)
            }
            world.setBlockState(pos.down(3), MAGMA_BLOCK.defaultState)
            
            for (dx in -1..1) {
                for (dz in -1..1) {
                    if (random.nextFloat() < 0.5f) {
                        try {
                            world.setBlockState(pos.down(2).add(dx, 0, dz), 
                                SPIKE_BLOCK.defaultState.with(Properties.VERTICAL_DIRECTION, Direction.UP))
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    
    private fun createPitTrap() {
        for (attempt in 0 until 30) {
            val x = random.nextInt(6, width - 6)
            val y = random.nextInt(height / 3, height - 12)
            val z = random.nextInt(3, depth - 3)
            
            if (!tunnelPositions.contains(origin.add(x, y, z))) continue
            
            val pitDepth = random.nextInt(4, 7)
            
            for (dy in 0 until pitDepth) {
                for (dx in -1..1) {
                    world.setBlockState(origin.add(x + dx, y - dy, z), TUNNEL_BLOCK.defaultState)
                }
            }
            
            for (dx in -1..1) {
                world.setBlockState(origin.add(x + dx, y - pitDepth, z), 
                    if (random.nextBoolean()) MAGMA_BLOCK.defaultState else SOUL_SAND.defaultState)
            }
            break
        }
    }
    
    private fun addLootChests() {
        for (chestPos in chestPositions) {
            placeChestWithLoot(chestPos)
        }
        
        // WAY more random chests throughout tunnels!
        val extraChests = (width / 6).coerceIn(15, 40)
        repeat(extraChests) {
            for (i in 0 until 50) {
                val x = random.nextInt(5, width - 5)
                val y = random.nextInt(3, height - 7)
                val z = random.nextInt(2, depth - 2)
                
                val pos = origin.add(x, y, z)
                val belowPos = origin.add(x, y - 1, z)
                
                if (tunnelPositions.contains(pos) && !tunnelPositions.contains(belowPos)) {
                    placeChestWithLoot(pos)
                    break
                }
            }
        }
        
        // Also add chests near tunnel endpoints
        for ((ex, ey, ez) in tunnelEndpoints) {
            if (random.nextFloat() < 0.25f) { // 25% chance at each endpoint
                val pos = origin.add(ex, ey, ez)
                if (tunnelPositions.contains(pos)) {
                    placeChestWithLoot(pos)
                }
            }
        }
    }
    
    private fun placeChestWithLoot(pos: BlockPos) {
        world.setBlockState(pos, Blocks.CHEST.defaultState, 3)
        
        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity is ChestBlockEntity) {
            // Depth-based loot quality!
            val localY = pos.y - origin.y
            val depthLevel = getDepthLevel(localY)
            
            // More items in deeper chests
            val itemCount = when (depthLevel) {
                0 -> random.nextInt(2, 5)   // Top: 2-4 items
                1 -> random.nextInt(4, 7)   // Middle: 4-6 items
                else -> random.nextInt(6, 10) // Deep: 6-9 items (treasure!)
            }
            
            val usedSlots = mutableSetOf<Int>()
            
            repeat(itemCount) {
                var slot = random.nextInt(27)
                while (usedSlots.contains(slot)) slot = random.nextInt(27)
                usedSlots.add(slot)
                
                // Better loot the deeper you go
                val item = when (depthLevel) {
                    0 -> getShallowLoot()   // Basic loot at top
                    1 -> getMediumLoot()     // Better loot in middle
                    else -> getDeepLoot()    // Best loot at bottom!
                }
                
                val count = when {
                    item == Items.TORCH -> random.nextInt(4, 16)
                    item in listOf(Items.BREAD, Items.COOKED_BEEF, Items.APPLE) -> random.nextInt(2, 8)
                    item in listOf(Items.GOLD_INGOT, Items.IRON_INGOT) -> random.nextInt(1, 5)
                    item == Items.DIAMOND -> random.nextInt(1, 4)
                    item == Items.EMERALD -> random.nextInt(1, 5)
                    item == Items.GOLDEN_APPLE -> random.nextInt(1, 2)
                    item == Items.ENCHANTED_GOLDEN_APPLE -> 1
                    else -> 1
                }
                
                blockEntity.setStack(slot, ItemStack(item, count))
            }
        }
    }
    
    // Shallow loot - basic stuff
    private fun getShallowLoot() = getWeightedRandom(listOf(
        Items.BREAD to 25,
        Items.APPLE to 20,
        Items.TORCH to 20,
        Items.STONE_SWORD to 10,
        Items.STONE_PICKAXE to 10,
        Items.LEATHER_BOOTS to 8,
        Items.IRON_INGOT to 5,
        Items.COOKIE to 2
    ))
    
    // Medium depth loot - decent gear
    private fun getMediumLoot() = getWeightedRandom(listOf(
        Items.IRON_SWORD to 15,
        Items.IRON_PICKAXE to 12,
        Items.IRON_HELMET to 10,
        Items.IRON_CHESTPLATE to 8,
        Items.IRON_BOOTS to 10,
        Items.SHIELD to 10,
        Items.BOW to 8,
        Items.ARROW to 10,
        Items.COOKED_BEEF to 8,
        Items.GOLD_INGOT to 5,
        Items.GOLDEN_APPLE to 3,
        Items.DIAMOND to 1
    ))
    
    // Deep loot - the good stuff!
    private fun getDeepLoot() = getWeightedRandom(listOf(
        Items.DIAMOND_SWORD to 10,
        Items.DIAMOND_PICKAXE to 8,
        Items.DIAMOND_HELMET to 7,
        Items.DIAMOND_CHESTPLATE to 5,
        Items.DIAMOND_LEGGINGS to 6,
        Items.DIAMOND_BOOTS to 7,
        Items.DIAMOND to 15,
        Items.EMERALD to 10,
        Items.GOLDEN_APPLE to 8,
        Items.ENCHANTED_GOLDEN_APPLE to 2,
        Items.CROSSBOW to 6,
        Items.TRIDENT to 3,
        Items.TOTEM_OF_UNDYING to 2,
        Items.IRON_BLOCK to 5,
        Items.GOLD_BLOCK to 4,
        Items.NETHERITE_SCRAP to 2
    ))
    
    private fun spawnMonsters() {
        // Monster rooms - more monsters deeper down
        for ((cx, cy, cz) in monsterRoomCenters) {
            val depthLevel = getDepthLevel(cy)
            val count = when (depthLevel) {
                0 -> random.nextInt(2, 4)   // Top: 2-3 spiders
                1 -> random.nextInt(3, 6)   // Middle: 3-5 spiders
                else -> random.nextInt(5, 9) // Deep: 5-8 spiders!
            }
            repeat(count) {
                spawnMonsterAt(cx + random.nextInt(-2, 3), cy, cz + random.nextInt(-1, 2))
            }
        }
        
        // Random tunnel monsters (reduced)
        val randomMonsters = (width * height) / 500
        repeat(randomMonsters.coerceIn(3, 12)) {
            for (i in 0 until 30) {
                val x = random.nextInt(5, width - 5)
                val y = random.nextInt(4, height - 7)
                val z = random.nextInt(2, depth - 2)
                
                if (tunnelPositions.contains(origin.add(x, y, z))) {
                    spawnMonsterAt(x, y, z)
                    break
                }
            }
        }
    }
    
    private fun spawnMonsterAt(x: Int, y: Int, z: Int) {
        val pos = origin.add(x, y, z)
        val depthLevel = getDepthLevel(y)
        
        // Different/more dangerous monsters at depth
        val entityType = when {
            depthLevel == 0 -> EntityType.CAVE_SPIDER  // Top: just cave spiders
            depthLevel == 1 && random.nextFloat() < 0.3f -> EntityType.SPIDER  // Middle: some regular spiders
            depthLevel == 1 -> EntityType.CAVE_SPIDER
            depthLevel == 2 && random.nextFloat() < 0.2f -> EntityType.SKELETON  // Deep: skeletons!
            depthLevel == 2 && random.nextFloat() < 0.3f -> EntityType.SPIDER
            else -> EntityType.CAVE_SPIDER
        }
        
        val entity = entityType.create(world, null, pos, SpawnReason.COMMAND, false, false)
        if (entity != null) {
            entity.refreshPositionAndAngles(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, random.nextFloat() * 360f, 0f)
            world.spawnEntityAndPassengers(entity)
        }
    }
    
    private fun carveBlock(x: Int, y: Int, z: Int) {
        if (x in 2 until width - 2 && y in 1 until height - 1 && z in 1 until depth - 1) {
            val pos = origin.add(x, y, z)
            if (!tunnelPositions.contains(pos)) {
                tunnelPositions.add(pos)
                world.setBlockState(pos, TUNNEL_BLOCK.defaultState)
            }
        }
    }
    
    private fun carveSmallTunnel(x: Int, y: Int, z: Int, width: Int, depth: Int) {
        for (dx in 0 until width) {
            for (dz in 0 until depth) {
                carveBlock(x + dx, y, z + dz)
            }
        }
    }
    
    private fun setBlock(x: Int, y: Int, z: Int, block: Block) {
        // Use flags to ensure blocks are placed as normal breakable blocks
        world.setBlockState(origin.add(x, y, z), block.defaultState, 3)
    }
    
    // Get depth level (0 = top, 1 = middle, 2 = deep) for difficulty scaling
    private fun getDepthLevel(y: Int): Int {
        val relativeDepth = (height - 5 - y).toFloat() / (height - 6).toFloat()
        return when {
            relativeDepth < 0.33f -> 0  // Top third - easy
            relativeDepth < 0.66f -> 1  // Middle - medium
            else -> 2                    // Bottom - hard
        }
    }
}
