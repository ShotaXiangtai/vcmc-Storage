package com.example.sharedchest.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.ConfigurationSection
import java.util.logging.Logger

/** MySQL への接続プールの管理。 */
class DatabaseManager(private val logger: Logger) {

    private var dataSource: HikariDataSource? = null

    /** データベースが起動していなくてもサーバーは止めず、使うときに接続を試みる。 */
    fun initialize(config: ConfigurationSection): ChestRepository {
        val hikari = HikariConfig().apply {
            jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "sharedchest"),
                config.getBoolean("database.use-ssl", false),
            )
            driverClassName = "com.mysql.cj.jdbc.Driver"
            username = config.getString("database.user", "root")
            password = config.getString("database.password", "password")
            maximumPoolSize = config.getInt("database.pool-size", 5)
            minimumIdle = 1
            connectionTimeout = CONNECTION_TIMEOUT_MILLIS
            initializationFailTimeout = -1
            poolName = "SharedChestPool"
        }
        val source = HikariDataSource(hikari)
        dataSource = source
        logger.info("データベース接続プールを初期化しました。")
        return ChestRepository(source)
    }

    fun shutdown() {
        dataSource?.let {
            if (!it.isClosed) {
                it.close()
                logger.info("データベース接続プールを終了しました。")
            }
        }
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 5000L
    }
}
