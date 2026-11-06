package com.example.sharedchest.listener

import com.example.sharedchest.ChestService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent

class ChestListener(private val chests: ChestService) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        chests.onClose(event.player.uniqueId, event.inventory)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        chests.onQuit(event.player.uniqueId)
    }
}
