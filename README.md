# RLScoreboard

Config-driven scoreboard + leaderboard framework for Paper servers. Built for Minecraft
26.2 / Paper API 26.2 / Java 25 / Kotlin.

> **Status: Phase 1 + 2 + 3, build-tested on GitHub Actions.** Every item from the original
> design spec has an implementation. One piece (a native Jobs Reborn datasource) was tried,
> broke the actual build on the first CI run, and was reverted to the safer detection-only
> approach — see [What broke on the first real build](#what-broke-on-the-first-real-build)
> for exactly what happened. Everything else compiled and resolved cleanly on the first try.

## Everything that's implemented

**Scoreboard Engine** — unlimited YAML-defined sidebar boards, animated titles, per-line
conditions *and* per-line animation, placeholders, priority + fallback selection, one board
per player, diffed rendering (no packet spam on unchanged frames).

**Leaderboard Engine** — YAML-defined leaderboards, a pluggable `LeaderboardDataSource` API,
and **five** renderers:

| Type      | What it is                                                                 |
|-----------|-----------------------------------------------------------------------------|
| `SIDEBAR` | Published as a normal board, competes on priority/conditions like any other |
| `HOLOGRAM`| `TextDisplay` entity stack at a world location                              |
| `TAB`     | Player-list header/footer, whole-server (see its doc comment for the "only one at a time" caveat) |
| `NPC`     | AI-disabled `Villager` at a location, #1 entry as its nameplate, rest as a floating `TextDisplay` stack above it |
| `GUI`     | On-demand snapshot, opened with `/rlscoreboard leaderboard view <id>`       |

Built-in datasources, none requiring any other plugin except where noted:

| id                      | Ranks by                                   | Needs           |
|--------------------------|---------------------------------------------|------------------|
| `topkills`/`topdeaths`/`topplaytime` | vanilla `Statistic`, online players only | nothing |
| `economy`                | Vault balance, online players only          | Vault + an economy plugin |
| `topkills_alltime`/`topdeaths_alltime`/`topplaytime_alltime`/`economy_alltime` | same stats, *every* player who's ever played | `storage.enabled: true` |
| `auraskills_powerlevel`   | AuraSkills power level (sum of all skills)  | AuraSkills |

`jobs_totallevel` was tried and reverted - see below. `%jobs_*%` still works via
PlaceholderAPI passthrough if both PlaceholderAPI and Jobs are installed, just not as a
directly-sortable datasource.

**Storage** (section 18) — a small hand-rolled JDBC connection pool (`storage/sql/Database`,
not HikariCP — see [below](#why-not-hikaricp)), two tables
(`rlscoreboard_player_stats`, `rlscoreboard_leaderboard_history`), SQLite bundled and
zero-config by default, MySQL/MariaDB bundled too (same code path, just set
`storage.type: mysql`). `StatsSyncService` keeps player stats fresh; `LeaderboardHistoryService`
periodically snapshots every leaderboard's ranking so `/rlscoreboard leaderboard history <id>
[hours-ago]` can answer "what did this look like earlier". Off by default —
`storage.enabled: true` in `config.yml`.

**Condition engine** — world / permission / gamemode / health / online-count / placeholder
comparisons, plus a `custom` hook for other plugins. **Placeholder engine** — internal
`%rl_*%` placeholders with zero dependencies, PlaceholderAPI as a two-way bridge.
**Integrations** — PlaceholderAPI, Vault, LuckPerms, and AuraSkills registered with real,
compiled-against APIs; Jobs Reborn is detection-only (see
[What broke on the first real build](#what-broke-on-the-first-real-build)).

**Commands** — `/rlscoreboard reload|version|debug`, `/rlscoreboard board <list|reload>`,
`/rlscoreboard leaderboard <list|create|delete|setlocation|reload|view|history>`. `list`,
`view`, and `history` need no special permission (open to any player); the rest need
`rlscoreboard.reload` / `rlscoreboard.leaderboard.manage` as appropriate.

**Public API** — `RLScoreboardAPI.get()` for other plugins to register placeholders,
conditions, data sources, and leaderboard renderers, and to read/force boards.

**CI** — GitHub Actions build on every push (`.github/workflows/build.yml`).

## Building

```bash
./gradlew build
```

Output jar: `build/libs/RLScoreboard-0.1.0.jar`. (No Gradle wrapper jar is bundled in this
delivery since this environment has no network access to download it — run `gradle wrapper
--gradle-version 9.5.1` once locally, or open the project in IntelliJ and let it generate one,
then commit `gradle/wrapper/`.)

Requires JDK 25 to compile (matches the `paper-api:26.2.build.+` target). **This is the
first thing to run** — see [Residual risk](#residual-risk--worth-checking-first) below for
exactly which two files to look at first if it fails.

## Project layout

```
api/            Public interfaces (RLScoreboardAPI, BoardAPI, LeaderboardAPI,
                LeaderboardDataSource, LeaderboardRenderer, PlaceholderProvider,
                ConditionProvider) + api/model (pure data classes) + api/internal (impl)
core/           ScoreboardEngine, BoardManager, BoardRenderer, LineRenderer,
                UpdateManager, PlayerSessionManager, CacheManager
leaderboard/    LeaderboardEngine, LeaderboardManager, RankingEngine, DataSourceManager
  /datasource   StatisticDataSource, EconomyDataSource, AsyncRefreshingDataSource,
                PersistentStatDataSource, AuraSkillsPowerLevelDataSource
  /renderer     SidebarLeaderboardRenderer, HologramLeaderboardRenderer,
                TabLeaderboardRenderer, NpcLeaderboardRenderer, GuiLeaderboardRenderer
config/         ConfigManager, BoardConfigLoader, LeaderboardConfigLoader, ConfigValidator
placeholder/    PlaceholderEngine, PlaceholderParser, InternalPlaceholders
condition/      Condition, ConditionParser, ConditionEngine
animation/      AnimationEngine
integration/    IntegrationManager + placeholderapi/, vault/, luckperms/, jobs/, auraskills/
command/        RLScoreboardCommand
listener/       PlayerConnectionListener, GuiClickListener
storage/        StorageProvider, InMemoryStorage, StatsSyncService, LeaderboardHistoryService
  /sql          Database, DatabaseType, PlayerStatsRepository, LeaderboardHistoryRepository
util/           ColorUtil
```

## What broke on the first real build

The very first `gradle build` (via the GitHub Actions workflow) failed at dependency
resolution, in exactly the two spots flagged as risky beforehand:

1. **`com.github.MilkBowl:VaultAPI:1.7`'s published POM has a hard, non-optional
   transitive dependency on `org.bukkit:bukkit:1.13.1-R0.1-SNAPSHOT`**, which conflicts
   with `paper-api` over the same Bukkit "capability" and fails resolution outright. Fixed
   by excluding that transitive dependency - `RLScoreboard` only ever calls VaultAPI's own
   `Economy` interface, never anything from that old bukkit artifact, so the exclusion is
   safe. This is a well-known VaultAPI issue, not specific to this project.
2. **`com.github.Zrips:Jobs:5.2.6.3`'s JitPack-published POM pulls in hard, non-optional
   dependencies on mcMMO, WorldGuard, WorldEdit, WildStackerAPI, and StackMob** - Jobs
   Reborn's own *optional* soft-integrations, apparently published as required dependencies
   in the JitPack build rather than `compileOnly`/`provided`. None of them resolve from any
   repository configured in this project (they're on their own separate repos this project
   never had reason to add). This is exactly the risk flagged in the previous version of
   this README before a real build had been attempted.

**Fix for #2**: rather than chase down five more Maven repositories for dependencies
RLScoreboard doesn't actually need, `JobsTotalLevelDataSource` and the
`com.github.Zrips:Jobs` dependency were removed entirely. `JobsIntegration` is back to
detection-only (matching `AuraSkillsIntegration`'s original Phase 1 shape) - Jobs is still
detected and logged correctly at startup, and `%jobs_*%` placeholders still work anywhere
via PlaceholderAPI passthrough, just not as a directly-sortable `LeaderboardDataSource`
anymore. If you want to revisit a native Jobs datasource later, the two options are tracking
down and adding all five of those repositories, or asking Jobs Reborn's maintainers whether
a lighter API-only artifact exists.

**AuraSkills, LuckPerms, PlaceholderAPI, SQLite, MySQL, Paper API, and Kotlin itself all
resolved cleanly on the first try** - no changes needed to any of them.

## What broke on the second real build

Dependency resolution passed; compilation failed with a wall of `Unresolved reference`
errors across many files, plus one real error: `core/BoardManager.kt:69:1 Syntax error:
Unclosed comment`. The cause: Kotlin's `/* */` block comments **nest** (unlike Java's), and
`BoardManager`'s class-level KDoc comment contained the literal text `scoreboards/*.yml`.
The `/*` in that path was parsed as *opening a second, nested* comment; the doc comment's
closing `*/` then only closed that inner nested comment, leaving the outer `/**` open until
end-of-file. Everything from that KDoc comment through the end of the file - the entire
`BoardManager` class - was silently swallowed into "comment", so the compiler genuinely
didn't know the class existed, which is what produced every one of the `Unresolved
reference 'BoardManager'` (and its knock-on `reload`/`resolveBoardFor`/`registerSynthetic`/
etc.) errors elsewhere. Fixed by rewording that one sentence to avoid the literal `/*`
sequence; every other `/*` in the codebase was audited and is a normal, non-nested comment.
A separate, harmless compiler warning (`-Xjvm-default is deprecated`) was also fixed by
switching to the current `-jvm-default` flag name.


## Architecture decisions

1. **`DataSourceAPI`/`ProviderAPI` merged into `RLScoreboardAPI`.** One facade interface
   (`registerPlaceholder`, `registerConditionProvider`, `registerDataSource`,
   `registerLeaderboardRenderer`) is a smaller surface for third-party plugins to learn,
   with no loss of modularity underneath.
2. **`AnimationManager`/`AnimatedFrames` merged into `AnimationEngine`.** Frame selection is
   a pure function of elapsed time, so there's no per-line/per-player *state* to manage —
   `AnimationEngine` is stateless and needs no scheduler of its own.
3. **SIDEBAR-type leaderboards are published as synthetic boards** into the same
   `BoardManager` YAML scoreboards use (`BoardManager.registerSynthetic`), rather than
   RLScoreboard running a second, parallel sidebar system. A player only ever has one active
   Bukkit scoreboard, so this also resolves what would otherwise be a real conflict between
   "your normal board" and "a sidebar leaderboard".
4. **`Database` is a small hand-rolled connection pool, not HikariCP.** A real pooling
   library means shading + relocating another third-party dependency for a plugin whose DB
   load is occasional upserts and periodic top-N reads, not high-concurrency OLTP traffic.
   See [Why not HikariCP](#why-not-hikaricp).
5. **SQLite/MySQL JDBC drivers are bundled but *not* relocated** in `shadowJar`. Relocating
   a JDBC driver correctly requires either rewriting the `META-INF/services/java.sql.Driver`
   SPI file to match the relocated class name, or not relying on SPI auto-registration at
   all — easy to get subtly wrong without a build to test against. `Database` calls
   `DriverManager.getConnection(url)` with no explicit `Class.forName`, relying on standard
   JDBC 4+ SPI auto-loading, and both driver packages are left unrelocated. Tradeoff: a
   small, rarely-hit risk of classpath collision if another plugin bundles a very different
   version of one of these drivers, versus a real risk of silently broken driver loading
   from a relocation I can't verify compiles and runs. `mysql-connector-j`'s optional
   `protobuf-java` dependency (only needed for its X DevAPI document-store mode, not plain
   JDBC) is excluded to keep the jar leaner.
6. **NPC leaderboards are a plain `Villager`, not a fake-player/skin NPC.** A true
   human-looking NPC needs either raw packet manipulation or a Citizens dependency; this
   plugin adds neither. An AI-disabled, invulnerable, silent mob entity needs nothing beyond
   stock Paper API and gets the "leaderboard standing at a location" effect without either.
7. **GUI leaderboards are on-demand, not ambient.** Unlike the other four renderer types,
   `GuiLeaderboardRenderer.render()` just refreshes an in-memory cached snapshot; nothing is
   visible until a player runs `/rlscoreboard leaderboard view <id>`, which builds the
   inventory from that snapshot. This is the natural fit for a GUI (nobody's screen should
   be hijacked ambiently) and means a GUI leaderboard can safely ship `enabled: true` by
   default with zero visible side effects.
8. **Leaderboard history reads `RankingEngine`'s existing cache (`peek`), never a datasource
   directly.** `LeaderboardHistoryService` adds zero datasource load beyond what each
   leaderboard's own `update.interval` already costs.

## Why not HikariCP

RLScoreboard's actual database load is occasional upserts (one per online player every
`storage.sync-interval-seconds`) and periodic top-N reads (one per "*_alltime" leaderboard
or history snapshot per their own intervals) — not concurrent high-throughput OLTP traffic.
A real pooling library adds a shaded dependency (plus, typically, `slf4j-api` as a
transitive dependency HikariCP needs to compile against) that has to be relocated correctly
to avoid classpath collisions with other plugins that might bundle a different HikariCP
version — another thing I can't verify without a build. The hand-rolled pool in
`storage/sql/Database` (`ArrayBlockingQueue<Connection>`, borrow/use/return, dead-connection
replacement) covers the actual concurrency this plugin needs using only `java.sql` and
`java.util.concurrent`, both already guaranteed present. SQLite's pool size is clamped to 1
regardless of `storage.pool-size` (SQLite serializes writers no matter how many connections
are open, so more than 1 buys nothing); MySQL/MariaDB honors the configured size.

## Storage & offline-inclusive leaderboards

Off by default (`storage.enabled: false`). Turning it on:

1. Connects a pooled `Database` (SQLite: 1 connection, zero-config, a file under the
   plugin's data folder; MySQL/MariaDB: `storage.pool-size` connections to your configured
   database) and creates two tables if they don't exist.
2. Starts `StatsSyncService`, which every `storage.sync-interval-seconds` snapshots online
   players' kills/deaths/playtime/(economy balance if Vault is present) on the **main
   thread** (required — Bukkit/Vault APIs aren't thread-safe) and writes them via an
   **async** task. Also syncs immediately on `PlayerQuitEvent`.
3. Registers `topkills_alltime`/`topdeaths_alltime`/`topplaytime_alltime`/`economy_alltime` —
   each an `AsyncRefreshingDataSource` refreshing its own cached top-100 from storage every
   `storage.refresh-interval-seconds` on an async task, so a DB query never happens on the
   main-thread heartbeat. Use them in a leaderboard YAML like any other datasource — see
   `leaderboards/topkills_alltime.yml` (ships `enabled: false`).
4. Starts `LeaderboardHistoryService`, which every `storage.history-interval-seconds`
   (default hourly) records every leaderboard's current top-N with a timestamp, queryable
   via `/rlscoreboard leaderboard history <id> [hours-ago]`.

**MySQL/MariaDB**: set `storage.type: mysql` and fill in `storage.mysql.*` — the driver
ships bundled, no extra setup needed.

## Leaderboard display types

`SIDEBAR` and `HOLOGRAM` are covered in the main table above. Notes on the rest:

- **`TAB`** writes to every online player's tab-list footer. There's only one footer per
  player, so enabling more than one `TAB`-type leaderboard means whichever renders last on
  a given tick wins — keep it to at most one per server. See
  `leaderboards/examples_phase3.yml`'s `online_tab` entry.
- **`NPC`** needs a `location:`, set the same way as `HOLOGRAM` (`/rlscoreboard leaderboard
  setlocation <id>`). See `richest_npc` in the same example file.
- **`GUI`** doesn't need a location and isn't ambient — see architecture decision 7. Any
  player can open one with `/rlscoreboard leaderboard view <id>`; see `kills_gui` in the
  same example file, which ships `enabled: true` since it's invisible until requested.

## Example configs

`src/main/resources/scoreboards/{survival,lobby}.yml` (including a per-line condition
example in `survival.yml`), `src/main/resources/leaderboards/{topkills,richest,
topkills_alltime,examples_phase3}.yml` — these ship as the plugin's defaults and double as
the best usage reference for every feature above.
