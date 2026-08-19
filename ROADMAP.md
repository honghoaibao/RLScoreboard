# RLScoreboard — Public Release Upgrade Roadmap

Tracks the "Complete Audit, Refactor & Public Release Upgrade" design spec (target: 0.5.x /
1.0.0) against what's actually implemented, section by section. Updated as each phase lands -
this is the authoritative "what's done vs still open" reference, not the chat log it came
from.

**Environment constraint that applies to every phase below:** this project has been developed
in a sandbox with **no Kotlin/Gradle/JDK toolchain and no network access** - nothing here has
been compiled, let alone build-tested, by the assistant. Every phase is verified as far as
possible without a compiler (brace/paren balance, the exact nested-comment bug class described
in [README "What broke on the first real build"](README.md#what-broke-on-the-first-real-build),
YAML validity, and - for pure-logic pieces like version comparison - a Python port tested
against real edge cases before the Kotlin is written), but **none of it is confirmed to
compile until a real `./gradlew build` run** (GitHub Actions, or a local machine with the
toolchain) succeeds. Treat every "✅ Done" below as "implemented and reasoned through
carefully," not "compiled and verified."

Legend: ✅ Done &nbsp;·&nbsp; 🟡 Partial / in progress &nbsp;·&nbsp; ⬜ Not started

## Phase 1 — Version compatibility system (done)

| # | Section | Status | Notes |
|---|---|---|---|
| 3 | Version format handling | ✅ | [`VersionRange`](src/main/kotlin/dev/rlscoreboard/integration/util/VersionRange.kt) parses dot-separated numeric release tokens + free-form suffix; handles `2.11.1-SNAPSHOT`, `5.2.6.6`, `1.7.3-b131`, `26.2-111`, unparseable strings. |
| 4 | Version compatibility system rework | ✅ | Six-state [`IntegrationStatus`](src/main/kotlin/dev/rlscoreboard/integration/IntegrationStatus.kt) (`SUPPORTED`/`PARTIALLY_SUPPORTED`/`DETECTED_UNTESTED`/`INCOMPATIBLE`/`NOT_INSTALLED`/`ERROR`) with icons matching the spec's ✅⚠️❌○. "Below tested minimum" and "above tested maximum" are no longer conflated - see [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md#status-model). |
| 5 | Integration lifecycle | ✅ | detect → range-check → `enable()` → (future) `disable()`/reload, all in [`IntegrationManager`](src/main/kotlin/dev/rlscoreboard/integration/IntegrationManager.kt). No crash path - every failure degrades to a status + logged reason. |
| 5 | Per-capability partial failure | ✅ | `Integration.enable()` now returns `Set<String>` (which capabilities actually wired); a capability-by-capability `runCatching` (see [`LuckPermsIntegration`](src/main/kotlin/dev/rlscoreboard/integration/luckperms/LuckPermsIntegration.kt)) lets one capability fail without taking the whole integration down → `PARTIALLY_SUPPORTED`. |
| 10 | LuckPerms suffix | ✅ | `%rl_suffix%` added alongside existing rank/prefix. |
| 24/25 | `/rlscoreboard status` | ✅ | New command: version, platform (Paper + Java), player count, active-vs-configured board count, integration summary. |

## Phase 2 — Config safety, versioning, JAR size (done)

| # | Section | Status | Notes |
|---|---|---|---|
| 22 | Config validation on startup | 🟡 | [`ConfigValidator.validateInstallation`](src/main/kotlin/dev/rlscoreboard/config/ConfigValidator.kt) recursively scans `config.yml` + every board/leaderboard file for invalid color codes and unrecognised condition `operator:` values (a typo there previously failed *silently* - `Operator.parse` already defaulted to `EQUALS`, just with no warning). Runs on startup and on `/rlscoreboard reload`; never blocks either. **Not yet covered**: missing required fields, invalid MiniMessage (doesn't exist yet - see section 15 below), invalid animation config, invalid `language` cross-check surfaced in this same report (today it's a separate warning logged by `LocaleManager` itself, not merged into this one) - marked 🟡 partial rather than ✅ for that reason. |
| 23 | `config.yml` version + migration + `.bak` backup | ✅ | New [`ConfigMigrator`](src/main/kotlin/dev/rlscoreboard/config/ConfigMigrator.kt): `config-version` key, backs up to `config.yml.v<old>.bak` before migrating, chains migration steps in order. First step (0→1) is a no-op beyond the version stamp, since it's introducing the system itself, not changing a schema. |
| 27 | JAR size / native dependency audit | ✅ (conservative) | Researched `sqlite-jdbc`'s actual native-library packaging (confirmed via its own docs/issue tracker - see `build.gradle.kts`) and excluded the confirmed-safe-to-drop architectures (32-bit ARM Linux, FreeBSD) from the shaded jar. Deliberately did **not** drop Linux-Musl (Alpine/Docker is a real hosting pattern) or any x86_64/aarch64 variant. Actual before/after size is unmeasured - this environment cannot run a real build; see the note in `build.gradle.kts` for exactly what to verify once one runs. |

Also fixed while working on this phase (not part of the original section list, but real bugs caught by this project's own verification habits): two more instances of the exact nested-`/*`-comment bug described in [README "What broke on the first real build"](README.md#what-broke-on-the-first-real-build) — this is now the second session in a row this exact bug class has been caught before shipping, purely by a `grep -rnE '[A-Za-z0-9_]/\*'` sweep run as a mandatory last step on every Kotlin file touched. Worth keeping as a standing habit for every future phase.

## Phase 3 — Research-backed integration placeholders (done)

Every item below was implemented only after confirming the real, current API against official
documentation via web search (javadocs, plugin wikis) - not guessed - a deliberate change from
earlier passes, which stuck to detection-only for anything not already backed by a verified
API. Citations are in each integration's own KDoc and in [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md).

| # | Section | Status | Notes |
|---|---|---|---|
| 9 | AuraSkills per-skill level/XP | ✅ (partial) | `%rl_skill_level_<skill>%`/`%rl_skill_xp_<skill>%` for every enabled default skill, via [`AuraSkillsIntegration`](src/main/kotlin/dev/rlscoreboard/integration/auraskills/AuraSkillsIntegration.kt), confirmed against the official 2.3.3 `api-bukkit` javadoc. "Skill Progress" percentage not implemented - the XP-required-for-next-level denominator isn't exposed on the public API (see that file's KDoc). |
| 8 | Jobs Reborn placeholders | ✅ (partial) | `%rl_job%`/`%rl_job_level%`/`%rl_job_exp%`/`%rl_job_progress%`/`%rl_job_points%` via [`JobsIntegration`](src/main/kotlin/dev/rlscoreboard/integration/jobs/JobsIntegration.kt) - a PAPI-passthrough translation layer over Jobs Reborn's own `jobsr_*` expansion (confirmed against the plugin's own wiki), not a compiled dependency (still excluded per the Jobs Reborn build-breakage story). `%rl_job_income%` not implemented - no matching Jobs Reborn concept exists. |
| 7 | Real Geyser/Floodgate platform placeholders | ✅ | `%rl_platform%`, `%rl_is_bedrock%`, `%rl_is_java%` added to [`InternalPlaceholders`](src/main/kotlin/dev/rlscoreboard/placeholder/InternalPlaceholders.kt), backed by Floodgate's existing UUID heuristic. Platform-based board selection confirmed already possible today via the existing generic `conditions: { placeholder: "%rl_platform%", ... }` mechanism - no new condition-engine feature needed. A dedicated `platform:` shorthand condition key (matching the spec's exact mockup syntax) is a small ergonomic nice-to-have, not a functional gap - still open below. |

## Phase 4 — Colors & animation engine quality (done)

| # | Section | Status | Notes |
|---|---|---|---|
| 15 | MiniMessage support | ✅ (restricted) | [`ColorUtil`](src/main/kotlin/dev/rlscoreboard/util/ColorUtil.kt) now parses `<tag>`-shaped strings as MiniMessage (confirmed bundled with Paper since 1.18.x - no new dependency needed), auto-detected, legacy `&`/hex path unchanged for everything else. **Deliberately restricted to visual tags only** (color/gradient/rainbow/decorations/reset/newline) - click/hover/insert/nbt/score/selector/translate are excluded as a security measure, since config strings often have placeholder-substituted (potentially player-influenced) content mixed in by the time they reach this parser. See docs/ANIMATIONS.md. |
| 14 | Named animation presets | ✅ | [`AnimationPresetFactory`](src/main/kotlin/dev/rlscoreboard/animation/AnimationPresetFactory.kt) procedurally generates the `frames:` list for all 8 named presets (static/fade/color/gradient/typing/scrolling/pulse/wave) at config-load time - zero runtime cost difference vs hand-written frames. `gradient`/`wave` use MiniMessage's own `<gradient:...:phase>`/`<rainbow:phase>` tags (confirmed phase-argument syntax via Adventure docs) rather than reimplementing gradient math. Wired into both per-line and title animation config parsing in `BoardConfigLoader`, `frames:` still takes priority if hand-written. See docs/ANIMATIONS.md. |
| 22 | Config validation (extended) | 🟡 (more coverage) | `ConfigValidator` now also catches unrecognised `preset:` names (same silent-fallback problem as unrecognised operators), and the scanner was generalized to recurse into nested maps at any depth (previously only checked one level into a list item), so a typo inside a line's nested `animation: {...}` block is now caught too. Still doesn't cover missing required fields or invalid MiniMessage syntax specifically (the latter fails safe at runtime already - see above - just isn't proactively flagged at startup). |

## Phase 5 — Test suite + WorldGuard/WorldEdit research (done)

| # | Section | Status | Notes |
|---|---|---|---|
| 32 | Test suite | ✅ (partial, high-value core) | Real JUnit5 test infra added (`kotlin("test")`, `useJUnitPlatform()`, `testImplementation.extendsFrom(compileOnly)` so Bukkit types resolve for tests too). 51 tests across [`VersionRangeTest`](src/test/kotlin/dev/rlscoreboard/integration/util/VersionRangeTest.kt) (20 - every section-32 edge case, already Python-verified once before this Kotlin was written, plus every real tested-version range this codebase actually declares), [`AnimationPresetFactoryTest`](src/test/kotlin/dev/rlscoreboard/animation/AnimationPresetFactoryTest.kt) (14 - structural checks per preset), and [`ConfigValidatorTest`](src/test/kotlin/dev/rlscoreboard/config/ConfigValidatorTest.kt) (17 - color/operator/preset validation, plus real `YamlConfiguration`-backed recursive-scan tests). Deliberately scoped to the pure-logic/self-contained classes; anything needing a live server (renderers, engines, commands) would need MockBukkit or similar - not attempted this phase, tracked below. |
| 11/12 | WorldGuard region conditions / WorldEdit area selection | ⬜ (researched, deliberately not implemented) | Researched the real WorldGuard 7.x API (`RegionContainer`/`RegionQuery`/`ApplicableRegionSet`, confirmed via WorldGuard's own current docs) and found the official Maven coordinates (`maven.enginehub.org`, `com.sk89q.worldguard:worldguard-bukkit` + `com.sk89q.worldedit:worldedit-bukkit`) with what initially looked like a clean dependency tree. Further research turned up a **real, documented precedent for exactly the failure mode this project already hit once with Jobs Reborn**: [EngineHub/WorldGuard#1874](https://github.com/EngineHub/WorldGuard/issues/1874), a build breaking because a transitive `worldedit-core` SNAPSHOT dependency became unresolvable. Since this environment cannot compile to verify a mitigation (dependency exclusions) actually works, the conservative call was made **not** to add this dependency this phase - repeating a known failure mode without any way to verify a fix isn't a risk worth taking blind. This is a "researched and consciously deferred" decision, not a skipped one; revisit when a real build environment is available to verify against. |

## Not yet started

Listed in spec order, each with a one-line reason it wasn't attempted this pass rather than a
silent gap:

| # | Section | Why deferred |
|---|---|---|
| 6 | ViaVersion integration | New plugin, not previously scaffolded - needs its own `Integration` implementation from scratch. |
| 20 | Condition engine: dedicated `platform:`/`region:` shorthand keys, `AND`/`OR`/`NOT` combinators | `platform` is functionally achievable today via the generic placeholder-condition mechanism (see Phase 3 above) - a dedicated shorthand key is cosmetic. `region:` depends on WorldGuard's region API, deliberately deferred - see Phase 5 above. Explicit boolean combinators beyond implicit AND aren't confirmed present. |
| 16 | Font/symbol safety audit | Not yet reviewed against Bedrock/varied resource packs specifically. |
| 24 | `create`/`edit`/`set`/`area` subcommands | The spec's full subcommand list includes several (`create`, `edit`, `set`, `area`) not present in the current command set beyond the leaderboard-specific `create`/`delete`. `area` specifically depends on WorldEdit, deferred (Phase 5). |
| 28 | Security/robustness pass | Not yet done as a dedicated review pass. |
| 29 | Public API surface review | `RLScoreboardAPI` exists (see `src/main/kotlin/dev/rlscoreboard/api/`) but hasn't been reviewed against this section's checklist (encapsulation, minor-version compatibility promises) specifically. |
| 30/31 | README/CHANGELOG/CONTRIBUTING/LICENSE overhaul | README is already fairly complete; CHANGELOG.md, CONTRIBUTING.md, and LICENSE do not exist yet. **LICENSE specifically needs your decision** - see spec section 31 ("không tự ý chọn license mà không kiểm tra project owner intent"). |
| 32 | Test suite (remaining scope) | Core pure-logic classes covered as of Phase 5 (see above). Renderers/engines/commands - anything needing a live Bukkit server - still untested; would need MockBukkit or similar test-double infrastructure, not yet added. |
| 33 | Clean build / `jar tf` verification | Cannot be done in this environment at all (no toolchain) - see the environment-constraint note at the top of this file. |
| 35/36 | Default preset quality pass, "god class" audit | Not yet done as dedicated passes. |
| 37 | Placeholder packs / expansion / theme system scaffolding | Not started - this is forward-looking architecture work for *after* the current package structure is confirmed solid. |

## How to keep using this file

Each future session should: pick a section (or a few related ones) from "Not yet started",
implement it, move its row to a "done" table with a status/notes update, and note here if
implementing it changed the plan for anything still open below it (e.g. section 7's real
Bedrock placeholders will probably need section 20's condition-engine work landed first).
