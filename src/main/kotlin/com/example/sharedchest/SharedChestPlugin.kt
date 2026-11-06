package com.example.sharedchest

import com.example.sharedchest.command.ChestCommand
import com.example.sharedchest.database.DatabaseManager
import com.example.sharedchest.listener.ChestListener
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.logging.Level

@Suppress("UnstableApiUsage")
class SharedChestPlugin : JavaPlugin() {

    lateinit var messages: Messages
        private set

    private lateinit var databaseManager: DatabaseManager
    private lateinit var chests: ChestService
    private lateinit var dbThread: ExecutorService

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages(this)
        messages.reload()

        dbThread = Executors.newSingleThreadExecutor { task -> Thread(task, "SharedChest-db").apply { isDaemon = true } }
        databaseManager = DatabaseManager(logger)
        chests = ChestService(this, databaseManager.initialize(config))

        server.pluginManager.registerEvents(ChestListener(chests), this)

        val command = ChestCommand(this, chests)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register("gchest", "共有チェストを開きます", command)
        }

        // 期限が切れて別のサーバーから開かれないよう、開いている間はロックを更新する。
        server.asyncScheduler.runAtFixedRate(this, { _ -> chests.heartbeat() }, 1L, 1L, TimeUnit.MINUTES)

        logger.info("SharedChest が有効化されました。")
    }

    override fun onDisable() {
        if (::chests.isInitialized) {
            chests.saveAll()
            dbThread.shutdown()
            if (!dbThread.awaitTermination(DB_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.severe("チェストの保存が $DB_SHUTDOWN_TIMEOUT_SECONDS 秒で終わりませんでした。")
            }
            databaseManager.shutdown()
        }
        logger.info("SharedChest が無効化されました。")
    }

    /** データベースの処理を専用のスレッドで順番に実行する。プラグインの停止後は呼び出したスレッドで実行する。 */
    fun runDb(task: () -> Unit) {
        val safeTask = Runnable {
            try {
                task()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "データベースの処理に失敗しました", e)
            }
        }
        try {
            dbThread.execute(safeTask)
        } catch (_: RejectedExecutionException) {
            safeTask.run()
        }
    }

    private companion object {
        const val DB_SHUTDOWN_TIMEOUT_SECONDS = 60L
    }
}
