package com.example.sharedchest

import com.example.sharedchest.database.ChestRepository
import com.example.sharedchest.i18n.Language
import com.example.sharedchest.util.ChestLayout
import com.example.sharedchest.util.ItemSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * 共有チェストの開閉と保存。
 * データベースの処理は SharedChestPlugin.runDb の 1 本のスレッドで順番に行い、
 * インベントリの操作はプレイヤーのリージョンで行う。
 */
class ChestService(private val plugin: SharedChestPlugin, private val repository: ChestRepository) {

    private class Session(val player: UUID, val token: String, val inventory: Inventory)

    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val messages get() = plugin.messages

    /** どのスレッドから呼んでもよい。 */
    fun open(player: Player) {
        val id = player.uniqueId
        if (sessions.containsKey(id)) {
            messages.send(player, "busy")
            return
        }
        val rows = plugin.config.getInt("chest.rows", 3)
        val token = UUID.randomUUID().toString()

        plugin.runDb {
            val items = lockAndLoad(player, token) ?: return@runDb
            val scheduled = player.scheduler.run(plugin, { _ ->
                show(player, token, items, rows)
            }, {
                // 開く前にログアウトした。
                plugin.runDb { unlock(id, token) }
            })
            if (scheduled == null) unlock(id, token)
        }
    }

    /** データベースのスレッドで動く。開けないときはプレイヤーに伝えて null を返す。 */
    private fun lockAndLoad(player: Player, token: String): Array<ItemStack?>? {
        val id = player.uniqueId
        val locked = try {
            repository.tryLock(id, token)
        } catch (e: SQLException) {
            plugin.logger.log(Level.SEVERE, "ロック取得に失敗しました (UUID: $id)", e)
            messages.send(player, "db-error")
            return null
        }
        if (!locked) {
            messages.send(player, "busy")
            return null
        }
        return try {
            val items = ItemSerializer.deserialize(repository.load(id))
            require(items.indexOfLast { it != null } < ChestLayout.MAX_SLOTS) { "アイテムが多すぎます: ${items.size}" }
            items
        } catch (e: Exception) {
            // ここで空のチェストを開くと、閉じたときに保存されている中身を消してしまう。
            plugin.logger.log(Level.SEVERE, "チェスト読み込みに失敗しました (UUID: $id)", e)
            unlock(id, token)
            messages.send(player, "db-error")
            null
        }
    }

    /** プレイヤーのリージョンで動く。 */
    private fun show(player: Player, token: String, items: Array<ItemStack?>, rows: Int) {
        val id = player.uniqueId
        val size = ChestLayout.sizeFor(rows, items.indexOfLast { it != null })
        val inventory = plugin.server.createInventory(null, size, messages.chestTitle(Language.of(player)))
        items.forEachIndexed { slot, item -> if (item != null) inventory.setItem(slot, item) }

        val session = Session(id, token, inventory)
        sessions[id] = session
        if (player.openInventory(inventory) == null) {
            sessions.remove(id, session)
            plugin.runDb { unlock(id, token) }
            messages.send(player, "open-failed")
        }
    }

    /** インベントリを閉じたときに呼ぶ。プレイヤーのリージョンから呼ぶこと。 */
    fun onClose(player: UUID, inventory: Inventory) {
        val session = sessions[player] ?: return
        if (session.inventory == inventory && sessions.remove(player, session)) save(session)
    }

    /** プレイヤーが切断したときに呼ぶ。プレイヤーのリージョンから呼ぶこと。 */
    fun onQuit(player: UUID) {
        sessions.remove(player)?.let(::save)
    }

    /** 開いているチェストのロックを延長する。 */
    fun heartbeat() {
        plugin.runDb {
            sessions.values.forEach { session ->
                try {
                    if (!repository.heartbeat(session.player, session.token)) {
                        plugin.logger.severe("チェストのロックを失いました (UUID: ${session.player})。閉じるときの内容は復旧用ファイルへ保存します。")
                    }
                } catch (e: SQLException) {
                    plugin.logger.warning("ロックの延長に失敗しました (UUID: ${session.player}): ${e.message}")
                }
            }
        }
    }

    /** サーバーの停止時に呼ぶ。このときリスナーはもう動かない。 */
    fun saveAll() {
        sessions.keys.toList().forEach(::onQuit)
    }

    private fun save(session: Session) {
        val contents = session.inventory.contents.map { it?.clone() }.toTypedArray()
        plugin.runDb { persist(session, contents) }
    }

    /** データベースのスレッドで動く。保存できなくてもアイテムを失わないよう、最後は復旧用ファイルへ書く。 */
    private fun persist(session: Session, contents: Array<ItemStack?>) {
        val serialized = ItemSerializer.serialize(contents)
        for (attempt in 1..SAVE_ATTEMPTS) {
            try {
                if (repository.save(session.player, session.token, serialized)) return
                plugin.logger.severe("チェストのロックを失っていたため保存できませんでした (UUID: ${session.player})")
                break
            } catch (e: SQLException) {
                plugin.logger.warning("チェスト保存に失敗しました ($attempt/$SAVE_ATTEMPTS, UUID: ${session.player}): ${e.message}")
                if (attempt < SAVE_ATTEMPTS) Thread.sleep(SAVE_RETRY_DELAY_MILLIS)
            }
        }
        writeRecovery(session.player, serialized)
    }

    private fun writeRecovery(player: UUID, serialized: String) {
        val dir = File(plugin.dataFolder, "recovery")
        val file = File(dir, "$player-${RECOVERY_TIME.format(LocalDateTime.now())}.txt")
        try {
            Files.createDirectories(dir.toPath())
            Files.writeString(file.toPath(), serialized)
            plugin.logger.severe("チェストの内容を ${file.path} へ保存しました。README の手順で復旧してください。")
        } catch (e: IOException) {
            plugin.logger.log(Level.SEVERE, "復旧用ファイルも保存できませんでした (UUID: $player): $serialized", e)
        }
    }

    private fun unlock(player: UUID, token: String) {
        try {
            repository.unlock(player, token)
        } catch (e: SQLException) {
            // 解除できなくても期限が来れば別の人が開ける。
            plugin.logger.warning("アンロックに失敗しました (UUID: $player): ${e.message}")
        }
    }

    private companion object {
        const val SAVE_ATTEMPTS = 3
        const val SAVE_RETRY_DELAY_MILLIS = 2000L
        val RECOVERY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
