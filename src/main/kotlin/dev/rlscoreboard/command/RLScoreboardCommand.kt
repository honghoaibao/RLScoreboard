package dev.rlscoreboard.command

import dev.rlscoreboard.RLScoreboardPlugin
import dev.rlscoreboard.api.model.LeaderboardLocation
import dev.rlscoreboard.config.LocaleValidator
import dev.rlscoreboard.integration.Integration
import dev.rlscoreboard.integration.IntegrationStatus
import dev.rlscoreboard.leaderboard.renderer.GuiLeaderboardRenderer
import dev.rlscoreboard.util.ColorUtil
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** Implements every subcommand from section 14 of the design spec, plus `status`/`integrations`/
 *  `language`/`validate-language` from later addenda. All player/admin-facing text is resolved
 *  through [dev.rlscoreboard.config.LocaleManager] - see locales/en.yml, locales/vi.yml,
 *  locales/ja.yml. */
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
            "status" -> handleStatus(sender)
            "integrations" -> handleIntegrations(sender)
            "board" -> handleBoard(sender, args)
            "leaderboard", "lb" -> handleLeaderboard(sender, args)
            "language", "lang" -> handleLanguage(sender, args)
            "validate-language" -> handleValidateLanguage(sender, args)
            else -> sendHelp(sender)
        }
        return true
    }

    /**
     * `/rlscoreboard status` (design spec sections 24/25) - a shorter, public-facing system
     * overview (version/platform/player count/active boards/integrations at a glance).
     * Distinct from `/rlscoreboard debug`, which is aimed at an admin actively troubleshooting
     * (loaded-vs-active counts, per-integration detail via `/rlscoreboard integrations`) and
     * is intentionally not something anything auto-runs or spams on its own - both commands
     * only ever run when an admin explicitly types them.
     */
    private fun handleStatus(sender: CommandSender) {
        if (!sender.hasPermission("rlscoreboard.debug")) return denyPermission(sender)
        sender.sendMessage(prefixed(msg("status_header", "version" to plugin.pluginMeta.version)))
        sender.sendMessage(prefixed(msg("status_platform", "paper" to Bukkit.getVersion(), "java" to System.getProperty("java.version"))))
        sender.sendMessage(prefixed(msg("status_players", "count" to Bukkit.getOnlinePlayers().size.toString())))
        sender.sendMessage(
            prefixed(
                msg(
                    "status_boards",
                    "active" to plugin.sessionManager.distinctActiveBoardIds().size.toString(),
                    "configured" to plugin.boardManager.all().size.toString()
                )
            )
        )
        sender.sendMessage(prefixed(msg("status_integrations_header")))
        plugin.integrationManager.integrations().forEach { integration ->
            val key = if (integration.status.isEnabled) "debug_integration_available" else "debug_integration_unavailable"
            sender.sendMessage(prefixed(msg(key, "name" to integration.pluginName)))
        }
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

        // Design spec section 25: "/rlscoreboard status" / "/rlscoreboard debug" both list
        // every integration's status - this plugin's diagnostics command is named `debug`.
        sender.sendMessage(prefixed(msg("debug_integrations_header")))
        plugin.integrationManager.integrations().forEach { integration ->
            val key = if (integration.status.isEnabled) "debug_integration_available" else "debug_integration_unavailable"
            sender.sendMessage(prefixed(msg(key, "name" to integration.pluginName)))
        }
    }

    /** `/rlscoreboard integrations` (design spec section 4/25) - full per-integration breakdown: version, status, capabilities. */
    private fun handleIntegrations(sender: CommandSender) {
        if (!sender.hasPermission("rlscoreboard.debug")) return denyPermission(sender)
        sender.sendMessage(prefixed(msg("integrations_header")))
        plugin.integrationManager.integrations().forEach { integration -> sendIntegrationBlock(sender, integration) }
    }

    /**
     * Six statuses, not four (design spec section 4) - [IntegrationStatus.INCOMPATIBLE]
     * (below the tested minimum, genuinely risky) is reported distinctly from
     * [IntegrationStatus.DETECTED_UNTESTED] (above the tested maximum, or an unparseable
     * version - probably fine, just unverified), and [IntegrationStatus.PARTIALLY_SUPPORTED]
     * shows exactly which declared capabilities didn't wire up, not just a blanket "broken".
     */
    private fun sendIntegrationBlock(sender: CommandSender, integration: Integration) {
        val icon = integration.status.icon
        when (integration.status) {
            IntegrationStatus.SUPPORTED -> {
                sender.sendMessage(prefixed(msg("integrations_name_supported", "icon" to icon, "name" to integration.pluginName)))
                sender.sendMessage(prefixed(msg("integrations_line_version", "version" to (integration.detectedVersion ?: "?"))))
                sender.sendMessage(prefixed(msg("integrations_line_status_active")))
                sendCapabilitiesLine(sender, integration.activeCapabilities)
            }
            IntegrationStatus.PARTIALLY_SUPPORTED -> {
                sender.sendMessage(prefixed(msg("integrations_name_partial", "icon" to icon, "name" to integration.pluginName)))
                sender.sendMessage(prefixed(msg("integrations_line_version", "version" to (integration.detectedVersion ?: "?"))))
                val missing = integration.capabilities - integration.activeCapabilities
                sender.sendMessage(prefixed(msg("integrations_line_status_partial", "missing" to missing.joinToString(", "))))
                sendCapabilitiesLine(sender, integration.activeCapabilities)
            }
            IntegrationStatus.DETECTED_UNTESTED -> {
                sender.sendMessage(prefixed(msg("integrations_name_untested", "icon" to icon, "name" to integration.pluginName)))
                sender.sendMessage(
                    prefixed(
                        msg(
                            "integrations_line_status_untested",
                            "version" to (integration.detectedVersion ?: "?"),
                            "min" to integration.minSupportedVersion,
                            "max" to integration.maxTestedVersion
                        )
                    )
                )
                sendCapabilitiesLine(sender, integration.activeCapabilities)
            }
            IntegrationStatus.INCOMPATIBLE -> {
                sender.sendMessage(prefixed(msg("integrations_name_incompatible", "icon" to icon, "name" to integration.pluginName)))
                sender.sendMessage(
                    prefixed(
                        msg(
                            "integrations_line_status_incompatible",
                            "version" to (integration.detectedVersion ?: "?"),
                            "min" to integration.minSupportedVersion
                        )
                    )
                )
            }
            IntegrationStatus.ERROR -> {
                sender.sendMessage(prefixed(msg("integrations_name_error", "icon" to icon, "name" to integration.pluginName)))
                sender.sendMessage(prefixed(msg("integrations_line_status_error")))
            }
            IntegrationStatus.NOT_INSTALLED -> {
                sender.sendMessage(prefixed(msg("integrations_name_not_installed", "icon" to icon, "name" to integration.pluginName)))
                sender.sendMessage(prefixed(msg("integrations_line_status_not_installed")))
            }
        }
    }

    private fun sendCapabilitiesLine(sender: CommandSender, capabilities: Set<String>) {
        sender.sendMessage(
            prefixed(
                if (capabilities.isEmpty()) msg("integrations_line_capabilities_none")
                else msg("integrations_line_capabilities", "capabilities" to capabilities.sorted().joinToString(", "))
            )
        )
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

    /** `/rlscoreboard language [list|info|set <locale>]` (design spec section K). Bare `language` shows the current one. */
    private fun handleLanguage(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("rlscoreboard.language")) return denyPermission(sender)
        val locales = plugin.localeManager

        val sub = args.getOrNull(1)?.lowercase()
        when (sub) {
            null -> sender.sendMessage(prefixed(msg("language_current", "locale" to locales.activeLocale)))
            "list" -> sender.sendMessage(prefixed(msg("language_list", "locales" to locales.availableLocales().joinToString(", "))))
            "info" -> {
                val active = locales.activeLocale
                val total = locales.referenceKeys().size
                val defined = locales.keysOf(active).size
                sender.sendMessage(prefixed(msg("language_info_header")))
                sender.sendMessage(prefixed(msg("language_info_active", "locale" to active)))
                sender.sendMessage(prefixed(msg("language_info_keys", "defined" to defined.toString(), "total" to total.toString())))
            }
            "set" -> {
                if (!sender.hasPermission("rlscoreboard.language.admin")) return denyPermission(sender)
                val target = args.getOrNull(2)?.lowercase()
                if (target == null) {
                    sender.sendMessage(prefixed(msg("language_set_usage")))
                    return
                }
                if (!locales.isSupported(target)) {
                    sender.sendMessage(prefixed(msg("language_set_unsupported", "locale" to target, "locales" to locales.availableLocales().joinToString(", "))))
                    return
                }
                plugin.configManager.setLanguage(target)
                plugin.localeManager.reload()
                sender.sendMessage(prefixed(msg("language_set_success", "locale" to target)))
            }
            else -> sender.sendMessage(prefixed(msg("language_usage")))
        }
    }

    /** `/rlscoreboard validate-language <locale>` (design spec section L). Admin/developer tooling, not player-facing. */
    private fun handleValidateLanguage(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("rlscoreboard.language.admin")) return denyPermission(sender)
        val target = args.getOrNull(1)?.lowercase()
        if (target == null) {
            sender.sendMessage(prefixed(msg("validate_language_usage")))
            return
        }
        val locales = plugin.localeManager
        if (!locales.isSupported(target)) {
            sender.sendMessage(prefixed(msg("validate_language_unknown", "locale" to target, "locales" to locales.availableLocales().joinToString(", "))))
            return
        }

        val result = LocaleValidator(locales).validate(target)
        sender.sendMessage(prefixed(msg("validate_language_header", "locale" to target)))

        if (result.isClean) {
            sender.sendMessage(prefixed(msg("validate_language_clean", "locale" to target)))
        } else {
            if (result.missingKeys.isNotEmpty()) {
                sender.sendMessage(prefixed(msg("validate_language_missing_keys", "count" to result.missingKeys.size.toString(), "keys" to result.missingKeys.joinToString(", "))))
            }
            if (result.unknownKeys.isNotEmpty()) {
                sender.sendMessage(prefixed(msg("validate_language_unknown_keys", "count" to result.unknownKeys.size.toString(), "keys" to result.unknownKeys.joinToString(", "))))
            }
            if (result.brokenPlaceholderKeys.isNotEmpty()) {
                sender.sendMessage(prefixed(msg("validate_language_broken_placeholders", "count" to result.brokenPlaceholderKeys.size.toString(), "keys" to result.brokenPlaceholderKeys.joinToString(", "))))
            }
            if (result.invalidColorKeys.isNotEmpty()) {
                sender.sendMessage(prefixed(msg("validate_language_invalid_colors", "count" to result.invalidColorKeys.size.toString(), "keys" to result.invalidColorKeys.joinToString(", "))))
            }
        }
        sender.sendMessage(prefixed(msg("validate_language_summary", "total" to result.referenceKeyCount.toString())))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        when (args.size) {
            1 -> listOf("reload", "version", "debug", "status", "integrations", "board", "leaderboard", "language", "validate-language")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "board" -> listOf("list", "reload").filter { it.startsWith(args[1].lowercase()) }
                "leaderboard", "lb" -> listOf("list", "create", "delete", "setlocation", "reload", "view", "history")
                    .filter { it.startsWith(args[1].lowercase()) }
                "language", "lang" -> listOf("list", "info", "set").filter { it.startsWith(args[1].lowercase()) }
                "validate-language" -> plugin.localeManager.availableLocales().filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when {
                args[0].equals("leaderboard", true) && args[1].lowercase() in setOf("delete", "setlocation", "view", "history") ->
                    plugin.leaderboardEngine.manager.all().map { it.id }.filter { it.startsWith(args[2].lowercase()) }
                args[0].lowercase() in setOf("language", "lang") && args[1].equals("set", true) ->
                    plugin.localeManager.availableLocales().filter { it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(prefixed(msg("help_header")))
        sender.sendMessage(prefixed(msg("help_leaderboard")))
        sender.sendMessage(prefixed(msg("help_language")))
    }

    private fun denyPermission(sender: CommandSender) {
        sender.sendMessage(prefixed(msg("no_permission")))
    }

    private fun msg(key: String, vararg placeholders: Pair<String, String>): String =
        plugin.localeManager.get(key, *placeholders)

    private fun prefixed(text: String): Component = ColorUtil.toComponent(text)
}
