package com.kenny.alienworlds

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object AlienBlocks {

    val ALIEN_STONE = registerBlock("alien_stone",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .strength(2.0f, 6.0f)
            .sound(SoundType.STONE)
    )

    val ALIEN_SOIL = registerBlock("alien_soil",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .strength(0.6f)
            .sound(SoundType.GRAVEL)
    )

    val ALIEN_CRYSTAL = registerBlock("alien_crystal",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(1.5f, 6.0f)
            .sound(SoundType.AMETHYST)
            .lightLevel { 12 }
            .noOcclusion()
    )

    val ALIEN_SAND = registerBlock("alien_sand",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(0.5f)
            .sound(SoundType.SAND)
    )

    val ALIEN_MOSS = registerBlock("alien_moss",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GREEN)
            .strength(0.3f)
            .sound(SoundType.MOSS)
            .lightLevel { 5 }
    )

    val ALIEN_DEEPROCK = registerBlock("alien_deeprock",
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(4.0f, 10.0f)
            .sound(SoundType.DEEPSLATE)
    )

    private fun registerBlock(name: String, props: BlockBehaviour.Properties): Block {
        val id = Identifier.parse("${AlienWorldsMod.MOD_ID}:$name")
        val blockKey = ResourceKey.create(Registries.BLOCK, id)
        val itemKey = ResourceKey.create(Registries.ITEM, id)

        val block = Block(props.setId(blockKey))
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block)

        val itemProps = Item.Properties().setId(itemKey)
        Registry.register(BuiltInRegistries.ITEM, itemKey, BlockItem(block, itemProps))

        return block
    }

    fun register() {
        AlienWorldsMod.logger.info("Registered alien blocks")
    }
}
