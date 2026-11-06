package com.example.sharedchest.command

import com.example.sharedchest.ChestService
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/** /gchest */
class ChestCommand(private val chests: ChestService) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<out String>) {
        val sender = stack.sender
        if (sender is Player) {
            chests.open(sender)
        } else {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ使用できます。", NamedTextColor.RED))
        }
    }

    override fun permission(): String = "sharedchest.use"
}
