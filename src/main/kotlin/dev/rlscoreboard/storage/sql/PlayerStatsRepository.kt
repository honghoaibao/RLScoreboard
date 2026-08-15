package dev.rlscoreboard.storage.sql

import dev.rlscoreboard.api.model.LeaderboardEntry
import java.util.UUID
import java.util.logging.Logger

/**
 * Persists per-player stat values (kills, deaths, playtime, economy balance, ...) keyed by
 * an arbitrary [statKey] string, independent of whether the player is currently online.
 * This is what makes offline-inclusive leaderboards possible - see
 * [dev.rlscoreboard.leaderboard.datasource.PersistentStatDataSource] and
 * [dev.rlscoreboard.storage.StatsSyncService], which keeps this table populated.
 *
 * Every method here is blocking JDBC I/O - always call from an async task, never the main
 * thread (section 17 of the design spec).
 */
class PlayerStatsRepository(private val database: Database, private val logger: Logger) {

    fun initSchema() {
        database.use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS rlscoreboard_player_stats (
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        stat_key TEXT NOT NULL,
                        value REAL NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, stat_key)
                    )
                    """.trimIndent()
                )
                st.execute("CREATE INDEX IF NOT EXISTS idx_rlscoreboard_stat_key_value ON rlscoreboard_player_stats (stat_key, value)")
            }
        }
    }

    fun upsert(playerId: UUID, playerName: String, statKey: String, value: Double) {
        val sql = if (database.type == DatabaseType.MYSQL) {
            """
            INSERT INTO rlscoreboard_player_stats (player_uuid, player_name, stat_key, value, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), value = VALUES(value), updated_at = VALUES(updated_at)
            """.trimIndent()
        } else {
            """
            INSERT INTO rlscoreboard_player_stats (player_uuid, player_name, stat_key, value, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid, stat_key) DO UPDATE SET
                player_name = excluded.player_name, value = excluded.value, updated_at = excluded.updated_at
            """.trimIndent()
        }

        runCatching {
            database.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId.toString())
                    ps.setString(2, playerName)
                    ps.setString(3, statKey)
                    ps.setDouble(4, value)
                    ps.setLong(5, System.currentTimeMillis())
                    ps.executeUpdate()
                }
            }
        }.onFailure { logger.warning("[Storage] Failed to save stat '$statKey' for $playerName: ${it.message}") }
    }

    fun topN(statKey: String, limit: Int): List<LeaderboardEntry> = runCatching {
        database.use { conn ->
            conn.prepareStatement(
                "SELECT player_uuid, player_name, value FROM rlscoreboard_player_stats WHERE stat_key = ? ORDER BY value DESC LIMIT ?"
            ).use { ps ->
                ps.setString(1, statKey)
                ps.setInt(2, limit)
                ps.executeQuery().use { rs ->
                    val out = mutableListOf<LeaderboardEntry>()
                    while (rs.next()) {
                        val uuid = runCatching { UUID.fromString(rs.getString("player_uuid")) }.getOrNull()
                        out += LeaderboardEntry(uuid, rs.getString("player_name"), rs.getDouble("value"))
                    }
                    out
                }
            }
        }
    }.onFailure { logger.warning("[Storage] Failed to read top $limit for '$statKey': ${it.message}") }
        .getOrDefault(emptyList())
}
