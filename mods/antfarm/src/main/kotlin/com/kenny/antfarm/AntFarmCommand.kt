package com.kenny.antfarm

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object AntFarmCommand {
    
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("antfarm")
                .then(
                    literal("create")
                        .then(
                            argument("width", IntegerArgumentType.integer(5, 200))
                                .then(
                                    argument("height", IntegerArgumentType.integer(5, 100))
                                        .then(
                                            argument("depth", IntegerArgumentType.integer(3, 10))
                                                .executes(::executeCreate)
                                        )
                                )
                        )
                )
                .then(
                    literal("demo")
                        .executes(::executeDemo)
                )
        )
    }
    
    private fun executeCreate(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val world = source.world
        val pos = player.blockPos
        
        val width = IntegerArgumentType.getInteger(context, "width")
        val height = IntegerArgumentType.getInteger(context, "height")
        val depth = IntegerArgumentType.getInteger(context, "depth")
        
        val totalBlocks = width * height * depth
        val maxBlocks = 150_000
        
        if (totalBlocks > maxBlocks) {
            source.sendFeedback({ 
                Text.literal("❌ Too many blocks! ${totalBlocks} > ${maxBlocks} limit. Try smaller dimensions.") 
            }, false)
            return 0
        }
        
        source.sendFeedback({ Text.literal("🐜 Generating ant farm ${width}x${height}x${depth} (${totalBlocks} blocks)...") }, true)
        
        val generator = AntFarmGenerator(world, pos, width, height, depth)
        val tunnelCount = generator.generate()
        
        source.sendFeedback({ 
            Text.literal("✅ Ant farm created with $tunnelCount tunnels!") 
        }, true)
        
        return 1
    }
    
    private fun executeDemo(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val world = source.world
        val pos = player.blockPos
        
        source.sendFeedback({ Text.literal("🐜 Generating demo ant farm (30x20x5)...") }, true)
        
        val generator = AntFarmGenerator(world, pos, 30, 20, 5)
        val tunnelCount = generator.generate()
        
        source.sendFeedback({ 
            Text.literal("✅ Demo ant farm created with $tunnelCount tunnels!") 
        }, true)
        
        return 1
    }
}

