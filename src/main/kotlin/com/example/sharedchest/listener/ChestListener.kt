package com.example.sharedchest.listener

import com.example.sharedchest.SharedChestPlugin
import com.example.sharedchest.database.DatabaseManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChestListener(
    private val plugin: SharedChestPlugin,
    private val databaseManager: DatabaseManager
) : Listener {

    /**
     * インベントリクローズ時にチェスト内容を非同期で保存しアンロック
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId
        val openChest = plugin.openChests[uuid] ?: return

        if (event.inventory != openChest) return

        plugin.openChests.remove(uuid)
        val contents = openChest.contents.map { it?.clone() }.toTypedArray()

        plugin.server.asyncScheduler.runNow(plugin) {
            val saved = databaseManager.saveChest(uuid, contents).join()
            if (!saved) {
                plugin.logger.severe("プレイヤー $uuid のチェスト保存に失敗しました。アンロックのみ試行します。")
                databaseManager.unlock(uuid).join()
            }
        }
    }

    /**
     * クリックイベントの検知
     */
    @EventHandler(priority = EventPriority.LOW)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val uuid = player.uniqueId
        val openChest = plugin.openChests[uuid] ?: return

        if (event.view.topInventory != openChest) return
    }

    /**
     * プレイヤー退出時にチェストが開いていれば保存・アンロック
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        val openChest = plugin.openChests.remove(uuid) ?: return
        val contents = openChest.contents.map { it?.clone() }.toTypedArray()

        plugin.server.asyncScheduler.runNow(plugin) {
            val saved = databaseManager.saveChest(uuid, contents).join()
            if (!saved) {
                plugin.logger.severe("切断時チェスト保存に失敗しました (UUID: $uuid)。アンロックのみ試行します。")
                databaseManager.unlock(uuid).join()
            }
        }
    }
}
