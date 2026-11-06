package com.example.sharedchest.command

import com.example.sharedchest.ChestService
import com.example.sharedchest.SharedChestPlugin
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

/** /gchest と /gchest reload */
class ChestCommand(private val plugin: SharedChestPlugin, private val chests: ChestService) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<out String>) {
        val sender = stack.sender
        if (args.firstOrNull().equals("reload", ignoreCase = true) && sender.hasPermission(PERM_RELOAD)) {
            plugin.reloadConfig()
            plugin.messages.reload()
            plugin.messages.send(sender, "reloaded")
            return
        }
        if (sender is Player) {
            chests.open(sender)
        } else {
            plugin.messages.send(sender, "player-only")
        }
    }

    override fun suggest(stack: CommandSourceStack, args: Array<out String>): Collection<String> {
        if (args.size > 1 || !stack.sender.hasPermission(PERM_RELOAD)) return emptyList()
        val prefix = args.firstOrNull()?.lowercase().orEmpty()
        return listOf("reload").filter { it.startsWith(prefix) }
    }

    override fun permission(): String = "sharedchest.use"

    private companion object {
        const val PERM_RELOAD = "sharedchest.reload"
    }
}
