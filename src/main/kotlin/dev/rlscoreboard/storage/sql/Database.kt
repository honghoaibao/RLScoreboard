package dev.rlscoreboard.storage.sql

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

enum class DatabaseType { SQLITE, MYSQL }

/**
 * A small, hand-rolled JDBC connection pool - deliberately not HikariCP. A real pooling
 * library means shading (and correctly relocating) another third-party dependency for a
 * plugin whose DB load is occasional upserts and periodic top-N reads, not high-concurrency
 * OLTP traffic; see README "Storage & offline leaderboards" for the full reasoning.
 *
 * SQLite only ever really benefits from a pool of 1 (SQLite serializes writers regardless
 * of how many connections are open), so [requestedPoolSize] is clamped to 1 for that
 * backend; MySQL/MariaDB can use more (`storage.pool-size` in config.yml).
 *
 * All access goes through [use], which borrows a connection, runs the block, and always
 * returns it - safe to call from multiple async threads at once, but never from the main
 * thread (section 17 of the design spec).
 */
class Database(
    val type: DatabaseType,
    private val jdbcUrl: String,
    private val username: String? = null,
    private val password: String? = null,
    requestedPoolSize: Int = 4
) {
    private val poolSize = if (type == DatabaseType.SQLITE) 1 else requestedPoolSize.coerceIn(1, 16)
    private val pool = ArrayBlockingQueue<Connection>(poolSize)

    @Volatile private var started = false

    @Synchronized
    fun connect() {
        if (started) return
        repeat(poolSize) { pool.put(openConnection()) }
        started = true
    }

    private fun openConnection(): Connection =
        if (username != null) DriverManager.getConnection(jdbcUrl, username, password ?: "")
        else DriverManager.getConnection(jdbcUrl)

    /** Borrows a connection, runs [block], always returns it (or a fresh replacement) to the pool. Blocking. */
    fun <T> use(block: (Connection) -> T): T {
        if (!started) connect()

        val borrowed = pool.poll(10, TimeUnit.SECONDS)
            ?: throw IllegalStateException("Timed out waiting for a database connection (pool size: $poolSize)")

        val connection = if (borrowed.isClosed) {
            runCatching { borrowed.close() }
            openConnection()
        } else {
            borrowed
        }

        try {
            return block(connection)
        } finally {
            if (!connection.isClosed) {
                pool.put(connection)
            } else {
                // Died mid-use (connection drop, DB restart, ...) - replace it so the pool
                // doesn't shrink permanently. If even this fails, the pool is down one
                // connection until the next connect() - acceptable degradation, not a crash.
                runCatching { pool.put(openConnection()) }
            }
        }
    }

    @Synchronized
    fun close() {
        started = false
        val drained = mutableListOf<Connection>()
        pool.drainTo(drained)
        drained.forEach { runCatching { it.close() } }
    }
}
