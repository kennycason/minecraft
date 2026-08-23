package com.kenny.alienworlds

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.FixedBiomeSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.blending.Blender
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.floor

class AlienChunkGenerator(
    biomeSource: FixedBiomeSource
) : ChunkGenerator(biomeSource) {

    companion object {
        val CODEC: MapCodec<AlienChunkGenerator> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                FixedBiomeSource.CODEC.fieldOf("biome_source").forGetter { it.biomeSource as FixedBiomeSource }
            ).apply(instance, ::AlienChunkGenerator)
        }

        private const val SEA_LEVEL = 40
        private const val BASE_HEIGHT = 64
        private const val MIN_Y = -64
        private const val WORLD_HEIGHT = 384

        fun register() {
            Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.parse("${AlienWorldsMod.MOD_ID}:alien_chunk_generator"),
                CODEC
            )
            AlienWorldsMod.logger.info("Registered alien chunk generator")
        }

        private fun noise2D(x: Double, z: Double, seed: Long): Double {
            val hash = seed xor (floor(x).toLong() * 73856093L) xor (floor(z).toLong() * 19349663L)
            val h = hash * hash * hash * 60493
            return (h shr 13).toDouble() / Long.MAX_VALUE.toDouble()
        }

        private fun smoothNoise(x: Double, z: Double, scale: Double, seed: Long): Double {
            val sx = x / scale
            val sz = z / scale
            val ix = floor(sx).toInt()
            val iz = floor(sz).toInt()
            val fx = sx - ix
            val fz = sz - iz

            val smoothX = fx * fx * (3.0 - 2.0 * fx)
            val smoothZ = fz * fz * (3.0 - 2.0 * fz)

            val n00 = noise2D(ix.toDouble(), iz.toDouble(), seed)
            val n10 = noise2D((ix + 1).toDouble(), iz.toDouble(), seed)
            val n01 = noise2D(ix.toDouble(), (iz + 1).toDouble(), seed)
            val n11 = noise2D((ix + 1).toDouble(), (iz + 1).toDouble(), seed)

            val nx0 = n00 + smoothX * (n10 - n00)
            val nx1 = n01 + smoothX * (n11 - n01)
            return nx0 + smoothZ * (nx1 - nx0)
        }

        fun getTerrainHeight(x: Int, z: Int, seed: Long = 42L): Int {
            val continent = smoothNoise(x.toDouble(), z.toDouble(), 256.0, seed) * 30.0
            val hills = smoothNoise(x.toDouble(), z.toDouble(), 64.0, seed + 1) * 15.0
            val detail = smoothNoise(x.toDouble(), z.toDouble(), 16.0, seed + 2) * 5.0
            val ridges = (1.0 - abs(smoothNoise(x.toDouble(), z.toDouble(), 128.0, seed + 3))) * 20.0
            return (BASE_HEIGHT + continent + hills + detail + ridges).toInt()
        }
    }

    private val worldSeed = 42L

    override fun codec(): MapCodec<out ChunkGenerator> = CODEC

    override fun fillFromNoise(
        blender: Blender,
        randomState: RandomState,
        structureManager: StructureManager,
        chunk: ChunkAccess
    ): CompletableFuture<ChunkAccess> {
        val startX = chunk.pos.minBlockX
        val startZ = chunk.pos.minBlockZ
        val minY = chunk.minY
        val maxY = minY + chunk.height

        for (localX in 0 until 16) {
            for (localZ in 0 until 16) {
                val worldX = startX + localX
                val worldZ = startZ + localZ
                val terrainHeight = getTerrainHeight(worldX, worldZ, worldSeed)

                for (y in minY until maxY) {
                    val pos = BlockPos(worldX, y, worldZ)
                    val state: BlockState = when {
                        y == minY -> Blocks.BEDROCK.defaultBlockState()

                        y < terrainHeight - 12 -> AlienBlocks.ALIEN_DEEPROCK.defaultBlockState()

                        y < terrainHeight - 1 -> AlienBlocks.ALIEN_STONE.defaultBlockState()

                        y == terrainHeight - 1 -> AlienBlocks.ALIEN_SOIL.defaultBlockState()

                        // Crystal spires
                        y in terrainHeight until (terrainHeight + 20) && isCrystalSpire(worldX, worldZ) ->
                            AlienBlocks.ALIEN_CRYSTAL.defaultBlockState()

                        // Floating islands
                        isFloatingIsland(worldX, y, worldZ) ->
                            if (y == getFloatingIslandTop(worldX, worldZ))
                                AlienBlocks.ALIEN_SOIL.defaultBlockState()
                            else
                                AlienBlocks.ALIEN_STONE.defaultBlockState()

                        // Alien sea
                        y < SEA_LEVEL && y >= terrainHeight -> Blocks.WATER.defaultBlockState()

                        else -> continue
                    }
                    chunk.setBlockState(pos, state)
                }
            }
        }
        return CompletableFuture.completedFuture(chunk)
    }

    override fun buildSurface(
        region: WorldGenRegion,
        structureManager: StructureManager,
        randomState: RandomState,
        chunk: ChunkAccess
    ) {
        val startX = chunk.pos.minBlockX
        val startZ = chunk.pos.minBlockZ

        for (localX in 0 until 16) {
            for (localZ in 0 until 16) {
                val worldX = startX + localX
                val worldZ = startZ + localZ
                val terrainHeight = getTerrainHeight(worldX, worldZ, worldSeed)

                // Moss on surface
                val mossNoise = smoothNoise(worldX.toDouble(), worldZ.toDouble(), 8.0, worldSeed + 300)
                if (mossNoise > 0.3 && terrainHeight > SEA_LEVEL) {
                    chunk.setBlockState(BlockPos(worldX, terrainHeight, worldZ), AlienBlocks.ALIEN_MOSS.defaultBlockState())
                }

                // Desert patches
                val desertNoise = smoothNoise(worldX.toDouble(), worldZ.toDouble(), 200.0, worldSeed + 400)
                if (desertNoise > 0.5 && terrainHeight > SEA_LEVEL) {
                    chunk.setBlockState(BlockPos(worldX, terrainHeight - 1, worldZ), AlienBlocks.ALIEN_SAND.defaultBlockState())
                    chunk.setBlockState(BlockPos(worldX, terrainHeight - 2, worldZ), AlienBlocks.ALIEN_SAND.defaultBlockState())
                }
            }
        }
    }

    override fun applyCarvers(
        region: WorldGenRegion,
        seed: Long,
        randomState: RandomState,
        biomeManager: BiomeManager,
        structureManager: StructureManager,
        chunk: ChunkAccess
    ) {
        // No carving for now - could add alien cave networks later
    }

    override fun spawnOriginalMobs(region: WorldGenRegion) {}

    override fun getGenDepth(): Int = WORLD_HEIGHT

    override fun getSeaLevel(): Int = SEA_LEVEL

    override fun getMinY(): Int = MIN_Y

    override fun getBaseHeight(
        x: Int,
        z: Int,
        types: Heightmap.Types,
        levelHeightAccessor: LevelHeightAccessor,
        randomState: RandomState
    ): Int {
        return getTerrainHeight(x, z, worldSeed)
    }

    override fun getBaseColumn(
        x: Int,
        z: Int,
        levelHeightAccessor: LevelHeightAccessor,
        randomState: RandomState
    ): NoiseColumn {
        val terrainHeight = getTerrainHeight(x, z, worldSeed)
        val states = Array(WORLD_HEIGHT) { i ->
            val y = MIN_Y + i
            when {
                y == MIN_Y -> Blocks.BEDROCK.defaultBlockState()
                y < terrainHeight - 12 -> AlienBlocks.ALIEN_DEEPROCK.defaultBlockState()
                y < terrainHeight -> AlienBlocks.ALIEN_STONE.defaultBlockState()
                else -> Blocks.AIR.defaultBlockState()
            }
        }
        return NoiseColumn(MIN_Y, states)
    }

    override fun addDebugScreenInfo(info: MutableList<String>, randomState: RandomState, pos: BlockPos) {
        info.add("Alien Worlds Generator")
    }

    private fun isCrystalSpire(x: Int, z: Int): Boolean {
        return smoothNoise(x.toDouble(), z.toDouble(), 32.0, worldSeed + 200) > 0.85
    }

    private fun isFloatingIsland(x: Int, y: Int, z: Int): Boolean {
        if (y < 120 || y > 200) return false
        val islandNoise = smoothNoise(x.toDouble(), z.toDouble(), 80.0, worldSeed + 100)
        if (islandNoise < 0.6) return false
        val islandCenter = 150.0 + smoothNoise(x.toDouble(), z.toDouble(), 40.0, worldSeed + 101) * 20.0
        val dist = abs(y - islandCenter)
        val thickness = 4.0 + smoothNoise(x.toDouble(), z.toDouble(), 20.0, worldSeed + 102) * 3.0
        return dist < thickness
    }

    private fun getFloatingIslandTop(x: Int, z: Int): Int {
        val islandCenter = 150.0 + smoothNoise(x.toDouble(), z.toDouble(), 40.0, worldSeed + 101) * 20.0
        val thickness = 4.0 + smoothNoise(x.toDouble(), z.toDouble(), 20.0, worldSeed + 102) * 3.0
        return (islandCenter + thickness).toInt() - 1
    }
}
