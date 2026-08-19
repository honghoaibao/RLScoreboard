# RLScoreboard — Animations & Colors

## Colors

Two syntaxes are supported side by side (design spec section 15):

- **Legacy** (unchanged from every previous version): `&a` / `&c` / `&l` etc., plus
  `&#RRGGBB` hex. This is what every existing config already uses — nothing about it changed.
- **MiniMessage**: `<red>`, `<gradient:#00AEEF:#7B61FF>...</gradient>`, `<rainbow>`, etc. A
  line takes the MiniMessage path *only if it contains a `<tag>`-shaped substring at all* —
  legacy-only lines (the overwhelming majority) are completely unaffected. Once a line does
  contain a tag, the **whole line** is parsed as MiniMessage — use MiniMessage's own
  `<red>`/`<#RRGGBB>` for any other colors in that same line rather than mixing in `&c`, since
  MiniMessage doesn't interpret bare `&` codes itself.

**Only visual tags are enabled**, deliberately: `color`, `gradient`, `rainbow`,
`decorations` (bold/italic/underline/strikethrough/obfuscated), `reset`, `newline`. Tags like
`<click:...>`, `<hover:...>`, `<insert:...>`, `<key:...>`, `<nbt:...>`, `<score:...>`,
`<selector:...>`, and `<translate:...>` are **not** enabled — a config line is often built by
substituting placeholders (player names, PlaceholderAPI output from other plugins) into an
admin's template *before* it reaches the color parser, so an unrestricted parser would let a
player-influenced value that happens to contain `<click:run_command:...>` become a real,
clickable, command-running component. With only visual tags enabled, a disabled tag is just
treated as literal text instead — see [`ColorUtil`](../src/main/kotlin/dev/rlscoreboard/util/ColorUtil.kt)
for the exact list and reasoning. A malformed/invalid MiniMessage tag never crashes rendering
either way — it logs a warning (with `debug: true`, in server console regardless) and falls
back to legacy/plain parsing for that specific line.

```yaml
lines:
  - "&aLegacy still works exactly as before"
  - "<gradient:#00AEEF:#7B61FF>A MiniMessage gradient line</gradient>"
  - "<rainbow>Rainbow text</rainbow>"
```

## Animation presets

Every animated line/title still boils down to the same thing under the hood: a `frames:`
list, cycled by [`AnimationEngine`](../src/main/kotlin/dev/rlscoreboard/animation/AnimationEngine.kt)
purely from elapsed time (no per-line task, no per-player task — a single shared heartbeat,
per design spec section 14's "animation phải nhẹ"). Writing `frames:` by hand still works
exactly as before. New: `preset:` procedurally *generates* that frame list for you via
[`AnimationPresetFactory`](../src/main/kotlin/dev/rlscoreboard/animation/AnimationPresetFactory.kt) —
generation happens once, when the config loads, not on every render, so a procedural preset
costs exactly the same at runtime as an equivalent hand-written `frames:` list would.

```yaml
lines:
  - text: "&ePulse"
    animation:
      enabled: true
      preset: pulse
      colors: ["#FFFFFF", "#FF5555"]
      interval: 2       # seconds between frames
      frame-count: 20   # how many frames to generate (fade/gradient/pulse/wave only)
```

`frames:` (hand-written) always takes priority over `preset:` if both are somehow present —
this only matters if you're migrating a line from one to the other.

| Preset | What it does | Uses `colors` | Uses `frame-count` | Uses `width`/`speed` |
|---|---|---|---|---|
| `static` | No animation — a single frame. | – | – | – |
| `fade` | Solid color, one-way transition from `colors.first()` to `colors.last()`. | ✅ | ✅ | – |
| `color` | Cycles through every color in `colors`, one frame each. | ✅ | *(= colors.size)* | – |
| `gradient` | A real MiniMessage gradient across the whole line, smoothly shifting. | ✅ | ✅ | – |
| `pulse` | Like `fade`, but oscillates back and forth (a "breathing" loop) instead of a one-way transition. | ✅ | ✅ | – |
| `wave` | Like `gradient` but shifts faster/more restlessly; an Adventure `<rainbow>` if no `colors` given. | optional | ✅ | – |
| `typing` | Reveals the text one character at a time (typewriter effect). | – | *(= text length)* | – |
| `scrolling` | Classic horizontal marquee — slides a `width`-character window across the text. | – | – | ✅ |

Defaults if `colors`/`frame-count`/`width`/`speed` are omitted: `colors` → `["#FFFFFF",
"#AAAAAA"]` for fade/color/pulse, `["#00AEEF", "#7B61FF"]` for gradient; `frame-count` → 16;
`width` → 20; `speed` → 1. An unrecognised `preset:` name falls back to a single static frame
— `/rlscoreboard reload` (which runs [config validation](#validation)) will flag the typo
rather than leaving it silently non-animated.

### Validation

`preset:` and `operator:` typos are both things that previously failed *silently* (an
unrecognised preset just doesn't animate; an unrecognised condition operator silently
defaults to `==` — see [`ConfigValidator`](../src/main/kotlin/dev/rlscoreboard/config/ConfigValidator.kt)).
Startup and `/rlscoreboard reload` both run a validation pass that catches these, plus invalid
color codes, and logs a consolidated, human-readable report — never blocking startup over a
config mistake.
