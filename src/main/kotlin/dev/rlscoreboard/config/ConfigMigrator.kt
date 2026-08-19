package dev.rlscoreboard.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Logger

/**
 * Config-version-aware, backup-before-migrate upgrade path for `config.yml` (design spec
 * section 23). Runs once at startup, before [ConfigManager] parses the file into the values
 * the rest of the plugin reads. Never overwrites a user's actual settings silently - every
 * migration step is meant to be additive (new keys get sane defaults; nothing existing is
 * renamed or removed without a dedicated step that says so in its own KDoc), and a full copy
 * of the pre-migration file is saved to `config.yml.v<old-version>.bak` before anything is
 * touched. `config-version` didn't exist before this - an on-disk file with no such key is
 * treated as version `0`.
 *
 * **Adding a new migration**: bump [CURRENT_CONFIG_VERSION], then add a `fromVersion` branch
 * in [migrateStep] that transforms the config from exactly that version to `fromVersion + 1`.
 * [migrateIfNeeded] chains every step in order, so a config several versions behind gets every
 * intermediate transformation applied, not just the latest one.
 */
object ConfigMigrator {

    /** Bump this whenever a migration step is added to [migrateStep]. */
    const val CURRENT_CONFIG_VERSION = 1

    fun migrateIfNeeded(configFile: File, logger: Logger) {
        if (!configFile.exists()) return // Fresh install - the bundled default already ships at CURRENT_CONFIG_VERSION, nothing to migrate.

        val yaml = runCatching { YamlConfiguration.loadConfiguration(configFile) }.getOrNull() ?: run {
            logger.warning("config.yml could not be parsed to check for a migration - leaving it untouched. If startup fails, check config.yml for a YAML syntax error.")
            return
        }

        var version = yaml.getInt("config-version", 0)
        if (version >= CURRENT_CONFIG_VERSION) return

        val backupFile = File(configFile.parentFile, "${configFile.name}.v$version.bak")
        if (!backupFile.exists()) {
            val backedUp = runCatching { configFile.copyTo(backupFile) }
            if (backedUp.isFailure) {
                logger.warning(
                    "Could not back up config.yml before migrating (${backedUp.exceptionOrNull()?.message}) - " +
                        "migration was NOT performed, to avoid changing your config without a safety copy. " +
                        "Back it up manually and restart, or fix the file permission issue and restart."
                )
                return
            }
        }

        val startedAt = version
        while (version < CURRENT_CONFIG_VERSION) {
            migrateStep(yaml, version)
            version += 1
        }
        yaml.set("config-version", CURRENT_CONFIG_VERSION)

        runCatching { yaml.save(configFile) }
            .onSuccess {
                logger.info("Migrated config.yml from version $startedAt to $CURRENT_CONFIG_VERSION. Your previous config was backed up to ${backupFile.name}.")
            }
            .onFailure {
                logger.warning(
                    "Migrated config.yml in memory but failed to save it (${it.message}) - your original settings " +
                        "are still intact in ${backupFile.name}. RLScoreboard will use its built-in defaults for " +
                        "anything the in-memory migration would have changed until this is fixed."
                )
            }
    }

    /**
     * Applies the single step that upgrades [yaml] from [fromVersion] to `fromVersion + 1`, in
     * place. Each branch should only ever add/rename/restructure keys - it must never silently
     * drop a value the user actually set without an explicit, documented reason.
     */
    private fun migrateStep(yaml: YamlConfiguration, fromVersion: Int) {
        when (fromVersion) {
            0 -> {
                // Introducing config-version itself (section 23) - every setting from before
                // this point is already in its final 0.4.x shape, so there is no structural
                // change to make here beyond the version stamp migrateIfNeeded always applies
                // once every step has run. Intentionally empty.
            }
            // else -> add "1 -> { ... }" here for the next schema change that needs one.
        }
    }
}
