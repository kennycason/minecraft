package com.kenny.blockmoeba

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.Block

enum class AmoebaColor(
    val displayName: String,
    private val bodyBlockId: String,
    private val coreBlockId: String,
    val growSpeed: Double,
    val moveSpeed: Double,
    val branchiness: Double
) {
    PINK(
        "Pink",
        "minecraft:pink_stained_glass",
        "minecraft:pink_concrete",
        growSpeed = 1.4,
        moveSpeed = 1.3,
        branchiness = 0.3
    ),
    BLUE(
        "Blue",
        "minecraft:blue_stained_glass",
        "minecraft:blue_concrete",
        growSpeed = 0.7,
        moveSpeed = 0.6,
        branchiness = 0.2
    ),
    GREEN(
        "Green",
        "minecraft:lime_stained_glass",
        "minecraft:lime_concrete",
        growSpeed = 1.0,
        moveSpeed = 1.0,
        branchiness = 0.6
    ),
    PURPLE(
        "Purple",
        "minecraft:purple_stained_glass",
        "minecraft:purple_concrete",
        growSpeed = 1.1,
        moveSpeed = 0.9,
        branchiness = 0.5
    ),
    ORANGE(
        "Orange",
        "minecraft:orange_stained_glass",
        "minecraft:orange_concrete",
        growSpeed = 1.5,
        moveSpeed = 1.1,
        branchiness = 0.4
    ),
    CYAN(
        "Cyan",
        "minecraft:cyan_stained_glass",
        "minecraft:cyan_concrete",
        growSpeed = 0.9,
        moveSpeed = 1.4,
        branchiness = 0.35
    ),
    RED(
        "Red",
        "minecraft:red_stained_glass",
        "minecraft:red_concrete",
        growSpeed = 1.6,
        moveSpeed = 0.8,
        branchiness = 0.7
    ),
    YELLOW(
        "Yellow",
        "minecraft:yellow_stained_glass",
        "minecraft:yellow_concrete",
        growSpeed = 1.2,
        moveSpeed = 1.2,
        branchiness = 0.45
    ),
    CAMO(
        "Camo",
        "minecraft:stone",
        "minecraft:mossy_cobblestone",
        growSpeed = 1.0,
        moveSpeed = 1.0,
        branchiness = 0.4
    );

    val bodyBlock: Block get() = blockFor(bodyBlockId)
    val coreBlock: Block get() = blockFor(coreBlockId)
    val isCamo: Boolean get() = this == CAMO

    companion object {
        fun fromName(name: String): AmoebaColor? =
            entries.find { it.name.equals(name, ignoreCase = true) }

        fun random(random: java.util.Random): AmoebaColor {
            val options = entries.filter { it != CAMO }
            return options[random.nextInt(options.size)]
        }

        fun blockFor(id: String): Block =
            BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))
    }
}
