package com.example.sharedchest.database

import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

/**
 * shared_chests テーブルの読み書き。
 *
 * チェストを開いている間は、開いた人だけが持つトークンでロックする。
 * サーバーがクラッシュしてもチェストが開けなくならないよう、[LOCK_EXPIRY_MINUTES] 分更新されないロックは解除できる。
 * 開いている間は [heartbeat] で更新し続けるので、長く開いていても別のサーバーから開かれることはない。
 * ロックを失ったサーバーの保存は拒否する。先に開いた側の古い内容で上書きしないため。
 */
class ChestRepository(private val dataSource: DataSource) {
    private var schemaReady = false

    private fun <T> withConnection(block: (Connection) -> T): T {
        dataSource.connection.use { connection ->
            if (!schemaReady) {
                ensureSchema(connection)
                schemaReady = true
            }
            return block(connection)
        }
    }

    private fun ensureSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS shared_chests (
                  uuid       VARCHAR(36)  NOT NULL PRIMARY KEY,
                  contents   LONGTEXT     NOT NULL,
                  is_open    TINYINT(1)   NOT NULL DEFAULT 0,
                  lock_token VARCHAR(36)  NULL,
                  updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
        }
        if (hasLockTokenColumn(connection)) return
        // 1.0.0 のテーブルには lock_token が無い。
        try {
            connection.createStatement().use { it.executeUpdate("ALTER TABLE shared_chests ADD COLUMN lock_token VARCHAR(36) NULL") }
        } catch (e: SQLException) {
            // 別のサーバーが先に追加したのなら問題ない。
            if (!hasLockTokenColumn(connection)) throw e
        }
    }

    private fun hasLockTokenColumn(connection: Connection): Boolean =
        connection.metaData.getColumns(connection.catalog, null, "shared_chests", "lock_token").use { it.next() }

    /** ロックを取得する。他の誰かが開いていれば false。 */
    @Throws(SQLException::class)
    fun tryLock(player: UUID, token: String): Boolean = withConnection { connection ->
        val updated = connection.prepareStatement(
            """
            UPDATE shared_chests SET is_open = 1, lock_token = ?, updated_at = CURRENT_TIMESTAMP
            WHERE uuid = ? AND (is_open = 0 OR updated_at < NOW() - INTERVAL '$LOCK_EXPIRY_MINUTES' MINUTE)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, token)
            statement.setString(2, player.toString())
            statement.executeUpdate()
        }
        if (updated == 1) return@withConnection true

        // 行が無ければ新しく作ってロックする。
        connection.prepareStatement(
            "INSERT IGNORE INTO shared_chests (uuid, contents, is_open, lock_token) VALUES (?, '', 1, ?)",
        ).use { statement ->
            statement.setString(1, player.toString())
            statement.setString(2, token)
            statement.executeUpdate() == 1
        }
    }

    /** 保存されている内容。まだ何も保存していなければ空文字列。 */
    @Throws(SQLException::class)
    fun load(player: UUID): String = withConnection { connection ->
        connection.prepareStatement("SELECT contents FROM shared_chests WHERE uuid = ?").use { statement ->
            statement.setString(1, player.toString())
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1).orEmpty() else "" }
        }
    }

    /** 内容を保存してロックを解放する。ロックを失っていれば何もせず false を返す。 */
    @Throws(SQLException::class)
    fun save(player: UUID, token: String, contents: String): Boolean = withConnection { connection ->
        connection.prepareStatement(
            "UPDATE shared_chests SET contents = ?, is_open = 0, lock_token = NULL WHERE uuid = ? AND lock_token = ?",
        ).use { statement ->
            statement.setString(1, contents)
            statement.setString(2, player.toString())
            statement.setString(3, token)
            statement.executeUpdate() == 1
        }
    }

    /** 内容を変えずにロックを解放する。 */
    @Throws(SQLException::class)
    fun unlock(player: UUID, token: String) {
        withConnection { connection ->
            connection.prepareStatement(
                "UPDATE shared_chests SET is_open = 0, lock_token = NULL WHERE uuid = ? AND lock_token = ?",
            ).use { statement ->
                statement.setString(1, player.toString())
                statement.setString(2, token)
                statement.executeUpdate()
            }
        }
    }

    /** ロックの期限を延ばす。ロックを失っていれば false。 */
    @Throws(SQLException::class)
    fun heartbeat(player: UUID, token: String): Boolean = withConnection { connection ->
        connection.prepareStatement(
            "UPDATE shared_chests SET updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND lock_token = ?",
        ).use { statement ->
            statement.setString(1, player.toString())
            statement.setString(2, token)
            statement.executeUpdate() == 1
        }
    }

    companion object {
        const val LOCK_EXPIRY_MINUTES = 5
    }
}
