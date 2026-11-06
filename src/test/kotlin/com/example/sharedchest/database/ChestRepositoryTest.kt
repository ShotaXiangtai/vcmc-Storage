package com.example.sharedchest.database

import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** H2 の MySQL 互換モードでロックの動きを確認する。 */
class ChestRepositoryTest {
    private lateinit var dataSource: DataSource
    private lateinit var repository: ChestRepository
    private val player = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
        }
        repository = ChestRepository(dataSource)
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection -> connection.createStatement().use { it.executeUpdate(sql) } }
    }

    /** 最後の更新から minutes 分たったことにする。 */
    private fun age(minutes: Int) {
        execute("UPDATE shared_chests SET updated_at = NOW() - INTERVAL '$minutes' MINUTE")
    }

    @Test
    fun `初めて開くと空の内容でロックできる`() {
        assertTrue(repository.tryLock(player, "a"))
        assertEquals("", repository.load(player))
    }

    @Test
    fun `開いている間は別のサーバーから開けない`() {
        assertTrue(repository.tryLock(player, "a"))
        assertFalse(repository.tryLock(player, "b"))
    }

    @Test
    fun `別のプレイヤーのチェストには影響しない`() {
        assertTrue(repository.tryLock(player, "a"))
        assertTrue(repository.tryLock(UUID.randomUUID(), "b"))
    }

    @Test
    fun `保存するとロックが外れて内容を読める`() {
        repository.tryLock(player, "a")
        assertTrue(repository.save(player, "a", "中身"))
        assertTrue(repository.tryLock(player, "b"))
        assertEquals("中身", repository.load(player))
    }

    @Test
    fun `解除すると内容を変えずにロックが外れる`() {
        repository.tryLock(player, "a")
        repository.save(player, "a", "中身")
        repository.tryLock(player, "b")
        repository.unlock(player, "b")
        assertTrue(repository.tryLock(player, "c"))
        assertEquals("中身", repository.load(player))
    }

    @Test
    fun `期限が切れたロックは別のサーバーが取得できる`() {
        repository.tryLock(player, "a")
        age(ChestRepository.LOCK_EXPIRY_MINUTES + 1)
        assertTrue(repository.tryLock(player, "b"))
    }

    @Test
    fun `更新し続けていれば長く開いていても取られない`() {
        repository.tryLock(player, "a")
        age(ChestRepository.LOCK_EXPIRY_MINUTES + 1)
        assertTrue(repository.heartbeat(player, "a"))
        assertFalse(repository.tryLock(player, "b"))
    }

    @Test
    fun `ロックを失ったサーバーは保存も解除も延長もできない`() {
        repository.tryLock(player, "a")
        repository.save(player, "a", "最初の内容")
        repository.tryLock(player, "a")
        age(ChestRepository.LOCK_EXPIRY_MINUTES + 1)
        repository.tryLock(player, "b")

        assertFalse(repository.save(player, "a", "古いサーバーの内容"))
        assertFalse(repository.heartbeat(player, "a"))
        repository.unlock(player, "a")

        assertEquals("最初の内容", repository.load(player))
        assertFalse(repository.tryLock(player, "c"))
        assertTrue(repository.save(player, "b", "新しい内容"))
        assertEquals("新しい内容", repository.load(player))
    }

    @Test
    fun `以前のテーブルでもそのまま使える`() {
        execute(
            """
            CREATE TABLE shared_chests (
              uuid VARCHAR(36) NOT NULL PRIMARY KEY,
              contents LONGTEXT NOT NULL,
              is_open TINYINT(1) NOT NULL DEFAULT 0,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        )
        execute("INSERT INTO shared_chests (uuid, contents, is_open) VALUES ('$player', '以前の内容', 0)")

        assertTrue(repository.tryLock(player, "a"))
        assertEquals("以前の内容", repository.load(player))
    }

    @Test
    fun `以前のバージョンが残したロックも期限が来れば取得できる`() {
        repository.tryLock(player, "a")
        execute("UPDATE shared_chests SET lock_token = NULL")
        assertFalse(repository.tryLock(player, "b"))
        age(ChestRepository.LOCK_EXPIRY_MINUTES + 1)
        assertTrue(repository.tryLock(player, "b"))
    }
}
