package com.kenny.supermetroid

import com.google.gson.JsonParser
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStreamReader
import java.util.Base64
import javax.imageio.ImageIO

class TileRoomGenerator(
    private val world: ServerLevel,
    private val origin: BlockPos,
    private val roomId: String
) {
    companion object {
        const val ROOM_DEPTH = 3
        const val CRE_TILE_COUNT = 384
        const val TILE_PX = 8
        const val TILES_PER_ROW = 16
        const val SCREEN_TILES = 16
    }

    data class GenerationResult(
        val tilesWide: Int,
        val tilesTall: Int,
        val solidCount: Int,
        val airCount: Int
    )

    data class TileEntry(
        val tileNum: Int,
        val palette: Int,
        val hFlip: Boolean,
        val vFlip: Boolean
    )

    fun generate(): GenerationResult {
        val json = loadJson(roomId)
        val root = JsonParser.parseReader(json).asJsonObject
        json.close()

        val widthScreens = root.get("width").asInt
        val heightScreens = root.get("height").asInt
        val tilesetId = root.get("tileset").asInt

        val levelDataB64 = root.get("levelDataBase64").asString
        val tilesX = widthScreens * SCREEN_TILES
        val tilesY = heightScreens * SCREEN_TILES

        val levelData = Base64.getDecoder().decode(levelDataB64)

        // First 2 bytes = layer 1 size
        val layer1Size = (levelData[0].toInt() and 0xFF) or ((levelData[1].toInt() and 0xFF) shl 8)
        val layer1Tiles = parseTiles(levelData, 2, tilesX * tilesY)

        // Layer 2 starts after layer 1 header + data
        val layer2Offset = 2 + layer1Size
        val hasLayer2 = layer2Offset + 2 < levelData.size &&
                ((levelData[layer2Offset].toInt() and 0xFF) or ((levelData[layer2Offset + 1].toInt() and 0xFF) shl 8)) > 0
        val layer2Tiles = if (hasLayer2) {
            parseTiles(levelData, layer2Offset + 2, tilesX * tilesY)
        } else null

        val creImage = loadImage("/assets/super_metroid/tilesets/cre_tiles.png")
        val tilesetImage = loadImage("/assets/super_metroid/tilesets/tileset_${tilesetId}_ure.png")

        var solidCount = 0
        var airCount = 0

        // Clear area
        for (x in 0 until tilesX) {
            for (y in 0 until tilesY) {
                val worldX = origin.x + x
                val worldY = origin.y + (tilesY - 1 - y)
                for (z in 0..ROOM_DEPTH + 1) {
                    setBlock(BlockPos(worldX, worldY, origin.z + z), Blocks.AIR)
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
                setBlock(BlockPos(worldX, worldY, origin.z), Blocks.GLASS)
                setBlock(BlockPos(worldX, worldY, origin.z - 1), Blocks.BARRIER)

                val isSolid = isTileSolid(tile, creImage, tilesetImage)

                // Background wall
                val bgTile = if (layer2Tiles != null) layer2Tiles[idx] else tile
                val bgColor = getTileAverageColor(bgTile, creImage, tilesetImage)
                if (bgColor != null) {
                    setBlock(BlockPos(worldX, worldY, origin.z + ROOM_DEPTH + 1),
                        getBackgroundBlock(bgColor))
                } else {
                    setBlock(BlockPos(worldX, worldY, origin.z + ROOM_DEPTH + 1),
                        Blocks.BLACK_TERRACOTTA)
                }

                if (isSolid) {
                    val color = getTileAverageColor(tile, creImage, tilesetImage)
                    val block = if (color != null) colorToBlock(color) else Blocks.DEEPSLATE
                    for (z in 1..ROOM_DEPTH) {
                        setBlock(BlockPos(worldX, worldY, origin.z + z), block)
                    }
                    solidCount++
                } else {
                    airCount++
                }
            }
        }

        // Bedrock frame
        for (x in 0 until tilesX) {
            val worldX = origin.x + x
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(worldX, origin.y - 1, origin.z + z), Blocks.BEDROCK)
                setBlock(BlockPos(worldX, origin.y + tilesY, origin.z + z), Blocks.BEDROCK)
            }
        }
        for (y in -1..tilesY) {
            val worldY = origin.y + y
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(origin.x - 1, worldY, origin.z + z), Blocks.BEDROCK)
                setBlock(BlockPos(origin.x + tilesX, worldY, origin.z + z), Blocks.BEDROCK)
            }
        }

        return GenerationResult(tilesX, tilesY, solidCount, airCount)
    }

    private fun parseTiles(data: ByteArray, offset: Int, count: Int): List<TileEntry> {
        val tiles = ArrayList<TileEntry>(count)
        for (i in 0 until count) {
            val off = offset + i * 2
            if (off + 1 >= data.size) {
                tiles.add(TileEntry(1023, 0, false, false))
                continue
            }
            val word = (data[off].toInt() and 0xFF) or ((data[off + 1].toInt() and 0xFF) shl 8)
            tiles.add(TileEntry(
                tileNum = word and 0x3FF,
                palette = (word shr 10) and 0x07,
                hFlip = (word shr 14) and 1 == 1,
                vFlip = (word shr 15) and 1 == 1
            ))
        }
        return tiles
    }

    private fun isTileSolid(tile: TileEntry, cre: BufferedImage, tileset: BufferedImage): Boolean {
        if (tile.tileNum == 0 || tile.tileNum == 0xFF) return false
        val img = getTileImage(tile.tileNum, cre, tileset) ?: return false
        val tx = getTilePixelX(tile.tileNum)
        val ty = getTilePixelY(tile.tileNum)

        var opaqueCount = 0
        for (py in 0 until TILE_PX) {
            for (px in 0 until TILE_PX) {
                val x = tx + px
                val y = ty + py
                if (x >= img.width || y >= img.height) continue
                val argb = img.getRGB(x, y)
                val a = (argb shr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                if (a > 128 && (r + g + b) > 15) {
                    opaqueCount++
                }
            }
        }
        return opaqueCount > (TILE_PX * TILE_PX) / 4
    }

    private fun getTileAverageColor(tile: TileEntry, cre: BufferedImage, tileset: BufferedImage): Color? {
        val img = getTileImage(tile.tileNum, cre, tileset) ?: return null
        val tx = getTilePixelX(tile.tileNum)
        val ty = getTilePixelY(tile.tileNum)

        var rTotal = 0L
        var gTotal = 0L
        var bTotal = 0L
        var count = 0

        for (py in 0 until TILE_PX) {
            for (px in 0 until TILE_PX) {
                val x = tx + px
                val y = ty + py
                if (x >= img.width || y >= img.height) continue
                val argb = img.getRGB(x, y)
                val a = (argb shr 24) and 0xFF
                if (a < 128) continue
                rTotal += (argb shr 16) and 0xFF
                gTotal += (argb shr 8) and 0xFF
                bTotal += argb and 0xFF
                count++
            }
        }

        if (count == 0) return null
        return Color(
            (rTotal / count).toInt(),
            (gTotal / count).toInt(),
            (bTotal / count).toInt()
        )
    }

    private fun getTileImage(tileNum: Int, cre: BufferedImage, tileset: BufferedImage): BufferedImage? {
        return if (tileNum < CRE_TILE_COUNT) cre else tileset
    }

    private fun getTilePixelX(tileNum: Int): Int {
        val localIdx = if (tileNum < CRE_TILE_COUNT) tileNum else tileNum - CRE_TILE_COUNT
        return (localIdx % TILES_PER_ROW) * TILE_PX
    }

    private fun getTilePixelY(tileNum: Int): Int {
        val localIdx = if (tileNum < CRE_TILE_COUNT) tileNum else tileNum - CRE_TILE_COUNT
        return (localIdx / TILES_PER_ROW) * TILE_PX
    }

    private fun colorToBlock(color: Color): Block {
        val r = color.red
        val g = color.green
        val b = color.blue
        val brightness = (r + g + b) / 3

        if (brightness > 190) return Blocks.CALCITE
        if (brightness > 160) return Blocks.DIORITE

        val isGreenish = g > r + 15 && g > b + 15
        val isTealish = g > r + 10 && b > r + 10
        val isBlueish = b > r + 20 && b > g
        val isPurplish = r > g && b > g
        val isPinkish = r > g + 30 && r > b + 10

        if (isGreenish && brightness > 100) return Blocks.MOSS_BLOCK
        if (isGreenish && brightness > 60) return Blocks.GREEN_TERRACOTTA
        if (isGreenish) return Blocks.DARK_PRISMARINE

        if (isTealish && brightness > 100) return Blocks.PRISMARINE
        if (isTealish && brightness > 60) return Blocks.DARK_PRISMARINE
        if (isTealish) return Blocks.CYAN_TERRACOTTA

        if (isBlueish && brightness > 100) return Blocks.PACKED_ICE
        if (isBlueish && brightness > 50) return Blocks.BLUE_ICE
        if (isBlueish) return Blocks.BLUE_TERRACOTTA

        if (isPinkish && brightness > 120) return Blocks.PURPUR_BLOCK
        if (isPinkish && brightness > 80) return Blocks.PURPUR_PILLAR
        if (isPurplish && brightness > 120) return Blocks.PURPUR_BLOCK
        if (isPurplish && brightness > 80) return Blocks.PURPLE_TERRACOTTA
        if (isPurplish && brightness > 50) return Blocks.PURPLE_CONCRETE

        if (brightness > 60) return Blocks.DEEPSLATE_BRICKS
        if (brightness > 30) return Blocks.DEEPSLATE
        return Blocks.BLACK_CONCRETE
    }

    private fun getBackgroundBlock(color: Color): Block {
        val brightness = (color.red + color.green + color.blue) / 3
        if (brightness > 100) return Blocks.GRAY_TERRACOTTA
        if (brightness > 50) return Blocks.CYAN_TERRACOTTA
        return Blocks.BLACK_TERRACOTTA
    }

    private fun loadJson(roomId: String): InputStreamReader {
        val path = "/assets/super_metroid/roomdata/"
        val knownFiles = listOf(
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

    private fun loadImage(path: String): BufferedImage {
        val stream = javaClass.getResourceAsStream(path)
            ?: throw IllegalStateException("Image not found: $path")
        return ImageIO.read(stream)
    }

    private fun setBlock(pos: BlockPos, block: Block) {
        world.setBlock(pos, block.defaultBlockState(), 2)
    }
}
