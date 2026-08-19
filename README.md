# RLScoreboard

Config-driven scoreboard + leaderboard framework for Paper servers. Built for Minecraft
26.2 / Paper API 26.2 / Java 25 / Kotlin.

> **Status: v0.5.0-dev — Phases 1–4 + Integrations/Localization addendum + Public Release
> Upgrade Phases 1–5, build-tested on GitHub Actions through 0.3.0 only.** A "Complete Audit,
> Refactor & Public Release Upgrade" pass toward 1.0.0 is under way - see
> [ROADMAP.md](ROADMAP.md) for the authoritative, continuously-updated done-vs-open list.
> **Done so far**: real numeric-token version comparison, a six-state `IntegrationStatus`,
> per-capability partial-failure isolation, `/rlscoreboard status`, startup config validation,
> `config-version` + backup-before-migrate, a researched JAR-size trim, research-backed
> integration placeholders (AuraSkills per-skill, Jobs Reborn via PAPI-passthrough, real
> Geyser/Floodgate platform placeholders), MiniMessage color support (restricted to visual
> tags only - see [docs/ANIMATIONS.md](docs/ANIMATIONS.md)), eight procedurally-generated
> animation presets, and - new this phase - a real JUnit5 test suite (51 tests covering every
> pure-logic class: version comparison, animation preset generation, config validation).
> **Also this phase**: WorldGuard/WorldEdit region conditions were researched, not
> implemented - a documented, real precedent for the exact transitive-dependency breakage
> this project already hit once with Jobs Reborn was found
> ([EngineHub/WorldGuard#1874](https://github.com/EngineHub/WorldGuard/issues/1874)), and
> with no way to compile-verify a mitigation here, the conservative call was made not to add
> it blind - see ROADMAP.md. Everything else from the upgrade pass (that WorldGuard/WorldEdit
> work, and tests for anything needing a live server - renderers/engines/commands) is **not
> yet implemented** - tracked in ROADMAP.md, not silently skipped. One earlier piece (a
> native Jobs Reborn datasource) was tried, broke the actual build on the first CI run, and
> was reverted to the safer detection-only/PAPI-passthrough approach — see
> [What broke on the first real build](#what-broke-on-the-first-real-build) for exactly what
> happened. Everything written across every pass so far was developed in an environment with
> **no network access to a compiler and no Kotlin/Gradle/JDK toolchain at all** (web search
> for reference documentation was available and used throughout - confirming method/tag
> signatures against official docs before writing code that calls them, including
> the MiniMessage tag-restriction design this phase - but nothing here has actually been
> compiled) - see [Building](#building) for what that means in practice and what still needs a
> real CI run before it's trusted the way the rest of this README is.

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
`%rl_*%` placeholders with zero dependencies (including `%rl_platform%`/`%rl_is_bedrock%`/
`%rl_is_java%` - see [docs/INTEGRATIONS.md#floodgate](docs/INTEGRATIONS.md#floodgate)),
PlaceholderAPI as a two-way bridge.
**Integrations** — a first-class, version-aware framework (detect → check tested-version
range → enable or disable safely, never crash) with a live capability system
(`hasCapability("economy")`, etc. — see [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md#capability-system)
for the "Vault installed but no economy plugin" example this enables). PlaceholderAPI, Vault,
LuckPerms, and AuraSkills are registered with real, compiled-against APIs; Jobs Reborn adds
`%rl_job_*%` via a research-backed PlaceholderAPI-passthrough layer (no compiled Jobs
dependency - see [docs/INTEGRATIONS.md#jobs-reborn](docs/INTEGRATIONS.md#jobs-reborn));
Geyser/Floodgate/WorldGuard/WorldEdit remain detection-only (real version-aware compatibility
checking, no deeper capability wiring yet) — see [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md)
for the full compatibility matrix, per-plugin notes, and exactly what "detection-only" does
and doesn't mean.

**Commands** — `/rlscoreboard reload|version|debug|status|integrations`, `/rlscoreboard board
<list|reload>`, `/rlscoreboard leaderboard <list|create|delete|setlocation|reload|view|history>`,
`/rlscoreboard language <list|info|set>`, `/rlscoreboard validate-language <locale>`. `list`,
`view`, and `history` need no special permission (open to any player); the rest need
`rlscoreboard.reload` / `rlscoreboard.leaderboard.manage` as appropriate. `integrations` and
`debug` need `rlscoreboard.debug`; `language`/`language list`/`language info` are open to any
player (`rlscoreboard.language`, default true — read-only), `language set` and
`validate-language` need `rlscoreboard.language.admin`.

**Public API** — `RLScoreboardAPI.get()` for other plugins to register placeholders,
conditions, data sources, and leaderboard renderers, and to read/force boards.

**CI** — GitHub Actions build on every push (`.github/workflows/build.yml`).

## Colors & animations

See **[docs/ANIMATIONS.md](docs/ANIMATIONS.md)** for the full color/MiniMessage rules (and
exactly which tags are enabled and why) and the eight named animation presets
(`static`/`fade`/`color`/`gradient`/`typing`/`scrolling`/`pulse`/`wave`) - `preset: gradient`
in a line/title's `animation:` block instead of hand-writing a `frames:` list.

## Integrations

See **[docs/INTEGRATIONS.md](docs/INTEGRATIONS.md)** for the full compatibility matrix,
per-plugin capability notes, the startup log format, and how to report a compatibility
problem. Short version: `/rlscoreboard integrations` is always the live, accurate answer for
your own server; nothing is documented as "Supported" without a real capability behind it.

## Localization

English (`en`) is the default and its wording/behavior is unchanged. Vietnamese (`vi`) and
Japanese (`ja`) are fully translated. See **[docs/TRANSLATIONS.md](docs/TRANSLATIONS.md)** for
how to create, validate, and submit a new one — adding a language is a config-folder change,
never a plugin code change.

- **Config**: `language: en` in `config.yml` (commented with the supported codes), or
  `/rlscoreboard language set <locale>` to switch and reload immediately (permission
  `rlscoreboard.language.admin`). An unrecognised value falls back to `en` with a warning
  logged - it never crashes and never leaves a blank/`null` message on screen.
- **Commands**: `/rlscoreboard language` (current), `language list` (every locale currently
  loaded - bundled + any community file dropped into `locales/`), `language info` (active
  locale + how many of the reference keys it defines directly), `language set <locale>`, and
  `/rlscoreboard validate-language <locale>` (missing/unknown keys, placeholder mismatches,
  invalid color codes against the English reference - see
  [docs/TRANSLATIONS.md#validation](docs/TRANSLATIONS.md#validation)).
- **Files**: `locales/en.yml`, `locales/vi.yml`, `locales/ja.yml` under the plugin's data
  folder (bundled in the jar, extracted on first run - same mechanism already used for
  `messages.yml` and the default `scoreboards/`/`leaderboards/` files). 55 keys, one per
  distinct player/admin-facing string in the plugin - see
  [`LocaleManager`](src/main/kotlin/dev/rlscoreboard/config/LocaleManager.kt).
- **No hard-coded language list**: `/rlscoreboard language list` reflects whatever
  `locales/*.yml` files actually exist on disk, discovered at load time - a community
  translation (`ko.yml`, `zh_cn.yml`, whatever) dropped straight into that folder works
  immediately after `/rlscoreboard reload`, no plugin update required. Only the *bundled*
  set (en/vi/ja, shipped inside the jar) is a fixed list in code, since that's a packaging
  decision, not a language-support ceiling. A third-party addon plugin can also register its
  own locale content programmatically via `LocaleManager.registerLocale(code, reader)`.
- **Fallback chain**: active language → `en` → a visible placeholder (`&c[missing locale
  key: ...]`) if a key is somehow missing from both. A key present in `en.yml` but absent from
  another locale silently falls back to the English line rather than showing blank text. With
  `debug: true` in `config.yml`, each missing locale/key combination is logged the first time
  it's hit.
- **Scope, deliberately**: only plugin-generated chat/GUI text is localized. Command names
  (`/rlscoreboard board`, `leaderboard setlocation`, etc.), permission nodes, placeholders
  (`%rl_*%`), and the *content* of your own `scoreboards/*.yml` / `leaderboards/*.yml` files
  are untouched - those are your configuration, not the plugin's UI strings, and translating
  them automatically would silently rewrite server-specific setup.
- **Upgrading from 0.1.0**: if you had customised `no-permission` or `reloaded` in the old
  flat `messages.yml`, those values are copied into the new `locales/en.yml` automatically the
  first time the plugin loads after upgrading - one-time only, so any edits you make to
  `locales/en.yml` afterwards are never overwritten again. `messages.yml` itself is left in
  place untouched (nothing deletes it).
- **Reload**: `/rlscoreboard reload` reloads locale files too, alongside config, scoreboards,
  and leaderboards - it's the same reload path, just extended (no second reload system was
  added).

## Sidebar UI

**Score numbers are hidden on every sidebar (regular boards and SIDEBAR-type
leaderboards).** Minecraft's vanilla sidebar always had a number on the right of each line;
that's gone now, while the underlying score is still set on every line exactly as before -
it's the only thing that tells the client what order to display the lines in, so it can't be
removed, only hidden from rendering.

- **Mechanism**: `Objective.numberFormat(NumberFormat.blank())` -
  `io.papermc.paper.scoreboard.numbers.NumberFormat`, part of `paper-api` itself (no new
  dependency). Confirmed present against this project's exact target
  (`paper-api:26.2.build.+`) via PaperMC's own javadoc before writing this - not NMS, not a
  packet hack, not spaces/fake characters padding the text. Set once per objective, at
  creation time - not per tick, per line, or per score, so it adds zero packet overhead
  beyond what was already being sent.
- **Where**: [`BoardRenderer.kt`](src/main/kotlin/dev/rlscoreboard/core/BoardRenderer.kt) is
  the single place that creates the sidebar objective. `SidebarLeaderboardRenderer` (leaderboard
  SIDEBAR type) publishes through the same `BoardManager` → `BoardRenderer` path, so it gets
  the same fix automatically - there's no second sidebar code path that could've been missed.
- **Ordering**: unaffected. `objective.getScore(entry).score = size - index` is untouched.

**Default boards redesigned** (`scoreboards/survival.yml`, `scoreboards/lobby.yml`): one
accent colour for the header, muted grey labels with white values, blank-line grouping
instead of extra separators. Only placeholders that are always registered
(`rl_player_name`/`rl_online`/`rl_max_players`/`rl_world`/`rl_ping`) are used live, so a fresh
install never shows a raw `%rl_balance%`/`%rl_rank%` before Vault/LuckPerms are installed -
those two are included as commented-out lines to uncomment once you have them. Existing
animation and per-line condition examples are preserved, not removed.

**Leaderboard rendering** (all 5 types): a `leaderboard_empty` locale key now shows a
localized "no data yet" line/item instead of a bare title when a leaderboard has zero ranked
entries, consistently across SIDEBAR/HOLOGRAM/TAB/NPC/GUI. The GUI type additionally got a
shared default medal icon (🥇🥈🥉, extracted into
[`DefaultRankIcon`](src/main/kotlin/dev/rlscoreboard/leaderboard/renderer/DefaultRankIcon.kt)
and reused by every renderer instead of duplicating the same fallback in five places), bold
names for the top 3, and a footer item showing how many players are ranked. A leaderboard's
own `topIcons` config always overrides these defaults, unchanged from before.

## Building

```bash
./gradlew build       # compiles + runs the test suite (51 tests as of v0.5.0) + shades the jar
./gradlew test        # just the test suite - see ROADMAP.md for what is/isn't covered yet
```

Output jar: `build/libs/RLScoreboard-<version>.jar` (`0.3.0` was the last CI-verified build;
see the version bump note below for everything built on top of it since then).
`gradle/wrapper/gradle-wrapper.jar` + `gradlew`/`gradlew.bat` are committed (pinned to Gradle
9.5.1 via `gradle-wrapper.properties`, matching CI) — sourced directly from the
`gradle/gradle` GitHub repo rather than hand-written, since the wrapper jar is a binary and
the scripts are easy to get subtly wrong by hand. **Not executed end-to-end in the
environment this was prepared in** (no network path to `services.gradle.org` there) — GitHub
Actions is the first real test of it; if `./gradlew build` doesn't bootstrap cleanly, fall
back to a system-installed Gradle 9.5.1 (`gradle build`) and let us know.

Requires JDK 25 to compile (matches the `paper-api:26.2.build.+` target). **This is the
first thing to run** — see [What broke on the first real build](#what-broke-on-the-first-real-build)
below for exactly which two dependency issues to expect and how they were already fixed.

> **Everything since 0.3.0 — not yet CI-verified.** The integrations+localization addendum,
> and every Public Release Upgrade phase (1 through 5: the version-compatibility rework,
> config validation/migration, JAR-size trim, research-backed integration placeholders,
> MiniMessage/animation presets, and the new test suite itself) were written and reasoned
> through carefully, but **not compiled in this environment** - it has no network access, so
> `./gradlew build` couldn't be run to confirm any of it even compiles, let alone passes its
> own new tests or behaves correctly against real installs of the soft-dependency plugins
> involved. Compile-time risk was kept deliberately low throughout - detection-only
> integrations that need no new Maven dependency, a WorldGuard/WorldEdit dependency
> specifically *not* added after finding a real precedent for exactly the kind of breakage
> that would cause (see ROADMAP.md), first-party Kotlin tooling for the test suite rather than
> a third-party test framework - but "low risk" is not "verified". **Before trusting this the
> way the rest of this README is written, please run `./gradlew build` (which now also runs
> the test suite) or push to the existing GitHub Actions workflow, and treat any compiler
> error or failing test the same way the two real issues in
> [What broke on the first real build](#what-broke-on-the-first-real-build) were treated** -
> fix, document what broke and why, don't silently patch around it. The version ranges in
> docs/INTEGRATIONS.md for Geyser/Floodgate/WorldGuard/WorldEdit are similarly provisional
> until checked against a live server.

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
config/         ConfigManager, LocaleManager, BoardConfigLoader, LeaderboardConfigLoader, ConfigValidator
placeholder/    PlaceholderEngine, PlaceholderParser, InternalPlaceholders
condition/       Condition, ConditionParser, ConditionEngine
animation/       AnimationEngine
integration/     Integration, IntegrationStatus, IntegrationManager, AbstractIntegration,
                 util/VersionRange + placeholderapi/, vault/, luckperms/, jobs/, auraskills/,
                 geyser/, floodgate/, worldguard/, worldedit/ (see docs/INTEGRATIONS.md)
command/         RLScoreboardCommand
listener/        PlayerConnectionListener, GuiClickListener
storage/         StorageProvider, InMemoryStorage, StatsSyncService, LeaderboardHistoryService
  /sql           Database, DatabaseType, PlayerStatsRepository, LeaderboardHistoryRepository
util/            ColorUtil
```

`docs/` — [INTEGRATIONS.md](docs/INTEGRATIONS.md) (compatibility matrix, capability system,
per-plugin notes) and [TRANSLATIONS.md](docs/TRANSLATIONS.md) (how to add/validate/submit a
locale) live alongside this README.

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

> **Re-checked during Phase 4 (localization).** The original spec for this phase asked for
> native `%rl_jobs_primary%`/`%rl_jobs_level%`/`%rl_jobs_exp%` placeholders. A web search
> confirmed `com.github.Zrips:Jobs` is still only published via that same JitPack
> coordinate/POM - no lighter Maven Central artifact exists - so re-adding it would very
> likely reproduce the exact break documented above. A reflection-based integration (no
> compile-time dependency, so no POM risk) was considered instead, but Jobs Reborn's public
> API wiki doesn't document exact getter signatures for job name/level/experience, and
> shipping reflective calls against guessed method names is worse than not shipping them - it
> "compiles" but may silently do nothing or throw at runtime. **Decision: left
> `JobsIntegration` as detection-only, unchanged.** If native Jobs data is still wanted, the
> reliable path is providing the actual Jobs Reborn jar for this server's installed version so
> the reflective calls can be verified against real method signatures rather than guessed.

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

## What broke on the third real build

Dependency resolution and every other file compiled cleanly - one real, narrow error, from
GitHub Actions itself:

```
e: .../AuraSkillsIntegration.kt:69:29 Cannot infer type for type parameter 'T'.
e: .../AuraSkillsIntegration.kt:69:29 Cannot infer type for type parameter 'R'.
e: .../AuraSkillsIntegration.kt:69:29 Unresolved reference. None of the following candidates
   is applicable because of a receiver type mismatch:
   fun <T, R> DeepRecursiveFunction<T, R>.invoke(value: T): R
```

The cause: `skill.name()`, where `skill` is AuraSkills' `Skills` enum (a `java.lang.Enum`
subtype that also implements the `Skill` interface, which separately declares `String
name()`). In Java, this is fine - `Enum.name()` already satisfies that interface method.
**Kotlin gives every `java.lang.Enum` subtype special treatment**: `Enum.name()`/
`Enum.ordinal()` are exposed to Kotlin as the properties `.name`/`.ordinal`, not as callable
methods - regardless of whether some other interface the enum implements *also* declares a
same-named method. Writing `skill.name()` therefore doesn't call `Skill.name()` as intended;
Kotlin resolves `skill.name` to the property (a `String`) first, then tries to interpret the
trailing `()` as *invoking that String value as if it were a function* (Kotlin's `x()` sugar
for `x.invoke()`) - which obviously fails, and the compiler's actual error message ends up
pointing at an unrelated, unhelpful `invoke` candidate it found in scope
(`DeepRecursiveFunction`) instead of describing the real problem. Fixed by writing `skill.name`
(no parentheses) - for the `Skills` enum specifically, that returns the exact same value the
`Skill.name()` interface method's own javadoc promises, since each enum constant is already
named to match (`Skills.FARMING` → `"FARMING"`).

This is the same category of "not checkable without a real Kotlin compiler" issue as the
nested-comment bug above, but a different mechanism - a Kotlin/Java interop rule about `Enum`
specifically, not a lexer/parsing quirk. The rest of the codebase was audited for the same
`.name()`/`.ordinal()` pattern on any other externally-defined enum (LuckPerms, Bukkit's own
`GameMode`, etc.) and no other instance was found - every other `.name` usage in this codebase
was already written correctly (as a property, no parentheses) from the start.


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
