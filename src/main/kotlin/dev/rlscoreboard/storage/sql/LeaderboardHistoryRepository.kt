package dev.rlscoreboard.storage.sql

import dev.rlscoreboard.api.model.LeaderboardEntry
import java.util.UUID
import java.util.logging.Logger

/**
 * Timestamped top-N snapshots per leaderboard, so "what did this leaderboard look like N
 * hours/days ago" is answerable - see [dev.rlscoreboard.storage.LeaderboardHistoryService],
 * which populates this, and `/rlscoreboard leaderboard history` in RLScoreboardCommand,
 * which reads it. Every method here is blocking JDBC I/O - always call from an async task.
 */
class LeaderboardHistoryRepository(private val database: Database, private val logger: Logger) {

    fun initSchema() {
        database.use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS rlscoreboard_leaderboard_history (
                        leaderboard_id TEXT NOT NULL,
                        snapshot_at INTEGER NOT NULL,
                        rank INTEGER NOT NULL,
                        player_uuid TEXT,
                        player_name TEXT NOT NULL,
                        value REAL NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    "CREATE INDEX IF NOT EXISTS idx_rlscoreboard_history_lookup " +
                        "ON rlscoreboard_leaderboard_history (leaderboard_id, snapshot_at)"
                )
            }
        }
    }

    fun saveSnapshot(leaderboardId: String, snapshotAt: Long, entries: List<LeaderboardEntry>) {
        if (entries.isEmpty()) return
        runCatching {
            database.use { conn ->
                conn.prepareStatement(
                    "INSERT INTO rlscoreboard_leaderboard_history " +
                        "(leaderboard_id, snapshot_at, rank, player_uuid, player_name, value) VALUES (?, ?, ?, ?, ?, ?)"
                ).use { ps ->
                    entries.forEachIndexed { index, entry ->
                        ps.setString(1, leaderboardId)
                        ps.setLong(2, snapshotAt)
                        ps.setInt(3, index + 1)
                        ps.setString(4, entry.playerId?.toString())
                        ps.setString(5, entry.displayName)
                        ps.setDouble(6, entry.value)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
        }.onFailure { logger.warning("[Storage] Failed to save leaderboard history for '$leaderboardId': ${it.message}") }
    }

    /** The most recent snapshot at or before [beforeMillis] - e.g. "leaderboard as of ~N hours ago". Empty if none exists yet. */
    fun snapshotBefore(leaderboardId: String, beforeMillis: Long, limit: Int): List<LeaderboardEntry> = runCatching {
        database.use { conn ->
            var latestAt: Long? = null
            conn.prepareStatement(
                "SELECT MAX(snapshot_at) FROM rlscoreboard_leaderboard_history WHERE leaderboard_id = ? AND snapshot_at <= ?"
            ).use { ps ->
                ps.setString(1, leaderboardId)
                ps.setLong(2, beforeMillis)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val value = rs.getLong(1)
                        if (!rs.wasNull()) latestAt = value
                    }
                }
            }

            val resolvedAt = latestAt
            if (resolvedAt == null) {
                emptyList()
            } else {
                conn.prepareStatement(
                    "SELECT player_uuid, player_name, value FROM rlscoreboard_leaderboard_history " +
                        "WHERE leaderboard_id = ? AND snapshot_at = ? ORDER BY rank ASC LIMIT ?"
                ).use { ps ->
                    ps.setString(1, leaderboardId)
                    ps.setLong(2, resolvedAt)
                    ps.setInt(3, limit)
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<LeaderboardEntry>()
                        while (rs.next()) {
                            val uuid = rs.getString("player_uuid")?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
                            out += LeaderboardEntry(uuid, rs.getString("player_name"), rs.getDouble("value"))
                        }
                        out
                    }
                }
            }
        }
    }.onFailure { logger.warning("[Storage] Failed to read leaderboard history for '$leaderboardId': ${it.message}") }
        .getOrDefault(emptyList())
}
