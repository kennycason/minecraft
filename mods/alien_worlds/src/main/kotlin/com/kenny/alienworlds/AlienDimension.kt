package com.kenny.alienworlds

import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

object AlienDimension {
    val ALIEN_WORLD_KEY: ResourceKey<Level> = ResourceKey.create(
        Registries.DIMENSION,
        Identifier.parse("${AlienWorldsMod.MOD_ID}:alien_world")
    )
}
