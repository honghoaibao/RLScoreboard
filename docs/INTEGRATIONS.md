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

## Compatibility matrix

| Plugin | Status | Optional | Tested versions | Capabilities |
|---|---|---|---|---|
| [Vault](#vault) | Supported | Yes | 1.7 | `economy` |
| [PlaceholderAPI](#placeholderapi) | Supported | Yes | 2.11 | `placeholders` |
| [LuckPerms](#luckperms) | Supported | Yes | 5.0–5.4 | `rank`, `prefix` |
| [Jobs Reborn](#jobs-reborn) | Detection-only | Yes | 5.0–5.2 | `jobs_placeholders` |
| [AuraSkills](#auraskills) | Supported (partial) | Yes | 2.0–2.3 | `power` |
| [Geyser](#geyser) | Detection-only | Yes | 2.0–2.4 | `bedrock` |
| [Floodgate](#floodgate) | Detection-only | Yes | 2.0–2.2 | `bedrock_identity` (heuristic) |
| [WorldGuard](#worldguard) | Detection-only | Yes | 7.0 | *(none yet)* |
| [WorldEdit](#worldedit) | Detection-only | Yes | 7.0–7.3 | *(none yet)* |

**"Detection-only" is a distinct, honest status, not a lesser form of "Supported".** It means
RLScoreboard correctly reports whether the plugin is installed and version-compatible, but
doesn't yet call into that plugin's own API for anything beyond that — see each plugin's
section below for exactly what is and isn't wired up. Nothing in this table is marked
"Supported" without a datasource, placeholder, or other real capability backing it, per the
project rule: *don't claim an integration is supported until its API/version compatibility
has been verified.*

> **A note on the version ranges above.** Every range compiled against a real, published API
> (Vault, PlaceholderAPI, LuckPerms, AuraSkills) matches the `compileOnly` dependency pinned in
> `build.gradle.kts`, so those are accurate. The four detection-only additions in this pass —
> Geyser, Floodgate, WorldGuard, WorldEdit — do **not** need a compiled API dependency (see
> [How version detection works](#how-version-detection-works) below) but their ranges above
> are still provisional best-effort estimates, not independently verified against a live
> server, because this change was written in an environment with no network access to pull
> those plugins and test against them. Treat those four rows as *candidates pending
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
version-aware compatibility checking (a plugin installed outside its tested range is disabled
with a warning, exactly like a fully-supported one) without needing a new Maven dependency
just to answer "what version is this".

Version compatibility is checked by **major version** only (see
[`VersionRange`](../src/main/kotlin/dev/rlscoreboard/integration/util/VersionRange.kt)) — e.g.
a tested range of `5.0`–`5.4` accepts any `5.x`, matching how every range in this table is
expressed. An unparseable version string is treated as compatible rather than a false-negative
disable.

## Startup log

```
[RLScoreboard] Loading integrations...
[RLScoreboard] ✓ Core
[RLScoreboard] ✓ PlaceholderAPI 2.11.6
[RLScoreboard] ✓ Vault 1.7.3
[RLScoreboard] - Jobs Reborn not installed
[RLScoreboard] ✓ AuraSkills 2.3.5
[RLScoreboard] ✓ LuckPerms 5.4.117
[RLScoreboard] ✓ Geyser 2.4.0
[RLScoreboard] ✓ Floodgate 2.2.2
[RLScoreboard] - WorldGuard not installed
[RLScoreboard] - WorldEdit not installed
[RLScoreboard] Initialization complete.
```

An outside-tested-range plugin logs a warning instead of the `✓` line and is not enabled:

```
[RLScoreboard] WARNING: AuraSkills detected (version 3.0.1), but that's outside the tested
range (2.0-2.3). Integration has been disabled safely.
```

## Diagnostics command

`/rlscoreboard integrations` (permission: `rlscoreboard.debug`) prints the full per-plugin
breakdown — version, status, capabilities — for every integration above. `/rlscoreboard
debug` includes a condensed one-line-per-integration summary alongside board/leaderboard
counts.

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
Adds `%rl_rank%` (primary group, capitalised) and `%rl_prefix%` (LuckPerms prefix).

### Jobs Reborn
Detection-only by design, not by gap. A native sortable "jobs total level" datasource was
tried; the only published Jobs Reborn API artifact pulls in hard transitive dependencies
(mcMMO, WorldGuard, WorldEdit, WildStackerAPI, StackMob) that broke dependency resolution
entirely — see the main [README](../README.md#what-broke-on-the-first-real-build) for the
full story. `%jobs_*%` placeholders still work anywhere via PlaceholderAPI passthrough once
both PlaceholderAPI and Jobs are installed; that's just not a directly-sortable
`LeaderboardDataSource`.

### AuraSkills
Registers the `auraskills_powerlevel` leaderboard datasource (sum of all skill levels).
Per-skill level/XP/name capabilities from the original integration wishlist are **not yet
implemented** — declaring them as supported before a datasource actually backs them would be
exactly the kind of overclaiming this document exists to avoid. Tracked as a documented future
addition.

### Geyser
Detects a Bedrock↔Java proxy running on this server (Bukkit plugin id `Geyser-Spigot`). The
`bedrock` capability means exactly that fact — "Bedrock players can connect at all" — and
needs no Geyser API call to answer, which is why this is safely implementable without a new
compile-time dependency. It does **not** yet gate any rendering behavior (e.g. a future
`bedrock.safe-mode` from the wider 0.4.x design) — that's a separate, larger feature, not part
of this pass.

### Floodgate
Detects Floodgate's own plugin (Bukkit id `floodgate`). The `bedrock_identity` capability is
answered by [`FloodgateIntegration.isLikelyBedrockPlayer`](../src/main/kotlin/dev/rlscoreboard/integration/floodgate/FloodgateIntegration.kt),
a **heuristic** (Floodgate assigns Bedrock players a UUID with its version nibble forced to
`0`) — not a call to Floodgate's real `FloodgateApi.isFloodgatePlayer(uuid)`. It's a
reasonable, dependency-free signal, but not certain; don't gate anything
permission/economy-sensitive on it alone. Wiring the real `floodgate-api` as a `compileOnly`
dependency is a documented future upgrade, deferred here for the same "needs a live server to
verify against" reason as the version ranges above.

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
