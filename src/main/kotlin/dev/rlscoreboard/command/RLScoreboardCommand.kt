package dev.rlscoreboard.command

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.model.LeaderboardLocation
import dev.rlscoreboard.leaderboard.renderer.GuiLeaderboardRenderer
import dev.rlscoreboard.util.ColorUtil
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** Implements every subcommand from section 14 of the design spec. All player/admin-facing text is
 *  resolved through [dev.rlscoreboard.config.LocaleManager] - see locales/en.yml and locales/vi.yml. */
class RLScoreboardCommand(private val plugin: RLScoreboardPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "version" -> handleVersion(sender)
            "debug" -> handleDebug(sender)
            "board" -> handleBoard(sender, args)
            "leaderboard", "lb" -> handleLeaderboard(sender, args)
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("rlscoreboard.reload")) return denyPermission(sender)
        plugin.reloadEverything()
        sender.sendMessage(prefixed(msg("reload_success")))
    }

    private fun handleVersion(sender: CommandSender) {
        sender.sendMessage(prefixed(msg("version", "version" to plugin.pluginMeta.version)))
    }

    private fun handleDebug(sender: CommandSender) {
        if (!sender.hasPermission("rlscoreboard.debug")) return denyPermission(sender)
        if (sender !is Player) {
            sender.sendMessage(prefixed(msg("player_only")))
            return
        }
        val board = plugin.boardManager.resolveBoardFor(sender)
        sender.sendMessage(prefixed(msg("debug_active_board", "board" to (board?.id ?: "none"))))
        sender.sendMessage(prefixed(msg("debug_loaded_boards", "count" to plugin.boardManager.all().size.toString())))
        sender.sendMessage(prefixed(msg("debug_loaded_leaderboards", "count" to plugin.leaderboardEngine.manager.all().size.toString())))
    }

    private fun handleBoard(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) return sendHelp(sender)
        when (args[1].lowercase()) {
            "list" -> sender.sendMessage(prefixed(msg("board_list", "boards" to plugin.boardManager.all().joinToString(", ") { it.id })))
            "reload" -> {
                if (!sender.hasPermission("rlscoreboard.reload")) return denyPermission(sender)
                plugin.boardManager.reload()
                sender.sendMessage(prefixed(msg("board_reload_success")))
            }
            else -> sendHelp(sender)
        }
    }

    private fun handleLeaderboard(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) return sendHelp(sender)
        val lm = plugin.leaderboardEngine.manager

        when (args[1].lowercase()) {
            "list" -> sender.sendMessage(prefixed(msg("leaderboard_list", "leaderboards" to lm.all().joinToString(", ") { it.id })))
            "create" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                val id = args.getOrNull(2)
                if (id == null) {
                    sender.sendMessage(prefixed(msg("leaderboard_create_usage")))
                    return
                }
                val type = args.getOrNull(3) ?: "SIDEBAR"
                val dataSource = args.getOrNull(4) ?: "manual"
                lm.create(id, type, dataSource)
                sender.sendMessage(prefixed(msg("leaderboard_created", "id" to id)))
            }
            "delete" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                val id = args.getOrNull(2)
                if (id == null) {
                    sender.sendMessage(prefixed(msg("leaderboard_delete_usage")))
                    return
                }
                if (lm.delete(id)) sender.sendMessage(prefixed(msg("leaderboard_deleted", "id" to id)))
                else sender.sendMessage(prefixed(msg("leaderboard_not_found", "id" to id)))
            }
            "setlocation" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                if (sender !is Player) {
                    sender.sendMessage(prefixed(msg("player_only")))
                    return
                }
                val id = args.getOrNull(2)
                if (id == null) {
                    sender.sendMessage(prefixed(msg("leaderboard_setlocation_usage")))
                    return
                }
                val loc = sender.location
                lm.setLocation(id, LeaderboardLocation(loc.world!!.name, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))
                sender.sendMessage(prefixed(msg("leaderboard_location_set", "id" to id)))
            }
            "reload" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                lm.reload()
                sender.sendMessage(prefixed(msg("leaderboard_reload_success")))
            }
            "view" -> handleLeaderboardView(sender, args)
            "history" -> handleLeaderboardHistory(sender, args)
            else -> sendHelp(sender)
        }
    }

    /** Opens a GUI-type leaderboard's latest snapshot - any player can use this, no admin permission needed. */
    private fun handleLeaderboardView(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(prefixed(msg("player_only")))
            return
        }
        val id = args.getOrNull(2)
        if (id == null) {
            sender.sendMessage(prefixed(msg("leaderboard_view_usage")))
            return
        }
        val renderer = plugin.leaderboardEngine.manager.rendererFor("GUI") as? GuiLeaderboardRenderer
        val inventory = renderer?.open(id)
        if (inventory == null) {
            sender.sendMessage(prefixed(msg("leaderboard_no_gui_snapshot", "id" to id)))
        } else {
            sender.openInventory(inventory)
        }
    }

    /** Prints a leaderboard's ranking as of roughly N hours ago, from stored history. Requires `storage.enabled: true`. */
    private fun handleLeaderboardHistory(sender: CommandSender, args: Array<out String>) {
        val id = args.getOrNull(2)
        if (id == null) {
            sender.sendMessage(prefixed(msg("leaderboard_history_usage")))
            return
        }
        val repository = plugin.leaderboardHistoryRepository
        if (repository == null) {
            sender.sendMessage(prefixed(msg("storage_disabled")))
            return
        }
        val hoursAgo = args.getOrNull(3)?.toLongOrNull() ?: 24L
        val beforeMillis = System.currentTimeMillis() - hoursAgo * 3_600_000L

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val entries = repository.snapshotBefore(id, beforeMillis, 10)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (entries.isEmpty()) {
                    sender.sendMessage(prefixed(msg("leaderboard_history_empty", "id" to id, "hours" to hoursAgo.toString())))
                } else {
                    sender.sendMessage(prefixed(msg("leaderboard_history_header", "id" to id, "hours" to hoursAgo.toString())))
                    entries.forEachIndexed { index, entry ->
                        sender.sendMessage(prefixed(msg(
                            "leaderboard_history_entry",
                            "position" to (index + 1).toString(),
                            "player" to entry.displayName,
                            "value" to entry.formattedValue
                        )))
                    }
                }
            })
        })
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        when (args.size) {
            1 -> listOf("reload", "version", "debug", "board", "leaderboard").filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "board" -> listOf("list", "reload").filter { it.startsWith(args[1].lowercase()) }
                "leaderboard", "lb" -> listOf("list", "create", "delete", "setlocation", "reload", "view", "history")
                    .filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when {
                args[0].equals("leaderboard", true) && args[1].lowercase() in setOf("delete", "setlocation", "view", "history") ->
                    plugin.leaderboardEngine.manager.all().map { it.id }.filter { it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(prefixed(msg("help_header")))
        sender.sendMessage(prefixed(msg("help_leaderboard")))
    }

    private fun denyPermission(sender: CommandSender) {
        sender.sendMessage(prefixed(msg("no_permission")))
    }

    private fun msg(key: String, vararg placeholders: Pair<String, String>): String =
        plugin.localeManager.get(key, *placeholders)

    private fun prefixed(text: String): Component = ColorUtil.toComponent(text)
}
