package com.kenny.supermetroid

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import org.slf4j.LoggerFactory

object SmTileBlocks {
    private val logger = LoggerFactory.getLogger("super_metroid")
    private const val MAX_TILES = 1024
    private val tileBlocks = arrayOfNulls<Block>(MAX_TILES)

    private val KNOWN_TILES = intArrayOf(
        12, 28, 29, 44, 64, 66, 67, 69, 70, 71, 72, 73, 86, 88, 89, 91, 95,
        96, 98, 99, 100, 101, 102, 114, 122, 123, 125, 126, 127,
        155, 156, 157, 158, 163, 164, 182, 183, 188, 189, 190, 194, 195, 196,
        255, 256, 257, 258, 259, 260, 266, 267, 268, 269, 270, 271, 272, 273, 274,
        276, 278, 279, 280, 281, 282, 283, 284, 288, 289, 290, 291, 292,
        297, 298, 299, 300, 301, 302, 303, 304, 305, 306, 308, 310, 311, 312, 313, 314,
        372, 375,
        696, 697, 728, 782, 783, 784, 814, 815, 822, 823, 824, 825, 1023
    )

    fun register() {
        for (i in KNOWN_TILES) {
            val id = Identifier.parse("super_metroid:sm_tile_$i")
            val blockKey = ResourceKey.create(Registries.BLOCK, id)
            val itemKey = ResourceKey.create(Registries.ITEM, id)

            val props = BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .setId(blockKey)

            val block = SmTileBlock(props)
            Registry.register(BuiltInRegistries.BLOCK, blockKey, block)

            val itemProps = Item.Properties().setId(itemKey)
            Registry.register(BuiltInRegistries.ITEM, itemKey, BlockItem(block, itemProps))

            tileBlocks[i] = block
        }
        logger.info("Registered ${KNOWN_TILES.size} SM tile blocks")
    }

    fun getBlock(tileNum: Int): Block? {
        if (tileNum < 0 || tileNum >= MAX_TILES) return null
        return tileBlocks[tileNum]
    }

    fun hasBlock(tileNum: Int): Boolean {
        return tileNum in 0 until MAX_TILES && tileBlocks[tileNum] != null
    }
}
