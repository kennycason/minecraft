package com.kenny.supermetroid

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty

class SmTileBlock(properties: BlockBehaviour.Properties) : Block(properties) {
    companion object {
        val HFLIP: BooleanProperty = BooleanProperty.create("hflip")
        val VFLIP: BooleanProperty = BooleanProperty.create("vflip")
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(HFLIP, false)
                .setValue(VFLIP, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HFLIP, VFLIP)
    }
}
