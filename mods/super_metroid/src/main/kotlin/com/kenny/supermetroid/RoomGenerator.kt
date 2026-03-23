package com.kenny.supermetroid

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs

class RoomGenerator(
    private val world: ServerWorld,
    private val origin: BlockPos,
    private val roomName: String
) {
    companion object {
        const val TILE_SIZE = 16
        const val ROOM_DEPTH = 3
        // Threshold for detecting the "S" overlay in meta images.
        // Tiles where meta differs from the room image by more than this are solid.
        const val SOLID_THRESHOLD = 12
    }

    data class GenerationResult(
        val tilesWide: Int,
        val tilesTall: Int,
        val solidCount: Int,
        val airCount: Int
    )

    fun generate(): GenerationResult {
        val roomImage = loadImage("${roomName}.png")
        val metaImage = loadImage("${roomName}_meta.png")

        val tilesX = roomImage.width / TILE_SIZE
        val tilesY = roomImage.height / TILE_SIZE

        // Build solid grid by comparing room image with meta image
        val solidGrid = Array(tilesX) { x ->
            BooleanArray(tilesY) { y ->
                isTileSolid(roomImage, metaImage, x, y)
            }
        }

        // Build color grid from room image for block selection
        val colorGrid = Array(tilesX) { x ->
            Array(tilesY) { y ->
                getTileAverageColor(roomImage, x, y)
            }
        }

        var solidCount = 0
        var airCount = 0

        // Clear the area first
        for (x in 0 until tilesX) {
            for (y in 0 until tilesY) {
                val worldX = origin.x + x
                val worldY = origin.y + (tilesY - 1 - y) // flip Y: image top = highest Y
                for (z in 0..ROOM_DEPTH + 1) {
                    setBlock(BlockPos(worldX, worldY, origin.z + z), Blocks.AIR)
                }
            }
        }

        // Build the room
        for (x in 0 until tilesX) {
            for (y in 0 until tilesY) {
                val worldX = origin.x + x
                val worldY = origin.y + (tilesY - 1 - y)

                // Glass front wall (barrier behind for unbreakability)
                setBlock(BlockPos(worldX, worldY, origin.z), Blocks.GLASS)
                setBlock(BlockPos(worldX, worldY, origin.z - 1), Blocks.BARRIER)

                // Background wall
                val bgBlock = getBackgroundBlock(colorGrid[x][y])
                setBlock(BlockPos(worldX, worldY, origin.z + ROOM_DEPTH + 1), bgBlock)

                if (solidGrid[x][y]) {
                    val block = colorToBlock(colorGrid[x][y])
                    for (z in 1..ROOM_DEPTH) {
                        setBlock(BlockPos(worldX, worldY, origin.z + z), block)
                    }
                    solidCount++
                } else {
                    airCount++
                }
            }
        }

        // Add floor bedrock beneath the room
        for (x in 0 until tilesX) {
            val worldX = origin.x + x
            val worldY = origin.y - 1
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(worldX, worldY, origin.z + z), Blocks.BEDROCK)
            }
        }

        // Add ceiling bedrock above the room
        for (x in 0 until tilesX) {
            val worldX = origin.x + x
            val worldY = origin.y + tilesY
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(worldX, worldY, origin.z + z), Blocks.BEDROCK)
            }
        }

        // Add side walls
        for (y in -1..tilesY) {
            val worldY = origin.y + y
            for (z in 0..ROOM_DEPTH + 1) {
                setBlock(BlockPos(origin.x - 1, worldY, origin.z + z), Blocks.BEDROCK)
                setBlock(BlockPos(origin.x + tilesX, worldY, origin.z + z), Blocks.BEDROCK)
            }
        }

        return GenerationResult(tilesX, tilesY, solidCount, airCount)
    }

    private fun loadImage(fileName: String): BufferedImage {
        val path = "/assets/super_metroid/rooms/$fileName"
        val stream = javaClass.getResourceAsStream(path)
            ?: throw IllegalStateException("Room image not found: $path")
        return ImageIO.read(stream)
    }

    /**
     * Detect if a tile is solid by comparing the room image with the meta image.
     * The meta image has "S" text overlaid on solid tiles, creating a visible difference.
     */
    private fun isTileSolid(room: BufferedImage, meta: BufferedImage, tileX: Int, tileY: Int): Boolean {
        var totalDiff = 0L
        val startX = tileX * TILE_SIZE
        val startY = tileY * TILE_SIZE

        for (px in 0 until TILE_SIZE) {
            for (py in 0 until TILE_SIZE) {
                val x = startX + px
                val y = startY + py
                if (x >= room.width || y >= room.height) continue
                if (x >= meta.width || y >= meta.height) continue

                val roomRgb = room.getRGB(x, y)
                val metaRgb = meta.getRGB(x, y)

                val rDiff = abs(((roomRgb shr 16) and 0xFF) - ((metaRgb shr 16) and 0xFF))
                val gDiff = abs(((roomRgb shr 8) and 0xFF) - ((metaRgb shr 8) and 0xFF))
                val bDiff = abs((roomRgb and 0xFF) - (metaRgb and 0xFF))

                totalDiff += rDiff + gDiff + bDiff
            }
        }

        val avgDiff = totalDiff / (TILE_SIZE * TILE_SIZE * 3)
        return avgDiff > SOLID_THRESHOLD
    }

    /**
     * Get the average color of a tile from the room image.
     */
    private fun getTileAverageColor(image: BufferedImage, tileX: Int, tileY: Int): Color {
        var rTotal = 0L
        var gTotal = 0L
        var bTotal = 0L
        var count = 0

        val startX = tileX * TILE_SIZE
        val startY = tileY * TILE_SIZE

        for (px in 0 until TILE_SIZE) {
            for (py in 0 until TILE_SIZE) {
                val x = startX + px
                val y = startY + py
                if (x >= image.width || y >= image.height) continue

                val rgb = image.getRGB(x, y)
                rTotal += (rgb shr 16) and 0xFF
                gTotal += (rgb shr 8) and 0xFF
                bTotal += rgb and 0xFF
                count++
            }
        }

        if (count == 0) return Color(0, 0, 0)
        return Color(
            (rTotal / count).toInt(),
            (gTotal / count).toInt(),
            (bTotal / count).toInt()
        )
    }

    /**
     * Map tile average color to a Minecraft block for solid tiles.
     * Handles multiple Brinstar palettes based on dominant color channel.
     */
    private fun colorToBlock(color: Color): Block {
        val r = color.red
        val g = color.green
        val b = color.blue
        val brightness = (r + g + b) / 3

        // Very bright tiles = light stone
        if (brightness > 190) return Blocks.CALCITE
        if (brightness > 160) return Blocks.DIORITE

        // Detect palette from dominant channels
        val isGreenish = g > r + 15 && g > b + 15
        val isTealish = g > r + 10 && b > r + 10
        val isBlueish = b > r + 20 && b > g
        val isPurplish = r > g && b > g
        val isPinkish = r > g + 30 && r > b + 10

        // Green Brinstar (Crab Maze) - green/teal rocky terrain
        if (isGreenish && brightness > 100) return Blocks.MOSS_BLOCK
        if (isGreenish && brightness > 60) return Blocks.GREEN_TERRACOTTA
        if (isGreenish) return Blocks.DARK_PRISMARINE

        if (isTealish && brightness > 100) return Blocks.PRISMARINE
        if (isTealish && brightness > 60) return Blocks.DARK_PRISMARINE
        if (isTealish) return Blocks.CYAN_TERRACOTTA

        // Blue water-like tiles
        if (isBlueish && brightness > 100) return Blocks.PACKED_ICE
        if (isBlueish && brightness > 50) return Blocks.BLUE_ICE
        if (isBlueish) return Blocks.BLUE_TERRACOTTA

        // Purple/pink Brinstar (East Cactus Alley)
        if (isPinkish && brightness > 120) return Blocks.PURPUR_BLOCK
        if (isPinkish && brightness > 80) return Blocks.PURPUR_PILLAR
        if (isPurplish && brightness > 120) return Blocks.PURPUR_BLOCK
        if (isPurplish && brightness > 80) return Blocks.PURPLE_TERRACOTTA
        if (isPurplish && brightness > 50) return Blocks.PURPLE_CONCRETE

        // Dark tiles
        if (brightness > 60) return Blocks.DEEPSLATE_BRICKS
        if (brightness > 30) return Blocks.DEEPSLATE
        return Blocks.BLACK_CONCRETE
    }

    /**
     * Background block for non-solid (air) tiles — gives depth behind the playable space.
     */
    private fun getBackgroundBlock(color: Color): Block {
        val brightness = (color.red + color.green + color.blue) / 3

        // Dark atmospheric background
        if (brightness > 100) return Blocks.GRAY_TERRACOTTA
        if (brightness > 50) return Blocks.CYAN_TERRACOTTA
        return Blocks.BLACK_TERRACOTTA
    }

    private fun setBlock(pos: BlockPos, block: Block) {
        world.setBlockState(pos, block.defaultState, 2)
    }
}
