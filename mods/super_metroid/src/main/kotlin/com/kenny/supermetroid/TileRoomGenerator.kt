package com.kenny.supermetroid

import com.google.gson.JsonParser
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import java.io.InputStreamReader
import java.util.Base64

class TileRoomGenerator(
    private val world: ServerLevel,
    private val origin: BlockPos,
    private val roomId: String,
    private val showGlass: Boolean = false
) {
    companion object {
        const val ROOM_DEPTH = 5
        const val SCREEN_TILES = 16
    }

    data class GenerationResult(
        val tilesWide: Int,
        val tilesTall: Int,
        val solidCount: Int,
        val airCount: Int
    )

    data class TileEntry(
        val metatileIdx: Int,    // bits 0-9: metatile index (0-1023)
        val hFlip: Boolean,      // bit 10
        val vFlip: Boolean,      // bit 11
        val collision: Int       // bits 12-15: block type (0=air, 8=solid, 9=door, etc.)
    ) {
        val isSolid: Boolean get() = collision >= 8
        val isEmpty: Boolean get() = collision == 0 && metatileIdx == 0
    }

    fun generate(): GenerationResult {
        val json = loadJson(roomId)
        val root = JsonParser.parseReader(json).asJsonObject
        json.close()

        val widthScreens = root.get("width").asInt
        val heightScreens = root.get("height").asInt

        val levelDataB64 = root.get("levelDataBase64").asString
        val tilesX = widthScreens * SCREEN_TILES
        val tilesY = heightScreens * SCREEN_TILES

        val levelData = Base64.getDecoder().decode(levelDataB64)

        val layer1Size = (levelData[0].toInt() and 0xFF) or ((levelData[1].toInt() and 0xFF) shl 8)
        val layer1Tiles = parseTiles(levelData, 2, tilesX * tilesY)

        // BTS (Block Type Subdata) sits between L1 and L2: 1 byte per tile
        val btsOffset = 2 + layer1Size
        val layer2Offset = btsOffset + tilesX * tilesY

        val hasLayer2 = layer2Offset + 2 < levelData.size &&
                ((levelData[layer2Offset].toInt() and 0xFF) or ((levelData[layer2Offset + 1].toInt() and 0xFF) shl 8)) > 0
        val layer2Tiles = if (hasLayer2) {
            parseTiles(levelData, layer2Offset, tilesX * tilesY)
        } else null

        var solidCount = 0
        var airCount = 0

        // Clear area
        val airState = Blocks.AIR.defaultBlockState()
        for (x in 0 until tilesX) {
            for (y in 0 until tilesY) {
                val worldX = origin.x + x
                val worldY = origin.y + (tilesY - 1 - y)
                for (z in 0..ROOM_DEPTH + 1) {
                    setBlock(BlockPos(worldX, worldY, origin.z + z), airState)
                }
            }
        }

        // Build the room
        for (y in 0 until tilesY) {
            for (x in 0 until tilesX) {
                val idx = y * tilesX + x
                val tile = layer1Tiles[idx]
                val worldX = origin.x + (tilesX - 1 - x)
                val worldY = origin.y + (tilesY - 1 - y)

                // Glass front + barrier
                if (showGlass) {
                    setBlock(BlockPos(worldX, worldY, origin.z), Blocks.GLASS.defaultBlockState())
                    setBlock(BlockPos(worldX, worldY, origin.z - 1), Blocks.BARRIER.defaultBlockState())
                }

                // Background wall
                val bgTile = if (layer2Tiles != null) layer2Tiles[idx] else tile
                val bgBlock = SmTileBlocks.getBlock(bgTile.metatileIdx)
                if (bgBlock != null && !bgTile.isEmpty) {
                    setBlock(BlockPos(worldX, worldY, origin.z + ROOM_DEPTH + 1),
                        tileState(bgBlock, bgTile))
                } else {
                    setBlock(BlockPos(worldX, worldY, origin.z + ROOM_DEPTH + 1), Blocks.BLACK_CONCRETE.defaultBlockState())
                }

                // Foreground solid tiles
                if (tile.isSolid) {
                    val block = SmTileBlocks.getBlock(tile.metatileIdx)
                    val state = if (block != null) tileState(block, tile) else Blocks.STONE.defaultBlockState()
                    for (z in 1..ROOM_DEPTH) {
                        setBlock(BlockPos(worldX, worldY, origin.z + z), state)
                    }
                    solidCount++
                } else {
                    airCount++
                }
            }
        }

        // Bedrock frame
        val bedrockState = Blocks.BEDROCK.defaultBlockState()
        for (x in 0 until tilesX) {
            val worldX = origin.x + x
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(worldX, origin.y - 1, origin.z + z), bedrockState)
                setBlock(BlockPos(worldX, origin.y + tilesY, origin.z + z), bedrockState)
            }
        }
        for (y in -1..tilesY) {
            val worldY = origin.y + y
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(origin.x - 1, worldY, origin.z + z), bedrockState)
                setBlock(BlockPos(origin.x + tilesX, worldY, origin.z + z), bedrockState)
            }
        }

        return GenerationResult(tilesX, tilesY, solidCount, airCount)
    }

    private fun parseTiles(data: ByteArray, offset: Int, count: Int): List<TileEntry> {
        val tiles = ArrayList<TileEntry>(count)
        for (i in 0 until count) {
            val off = offset + i * 2
            if (off + 1 >= data.size) {
                tiles.add(TileEntry(1023, false, false, 0))
                continue
            }
            val word = (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)
            tiles.add(TileEntry(
                metatileIdx = word and 0x3FF,
                hFlip = (word shr 10) and 1 == 1,
                vFlip = (word shr 11) and 1 == 1,
                collision = (word shr 12) and 0x0F
            ))
        }
        return tiles
    }

    private fun loadJson(roomId: String): InputStreamReader {
        val path = "/assets/super_metroid/roomdata/"
        val knownFiles = listOf(
            "Landing_Site_91F8.json",
            "Parlor_and_Alcatraz_92FD.json"
        )
        for (file in knownFiles) {
            if (file.endsWith("_${roomId}.json")) {
                val stream = javaClass.getResourceAsStream("$path$file")
                if (stream != null) return InputStreamReader(stream)
            }
        }
        val stream = javaClass.getResourceAsStream("$path${roomId}.json")
            ?: throw IllegalStateException("Room data not found for ID: $roomId. Add the JSON file to roomdata/")
        return InputStreamReader(stream)
    }

    private fun tileState(block: Block, tile: TileEntry): BlockState {
        val state = block.defaultBlockState()
        if (block is SmTileBlock) {
            return state
                .setValue(SmTileBlock.HFLIP, tile.hFlip)
                .setValue(SmTileBlock.VFLIP, tile.vFlip)
        }
        return state
    }

    private fun setBlock(pos: BlockPos, state: BlockState) {
        world.setBlock(pos, state, 2)
    }
}
