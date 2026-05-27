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
        12, 28, 29, 44, 64, 66, 67, 96, 98, 99,
        255, 256, 257, 258, 259, 260, 267, 268, 269, 270, 271, 272, 273,
        276, 280, 282, 283, 284, 288, 289, 290, 291, 292,
        298, 299, 302, 303, 304,
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

            val block = Block(props)
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
