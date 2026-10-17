package com.example.sharedchest

import com.example.sharedchest.command.ChestCommand
import com.example.sharedchest.database.DatabaseManager
import com.example.sharedchest.listener.ChestListener
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SharedChestPlugin : JavaPlugin() {

    lateinit var databaseManager: DatabaseManager
        private set

    val openChests: MutableMap<UUID, Inventory> = ConcurrentHashMap()

    override fun onEnable() {
        saveDefaultConfig()

        databaseManager = DatabaseManager(this).also {
            it.initialize(config)
        }

        val chestCommand = ChestCommand(this, databaseManager)
        val bukkitCommand = object : Command("gchest") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                return chestCommand.onCommand(sender, this, commandLabel, args)
            }
        }
        server.commandMap.register(name, bukkitCommand)

        server.pluginManager.registerEvents(ChestListener(this, databaseManager), this)

        logger.info("SharedChest が有効化されました。")
    }

    override fun onDisable() {
        if (::databaseManager.isInitialized) {
            for (uuid in openChests.keys.toList()) {
                val inv = openChests.remove(uuid) ?: continue
                val contents = inv.contents.map { it?.clone() }.toTypedArray()
                val saved = databaseManager.saveChest(uuid, contents).join()
                if (!saved) {
                    logger.severe("シャットダウン時のチェスト保存に失敗しました (UUID: $uuid)。アンロックのみ試行します。")
                    databaseManager.unlock(uuid).join()
                }
            }
            databaseManager.shutdown()
        }
        logger.info("SharedChest が無効化されました。")
    }
}
