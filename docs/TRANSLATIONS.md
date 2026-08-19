# RLScoreboard — Translations

RLScoreboard ships English (`en`, the reference/fallback), Vietnamese (`vi`), and Japanese
(`ja`). Adding another language **never requires a code change** — see
[Why no code change is needed](#why-no-code-change-is-needed) below.

## How to create a language file

1. Copy `plugins/RLScoreboard/locales/en.yml` (or grab
   [`src/main/resources/locales/en.yml`](../src/main/resources/locales/en.yml) from source) to
   `plugins/RLScoreboard/locales/<code>.yml`, where `<code>` is your language's code —
   `ko` for Korean, `zh_cn` for Simplified Chinese, `zh_tw` for Traditional Chinese, `es` for
   Spanish, `fr` for French, `de` for German, etc. Lowercase, matching the filename exactly
   (no leading `locales/`, no `.yml` in the code itself).
2. Translate each value. **Leave every key (the part before `:`) exactly as-is** — only the
   quoted text after it is translated.
3. Set `language: <code>` in `config.yml`, or run
   `/rlscoreboard language set <code>` (requires `rlscoreboard.language.admin`) to switch and
   reload immediately without a restart.
4. Run `/rlscoreboard validate-language <code>` to check your work — see
   [Validation](#validation) below.

## Naming convention

- File: `locales/<code>.yml`, lowercase.
- Prefer [ISO 639-1](https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes) two-letter
  codes (`en`, `vi`, `ja`, `ko`, `es`, `fr`, `de`...). For a language that needs a
  region/script qualifier, use an underscore: `zh_cn`, `zh_tw`, `pt_br`.
- Keys inside the file are never translated or renamed — `LocaleManager` looks them up
  literally (e.g. `reload_success`), so a renamed key simply won't be found and that line
  falls back to English instead of erroring.

## Placeholders

Any `{token}` in curly braces (e.g. `{id}`, `{version}`, `{count}`) is substituted by the
plugin at runtime — **keep the token exactly as-is**, including the exact spelling, in your
translated line. You can move a token to a different position in the sentence (word order
varies by language) and repeat it if needed, but you cannot rename, remove, or add tokens that
don't exist in the English original for that same key — that's exactly what
`/rlscoreboard validate-language` flags as a "broken placeholder".

`%rl_*%`-style tokens (`%rl_player_name%`, `%rl_balance%`, etc.) are a *different* thing —
those are RLScoreboard's in-game placeholders, used inside `scoreboards/*.yml` and
`leaderboards/*.yml` content lines, not inside locale files. Locale files never contain
`%...%` placeholders.

## Color codes

Legacy `&` codes (`&a`, `&c`, `&7`, `&f`, `&e`, `&6`, plus formatting codes `&l`/`&o`/`&n`/`&k`
and reset `&r`) and hex codes in the exact form `&#RRGGBB` are both valid. Anything else after
an `&` — a typo, a stray `&` with nothing valid after it, a malformed hex code — is what
`/rlscoreboard validate-language` reports as an "invalid color code".

## Pluralization

Not implemented. Every locale key is a single fixed string with `{count}`/`{total}`-style
numeric tokens substituted in as plain text (e.g. `debug_loaded_boards: "&7Loaded boards:
&f{count}"` — the same line is used whether `{count}` is `0`, `1`, or `50`). Most of the
supported languages so far don't require grammatical pluralization for these short admin/UI
strings; if a future language genuinely needs it, that's a `LocaleManager` API change, not
something a translator works around in YAML.

## Fallback behavior

`<active locale>` → `en` → a visible placeholder (`&c[missing locale key: ...]`) if somehow
missing from both. A key you haven't translated yet simply isn't a problem — it silently shows
the English line instead of breaking or showing blank text. You don't need to translate 100%
of the file before it's usable in-game.

With `debug: true` in `config.yml`, the server log records the first time each missing
locale/key combination is hit (`[locale debug] '<code>' is missing key '<key>'.`) — a quick way
to see exactly which keys a translation still needs, beyond what
`/rlscoreboard validate-language` reports (that command's "missing keys" list is the same
information, computed proactively instead of waiting for the key to be requested).

## Validation

`/rlscoreboard validate-language <code>` (permission: `rlscoreboard.language.admin`) checks
your file against the English reference and reports:

- **Missing keys** — present in `en.yml`, absent from yours. Not fatal (falls back to English)
  but worth knowing about.
- **Unknown keys** — present in yours, not in `en.yml`. Usually a typo'd key name; the
  translation under it is simply never looked up.
- **Broken placeholders** — a shared key where your `{token}` set doesn't match English's for
  that key (missing, extra, or misspelled token).
- **Invalid color codes** — an `&` not followed by a valid legacy code or a proper
  `&#RRGGBB` hex code.

Example output:

```
Validating 'ko' against the English reference...
Missing keys (3): leaderboard_gui_footer, language_info_keys, validate_language_summary
Broken placeholders (1): leaderboard_history_header
Checked 55 reference key(s).
```

A clean result:

```
Validating 'ja' against the English reference...
No issues found - 'ja' is fully valid against the reference (en).
Checked 55 reference key(s).
```

## How to submit a translation

Open a pull request adding `src/main/resources/locales/<code>.yml` (so it ships bundled in the
jar and gets extracted automatically — see below for the alternative if you'd rather not touch
source), plus a one-line addition to this file's [supported languages](#why-no-code-change-is-needed)
note and the main [README](../README.md#localization). Run `/rlscoreboard validate-language
<code>` first and paste a clean result in the PR description — that's the fastest way to get a
translation merged without a review round-trip over a typo'd key.

If you'd rather not touch source at all: drop `<code>.yml` straight into a running server's
`plugins/RLScoreboard/locales/` folder and it's picked up on the next `/rlscoreboard reload` —
see below. That's a perfectly valid way to use (or even test-drive) a translation; it just
isn't bundled inside the jar for other server owners until it's submitted as above.

## Why no code change is needed

`LocaleManager.availableLocales()` is whatever `locales/*.yml` files actually exist on disk at
load time — not a fixed list in code (design spec section M: *"No hard-coded language enum
that prevents adding languages"*). Only the *bundled* set (currently `en`, `vi`, `ja` — the
ones shipped inside the jar and auto-extracted on first run) is a fixed list in
[`LocaleManager`](../src/main/kotlin/dev/rlscoreboard/config/LocaleManager.kt), since that's a
packaging decision, not a language-support limit. A community translation dropped straight
into `plugins/RLScoreboard/locales/` works identically to a bundled one from the moment
`/rlscoreboard reload` (or a restart) picks it up — `/rlscoreboard language list` will show it,
`/rlscoreboard language set <code>` will switch to it, and `/rlscoreboard validate-language
<code>` will check it, all with zero plugin code touched.

A third-party addon plugin can also register locale content programmatically via
`LocaleManager.registerLocale(code, reader)` — see its KDoc — for keys *that plugin itself*
defines, without needing RLScoreboard's core to know about them in advance.
