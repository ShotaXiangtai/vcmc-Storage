package com.example.sharedchest.command

import com.example.sharedchest.SharedChestPlugin
import com.example.sharedchest.database.DatabaseManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ChestCommand(
    private val plugin: SharedChestPlugin,
    private val databaseManager: DatabaseManager
) : CommandExecutor {

    private val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("このコマンドはプレイヤーのみ使用できます。")
            return true
        }

        if (!sender.hasPermission("sharedchest.use")) {
            sender.sendMessage(miniMessage.deserialize("<red>このコマンドを使用する権限がありません。"))
            return true
        }

        val rows = plugin.config.getInt("chest.rows", 3).coerceIn(1, 6)
        val titleStr = plugin.config.getString("chest.title", "<gold>共有チェスト") ?: "<gold>共有チェスト"
        val slots = rows * 9
        val uuid = sender.uniqueId

        // AsyncScheduler で DB 操作 (Folia ではメインスレッドでの DB 操作を回避)
        plugin.server.asyncScheduler.runNow(plugin) {
            // ロックを試みる (重複防止: 別サーバー等で開いている場合は false)
            val locked = databaseManager.tryLock(uuid).join()

            if (!locked) {
                // EntityScheduler でプレイヤーにメッセージ送信
                sender.scheduler.run(plugin, {
                    sender.sendMessage(
                        miniMessage.deserialize("<red>別のサーバーで共有チェストを開いているか、処理中です。しばらく待ってから再度お試しください。")
                    )
                }, null)
                return@runNow
            }

            // DBからチェスト内容を取得
            val contents = databaseManager.loadChest(uuid, slots).join()

            // プレイヤーへのインベントリ操作は EntityScheduler で実行 (Folia 必須)
            sender.scheduler.run(plugin, {
                if (!sender.isOnline) {
                    plugin.server.asyncScheduler.runNow(plugin) {
                        databaseManager.unlock(uuid).join()
                    }
                    return@run
                }

                val inventory = plugin.server.createInventory(
                    null,
                    slots,
                    miniMessage.deserialize(titleStr)
                )

                for (i in 0 until minOf(contents.size, slots)) {
                    val item = contents[i]
                    if (item != null) {
                        inventory.setItem(i, item)
                    }
                }

                plugin.openChests[uuid] = inventory
                val view = sender.openInventory(inventory)

                if (view == null) {
                    plugin.openChests.remove(uuid)
                    plugin.server.asyncScheduler.runNow(plugin) {
                        databaseManager.unlock(uuid).join()
                    }
                    sender.sendMessage(
                        miniMessage.deserialize("<red>チェストを開けませんでした。再度お試しください。")
                    )
                }
            }, null)
        }

        return true
    }
}
