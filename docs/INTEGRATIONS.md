# RLScoreboard — Integrations

RLScoreboard never requires any of the plugins below. Every one is a **soft dependency**:
detected at startup, enabled only if installed *and* within its tested version range, and
disabled safely — never a crash — if it's missing, outdated, or fails to enable. See
[`IntegrationManager`](../src/main/kotlin/dev/rlscoreboard/integration/IntegrationManager.kt)
for the detection flow and [`Integration`](../src/main/kotlin/dev/rlscoreboard/integration/Integration.kt)
for the metadata contract every integration below implements.

Run `/rlscoreboard integrations` on your own server for the live, real-time version of this
table — that command is the source of truth; this file is a snapshot kept in sync with it by
hand. `/rlscoreboard debug` also includes a short integration summary.

## Status model

Six statuses, not a flat "supported or not" (design spec section 4) — each maps to an icon
shown by `/rlscoreboard integrations`/`/rlscoreboard status` and in the startup log:

| Icon | Status | Meaning |
|---|---|---|
| ✅ | `SUPPORTED` | Installed, version within the tested range, every declared capability wired successfully. |
| ⚠️ | `PARTIALLY_SUPPORTED` | Enabled, but only *some* declared capabilities wired — see [Capability system](#capability-system). |
| ⚠️ | `DETECTED_UNTESTED` | Installed and enabled optimistically, but the version is either newer than `maxTestedVersion` or unparseable — **not** treated as broken, just unverified. |
| ❌ | `INCOMPATIBLE` | Installed, but numerically *below* `minSupportedVersion` — genuinely risky, not enabled. |
| ❌ | `ERROR` | Detection or `enable()` threw unexpectedly — distinct from `INCOMPATIBLE`, this is a bug/edge-case, not a normal version mismatch. |
| ○ | `NOT_INSTALLED` | Target plugin isn't present at all. |

**The critical distinction this replaces:** a version *above* what was tested and a version
*below* what was tested used to be reported identically as "unsupported". They aren't
equally risky — a newer plugin release is far more likely to still work (`DETECTED_UNTESTED`,
enabled) than an older one that may simply be missing an API method this integration calls
(`INCOMPATIBLE`, not enabled). See
[`VersionRange`](../src/main/kotlin/dev/rlscoreboard/integration/util/VersionRange.kt)'s KDoc
for the exact comparison rule, including why `min` and `max` are compared with deliberately
different padding.

## Compatibility matrix

| Plugin | Status | Optional | Tested versions | Capabilities |
|---|---|---|---|---|
| [Vault](#vault) | ✅ Supported | Yes | 1.7 | `economy` |
| [PlaceholderAPI](#placeholderapi) | ✅ Supported | Yes | 2.11 | `placeholders` |
| [LuckPerms](#luckperms) | ✅ Supported | Yes | 5.0–5.4 | `rank`, `prefix`, `suffix` |
| [Jobs Reborn](#jobs-reborn) | ✅ Supported (PAPI passthrough) | Yes | 5.0–5.2 | `jobs_placeholders` |
| [AuraSkills](#auraskills) | ✅ Supported | Yes | 2.0–2.3 | `power`, `skills` |
| [Geyser](#geyser) | ✅ Supported (detection-only) | Yes | 2.0–2.4 | `bedrock` |
| [Floodgate](#floodgate) | ✅ Supported (detection-only) | Yes | 2.0–2.2 | `bedrock_identity` (heuristic) |
| [WorldGuard](#worldguard) | ✅ Supported (detection-only) | Yes | 7.0 | *(none yet — see [Extension points](#extension-points-not-yet-implemented))* |
| [WorldEdit](#worldedit) | ✅ Supported (detection-only) | Yes | 7.0–7.3 | *(none yet — see [Extension points](#extension-points-not-yet-implemented))* |

**"Detection-only" is a distinct, honest description of *scope*, not a status on its own.**
It means RLScoreboard correctly reports whether the plugin is installed and version-compatible
(reaching the real `SUPPORTED` status like any other integration once its checks pass), but
doesn't yet call into that plugin's own API for anything beyond that — see each plugin's
section below for exactly what is and isn't wired up. Nothing in this table declares a
capability without a datasource, placeholder, or other real mechanism backing it, per the
project rule: *don't claim an integration is supported until its API/version compatibility
has been verified.*

> **A note on the version ranges above.** Every range compiled against a real, published API
> (Vault, PlaceholderAPI, LuckPerms, AuraSkills) matches the `compileOnly` dependency pinned in
> `build.gradle.kts`, so those are accurate. The four detection-only additions — Geyser,
> Floodgate, WorldGuard, WorldEdit — do **not** need a compiled API dependency (see
> [How version detection works](#how-version-detection-works) below) but their ranges above
> are still provisional best-effort estimates, not independently verified against a live
> server, because this project has been developed in environments with no network access to
> pull those plugins and test against them. Treat those four rows as *candidates pending
> verification* until confirmed against a real running server (a green CI build alone doesn't
> verify this, since detection-only integrations don't touch those plugins' APIs at
> compile time) — please update this table and each integration's `minSupportedVersion`/
> `maxTestedVersion` once verified, per the project rule above.

## How version detection works

Every integration's `isInstalled()`/`readVersion()` default to reading the target plugin's
*own* `plugin.yml` version (`Plugin.getPluginMeta().getVersion()`) — see
[`AbstractIntegration`](../src/main/kotlin/dev/rlscoreboard/integration/AbstractIntegration.kt).
That works identically whether or not RLScoreboard has a compile-time API dependency on the
target plugin, which is why even the detection-only integrations below get real,
version-aware compatibility checking without needing a new Maven dependency just to answer
"what version is this".

Version compatibility is checked by comparing dot-separated numeric release tokens (not just
the leading major number) — see
[`VersionRange`](../src/main/kotlin/dev/rlscoreboard/integration/util/VersionRange.kt) for the
exact algorithm, which correctly handles shapes like `2.11.1-SNAPSHOT`, `5.2.6.6`,
`1.7.3-b131`, and `26.2-111` (all independently verified against a Python port of the same
algorithm before being written in Kotlin, since this environment has no compiler to check the
Kotlin itself — see the main [README](../README.md#building)).

## Startup log

```
[RLScoreboard] Loading integrations...
[RLScoreboard] ✅ Core
[RLScoreboard] ✅ PlaceholderAPI 2.11.6
[RLScoreboard] ✅ Vault 1.7.3
[RLScoreboard] ○ Jobs Reborn - not installed
[RLScoreboard] ✅ AuraSkills 2.3.5
[RLScoreboard] ✅ LuckPerms 5.4.117
[RLScoreboard] ✅ Geyser 2.4.0
[RLScoreboard] ✅ Floodgate 2.2.2
[RLScoreboard] ○ WorldGuard - not installed
[RLScoreboard] ○ WorldEdit - not installed
[RLScoreboard] Initialization complete.
```

A version above the tested maximum is enabled but flagged, not treated as broken:

```
[RLScoreboard] ⚠️ AuraSkills 3.0.1 (newer than tested max 2.3 - not yet verified)
```

A version below the tested minimum is not enabled:

```
[RLScoreboard] AuraSkills detected (version 1.9.0), but that's below the minimum tested
version (2.0). Integration has been disabled safely.
```

## Diagnostics commands

`/rlscoreboard integrations` (permission: `rlscoreboard.debug`) prints the full per-plugin
breakdown — version, status, capabilities (and, for `PARTIALLY_SUPPORTED`, exactly which
declared capabilities are missing) — for every integration above. `/rlscoreboard status` gives
a shorter, one-line-per-integration overview alongside player count and platform info.
`/rlscoreboard debug` includes the same condensed summary alongside board/leaderboard counts.

## Capability system

Code elsewhere in RLScoreboard never checks "is plugin X installed" directly — it asks
`IntegrationManager.hasCapability("economy")` (or a specific integration's own
`hasCapability(...)`) instead. This is what lets a capability degrade gracefully: Vault can be
installed with **no economy plugin behind it**, in which case the `Vault` integration itself
is still `Supported` (Vault genuinely is present), but `hasCapability("economy")` correctly
returns `false` until an economy provider actually registers — see
[`VaultIntegration`](../src/main/kotlin/dev/rlscoreboard/integration/vault/VaultIntegration.kt)
for the live re-check that makes this possible. `%rl_balance%` resolves to an empty string
(never a raw `%rl_balance%` shown to a player) in that case; hide the line entirely with a
placeholder-comparison condition in your board config if you'd rather it not appear at all.

## Per-integration notes

### Vault
Provides the `economy` leaderboard datasource and `%rl_balance%` placeholder, backed by
whatever economy plugin (Essentials, CMI, etc.) has registered a Vault `Economy` service.
Vault itself doesn't provide an economy — see the capability note above for what happens
without one.

### PlaceholderAPI
Two-way bridge: registers RLScoreboard's own `%rlscoreboard_*%` expansion for other plugins,
and lets any `%xxx%` RLScoreboard doesn't recognize itself fall through to PAPI, so
`%vault_eco_balance_formatted%`, `%jobs_job%`, etc. work in board/leaderboard lines without
RLScoreboard depending on those plugins directly.

### LuckPerms
Adds `%rl_rank%` (primary group, capitalised), `%rl_prefix%`, and `%rl_suffix%`. The three are
registered independently (each its own `runCatching` inside `enable()`), so if a future
LuckPerms release ever broke just one of the three underlying API calls, this integration
would show `PARTIALLY_SUPPORTED` with the other two still working, rather than all three going
down together - see [`LuckPermsIntegration`](../src/main/kotlin/dev/rlscoreboard/integration/luckperms/LuckPermsIntegration.kt),
the reference example for how `Integration.enable()`'s per-capability isolation is meant to be
used.

### Jobs Reborn
Still no compiled dependency on Jobs Reborn itself - the only published API artifact pulls in
hard transitive dependencies (mcMMO, WorldGuard, WorldEdit, WildStackerAPI, StackMob) that
broke dependency resolution entirely, see the main
[README](../README.md#what-broke-on-the-first-real-build). Instead, `%rl_job%`,
`%rl_job_level%`, `%rl_job_exp%`, `%rl_job_progress%`, and `%rl_job_points%` are a thin
translation layer over Jobs Reborn's *own* PlaceholderAPI expansion (`jobsr_*`, which Jobs
Reborn registers itself whenever both it and PlaceholderAPI are present - confirmed against
`github.com/Zrips/Jobs/wiki/Placeholders`, the plugin's own documentation) - RLScoreboard
never touches a Jobs Reborn class, compiled or otherwise. Each targets the player's first
currently-active job; a multi-job server wanting a specific job's numbers can already use
Jobs Reborn's raw `%jobsr_user_jlevel_2%`-style placeholders directly in any board line via
the PlaceholderAPI passthrough. **Not implemented**: `%rl_job_income%` - there's no Jobs
Reborn placeholder for a running income total (payments go through Vault's balance, which
isn't job-specific); forcing a placeholder onto a concept the plugin doesn't track this way
was avoided rather than guessed at.

### AuraSkills
Registers the `auraskills_powerlevel` leaderboard datasource (sum of all skill levels), plus
per-skill `%rl_skill_level_<skill>%` and `%rl_skill_xp_<skill>%` placeholders for every
*enabled* default skill (e.g. `%rl_skill_level_mining%`) - looped over AuraSkills' own
`Skills` enum, not hand-written per skill, so a future AuraSkills version adding/removing a
default skill needs no RLScoreboard update. Method signatures confirmed against the official
2.3.3 `api-bukkit` javadoc (`aurelium.dev/javadocs/auraskills-api-bukkit/...`) before writing
this - not guessed.

**Not implemented**: a "Skill Progress" percentage. `getSkillXp(Skill)` ranges from `0` to
"the XP required to progress to the next skill level" per its own javadoc, but that
denominator isn't exposed anywhere found on the public API module - only an internal,
non-public `XpRequirements` class turned up, in the plugin's old pre-rename package, not
reachable from the public `api-bukkit` artifact this project depends on. `%rl_skill_xp_<skill>%`
exposes the raw in-level XP number only; a real percentage is deferred rather than guessed at.
Custom skills registered by *other* plugins (AuraSkills' `NamespacedId`-based extension system)
aren't enumerated either - only the built-in default skill set.

### Geyser
Detects a Bedrock↔Java proxy running on this server (Bukkit plugin id `Geyser-Spigot`). The
`bedrock` capability means exactly that fact — "Bedrock players can connect at all" — and
needs no Geyser API call to answer, which is why this is safely implementable without a new
compile-time dependency.

### Floodgate
Detects Floodgate's own plugin (Bukkit id `floodgate`). The `bedrock_identity` capability is
answered by [`FloodgateIntegration.isLikelyBedrockPlayer`](../src/main/kotlin/dev/rlscoreboard/integration/floodgate/FloodgateIntegration.kt),
a **heuristic** (Floodgate assigns Bedrock players a UUID with its version nibble forced to
`0`) — not a call to Floodgate's real `FloodgateApi.isFloodgatePlayer(uuid)`. It's a
reasonable, dependency-free signal, but not certain; don't gate anything
permission/economy-sensitive on it alone. Wiring the real `floodgate-api` as a `compileOnly`
dependency is a documented future upgrade, deferred here for the same "needs a live server to
verify against" reason as the version ranges above.

**Real platform placeholders** (design spec section 7) now exist, registered by core (not by
this integration directly, so they're always defined): `%rl_platform%` (`"Java"` or
`"Bedrock"`), `%rl_is_bedrock%`, `%rl_is_java%` - see
[`InternalPlaceholders`](../src/main/kotlin/dev/rlscoreboard/placeholder/InternalPlaceholders.kt).
Without Floodgate's `bedrock_identity` capability live, every player reports as Java - the
honest default when there's genuinely no signal to go on (Geyser without Floodgate usually
means a real linked Java/Xbox account, indistinguishable by UUID from any other Java player).
**Platform-based board selection already works today** with the existing generic placeholder
condition, no new condition-engine feature needed:
```yaml
conditions:
  placeholder: "%rl_platform%"
  operator: "=="
  value: "Bedrock"
```
A dedicated `platform: [bedrock]` shorthand (matching the original design mockup's exact
syntax) is a small ergonomic nice-to-have, not a functional gap - tracked in ROADMAP.md.

### WorldGuard
Detected only. The design spec lists a future `conditions` capability (e.g.
"inside WorldGuard region X") — not implemented in this pass. WorldGuard's region API differs
meaningfully between the 6.x and 7.x major versions, and getting that wrong without a live
server to test against is exactly the class of mistake that broke the native Jobs Reborn
attempt (see above) — deferred rather than risked.

### WorldEdit
Detected only, per the design spec's own "future/editor support" framing — there's no current
RLScoreboard feature (e.g. a future `/rlscoreboard editor` selection wand) that needs
WorldEdit's API yet.

## Extension points (not yet implemented)

The design spec also calls for extension points — not implementations — for **CMI**,
**EssentialsX**, **MythicMobs**, **Citizens**, and **PlayerPoints**. None of these have a
verified compiled or detection-only integration yet, and none are claimed as "Supported" or
even "Detection-only" above for that reason. Adding one is: implement
[`Integration`](../src/main/kotlin/dev/rlscoreboard/integration/Integration.kt) (or extend
[`AbstractIntegration`](../src/main/kotlin/dev/rlscoreboard/integration/AbstractIntegration.kt)
for a detection-only start) in its own `integration/<plugin>/` package, register it in
[`RLScoreboardPlugin.onEnable`](../src/main/kotlin/dev/rlscoreboard/RLScoreboardPlugin.kt), and
add a row to this file and the README once its version range is actually verified.

## Reporting a compatibility problem

If RLScoreboard reports a plugin as unsupported/outside-range on a version you believe should
work, or an integration that reports `Active` isn't actually doing anything, please open an
issue with the output of `/rlscoreboard integrations` and the exact version of the other
plugin you're running — that's the fastest way to get a `minSupportedVersion`/
`maxTestedVersion` range corrected.
