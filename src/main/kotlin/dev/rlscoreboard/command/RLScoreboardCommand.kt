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

/** Implements every subcommand from section 14 of the design spec. */
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
        sender.sendMessage(prefixed(plugin.configManager.message("reloaded", "&aReloaded config, scoreboards and leaderboards.")))
    }

    private fun handleVersion(sender: CommandSender) {
        sender.sendMessage(prefixed("&7RLScoreboard v${plugin.pluginMeta.version}"))
    }

    private fun handleDebug(sender: CommandSender) {
        if (!sender.hasPermission("rlscoreboard.debug")) return denyPermission(sender)
        if (sender !is Player) {
            sender.sendMessage(prefixed("&cThis command must be run in-game."))
            return
        }
        val board = plugin.boardManager.resolveBoardFor(sender)
        sender.sendMessage(prefixed("&7Active board: &f${board?.id ?: "none"}"))
        sender.sendMessage(prefixed("&7Loaded boards: &f${plugin.boardManager.all().size}"))
        sender.sendMessage(prefixed("&7Loaded leaderboards: &f${plugin.leaderboardEngine.manager.all().size}"))
    }

    private fun handleBoard(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) return sendHelp(sender)
        when (args[1].lowercase()) {
            "list" -> sender.sendMessage(prefixed("&7Boards: &f${plugin.boardManager.all().joinToString(", ") { it.id }}"))
            "reload" -> {
                if (!sender.hasPermission("rlscoreboard.reload")) return denyPermission(sender)
                plugin.boardManager.reload()
                sender.sendMessage(prefixed("&aScoreboards reloaded."))
            }
            else -> sendHelp(sender)
        }
    }

    private fun handleLeaderboard(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) return sendHelp(sender)
        val lm = plugin.leaderboardEngine.manager

        when (args[1].lowercase()) {
            "list" -> sender.sendMessage(prefixed("&7Leaderboards: &f${lm.all().joinToString(", ") { it.id }}"))
            "create" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                val id = args.getOrNull(2)
                if (id == null) {
                    sender.sendMessage(prefixed("&cUsage: /rlscoreboard leaderboard create <id> [SIDEBAR|HOLOGRAM|TAB|NPC|GUI] [datasource]"))
                    return
                }
                val type = args.getOrNull(3) ?: "SIDEBAR"
                val dataSource = args.getOrNull(4) ?: "manual"
                lm.create(id, type, dataSource)
                sender.sendMessage(prefixed("&aCreated leaderboard '$id'."))
            }
            "delete" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                val id = args.getOrNull(2)
                if (id == null) {
                    sender.sendMessage(prefixed("&cUsage: /rlscoreboard leaderboard delete <id>"))
                    return
                }
                if (lm.delete(id)) sender.sendMessage(prefixed("&aDeleted leaderboard '$id'."))
                else sender.sendMessage(prefixed("&cNo leaderboard named '$id'."))
            }
            "setlocation" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                if (sender !is Player) {
                    sender.sendMessage(prefixed("&cThis command must be run in-game."))
                    return
                }
                val id = args.getOrNull(2)
                if (id == null) {
                    sender.sendMessage(prefixed("&cUsage: /rlscoreboard leaderboard setlocation <id>"))
                    return
                }
                val loc = sender.location
                lm.setLocation(id, LeaderboardLocation(loc.world!!.name, loc.x, loc.y, loc.z, loc.yaw, loc.pitch))
                sender.sendMessage(prefixed("&aLocation for '$id' set to your current position."))
            }
            "reload" -> {
                if (!sender.hasPermission("rlscoreboard.leaderboard.manage")) return denyPermission(sender)
                lm.reload()
                sender.sendMessage(prefixed("&aLeaderboards reloaded."))
            }
            "view" -> handleLeaderboardView(sender, args)
            "history" -> handleLeaderboardHistory(sender, args)
            else -> sendHelp(sender)
        }
    }

    /** Opens a GUI-type leaderboard's latest snapshot - any player can use this, no admin permission needed. */
    private fun handleLeaderboardView(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(prefixed("&cThis command must be run in-game."))
            return
        }
        val id = args.getOrNull(2)
        if (id == null) {
            sender.sendMessage(prefixed("&cUsage: /rlscoreboard leaderboard view <id>"))
            return
        }
        val renderer = plugin.leaderboardEngine.manager.rendererFor("GUI") as? GuiLeaderboardRenderer
        val inventory = renderer?.open(id)
        if (inventory == null) {
            sender.sendMessage(prefixed("&cNo GUI snapshot for '$id' yet - check it's a GUI-type leaderboard and has refreshed at least once."))
        } else {
            sender.openInventory(inventory)
        }
    }

    /** Prints a leaderboard's ranking as of roughly N hours ago, from stored history. Requires `storage.enabled: true`. */
    private fun handleLeaderboardHistory(sender: CommandSender, args: Array<out String>) {
        val id = args.getOrNull(2)
        if (id == null) {
            sender.sendMessage(prefixed("&cUsage: /rlscoreboard leaderboard history <id> [hours-ago]"))
            return
        }
        val repository = plugin.leaderboardHistoryRepository
        if (repository == null) {
            sender.sendMessage(prefixed("&cStorage isn't enabled - see storage.enabled in config.yml."))
            return
        }
        val hoursAgo = args.getOrNull(3)?.toLongOrNull() ?: 24L
        val beforeMillis = System.currentTimeMillis() - hoursAgo * 3_600_000L

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val entries = repository.snapshotBefore(id, beforeMillis, 10)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (entries.isEmpty()) {
                    sender.sendMessage(prefixed("&7No history found for '$id' around $hoursAgo hour(s) ago."))
                } else {
                    sender.sendMessage(prefixed("&6'$id' &7- snapshot from ~$hoursAgo hour(s) ago:"))
                    entries.forEachIndexed { index, entry ->
                        sender.sendMessage(prefixed("&e#${index + 1} &f${entry.displayName} &7- &f${entry.formattedValue}"))
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
        sender.sendMessage(prefixed("&6RLScoreboard &7- &f/rlscoreboard reload|version|debug|board|leaderboard"))
        sender.sendMessage(prefixed("&7/rlscoreboard leaderboard list|create|delete|setlocation|reload|view|history"))
    }

    private fun denyPermission(sender: CommandSender) {
        sender.sendMessage(prefixed(plugin.configManager.message("no-permission", "&cYou don't have permission to do that.")))
    }

    private fun prefixed(text: String): Component = ColorUtil.toComponent(text)
}
