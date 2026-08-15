package dev.rlscoreboard.core

import dev.rlscoreboard.util.ColorUtil
import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot

/**
 * Turns a resolved board (already placeholder/condition/animation-resolved into plain
 * strings by [LineRenderer]) into an actual Bukkit sidebar scoreboard.
 *
 * Uses the long-standing "one fake score entry + Team per line" technique rather than
 * anything tied to a specific newer scoreboard API surface, so it keeps working the same
 * way across a wide range of Paper versions. Diffs against [PlayerSession] so unchanged
 * frames touch zero scoreboard packets (section 17 performance requirements).
 */
class BoardRenderer(private val sessions: PlayerSessionManager) {

    private companion object {
        const val OBJECTIVE_NAME = "rlscoreboard"
        val ENTRY_CODES = (0..14).map { "§$it§r" } // up to 15 lines, vanilla sidebar limit
    }

    fun render(player: Player, boardId: String, title: String, lines: List<String>) {
        val session = sessions.sessionFor(player)
        var scoreboard = session.scoreboard

        if (scoreboard == null || session.activeBoardId != boardId) {
            scoreboard = Bukkit.getScoreboardManager()!!.newScoreboard
            session.scoreboard = scoreboard
            session.activeBoardId = boardId
            session.lastRenderedLines = emptyList()
            session.lastTitle = ""
            player.scoreboard = scoreboard
        }

        val objective = scoreboard.getObjective(OBJECTIVE_NAME)
            ?: scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, ColorUtil.toComponent(title)).also {
                it.displaySlot = DisplaySlot.SIDEBAR
                // Score numbers still exist internally (below) purely to order the lines - this
                // makes the client not render them, via the official Paper score-display API
                // (io.papermc.paper.scoreboard.numbers.NumberFormat, confirmed present in the
                // paper-api 26.2 this project targets) rather than any packet/NMS hack.
                it.numberFormat(NumberFormat.blank())
            }

        if (title != session.lastTitle) {
            objective.displayName(ColorUtil.toComponent(title))
            session.lastTitle = title
        }

        val capped = lines.take(ENTRY_CODES.size)
        if (capped == session.lastRenderedLines) return // identical frame, nothing to touch

        for (i in capped.size until ENTRY_CODES.size) {
            scoreboard.getTeam(teamName(i))?.unregister()
            val entry = ENTRY_CODES[i]
            if (scoreboard.entries.contains(entry)) scoreboard.resetScores(entry)
        }

        val size = capped.size
        capped.forEachIndexed { index, line ->
            val entry = ENTRY_CODES[index]
            val team = scoreboard.getTeam(teamName(index))
                ?: scoreboard.registerNewTeam(teamName(index)).also { it.addEntry(entry) }
            team.prefix(ColorUtil.toComponent(line))
            team.suffix(Component.empty())
            objective.getScore(entry).score = size - index
        }

        session.lastRenderedLines = capped
    }

    private fun teamName(index: Int) = "rl_line_$index"
}
