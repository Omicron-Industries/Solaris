# Solaris — KubeJS integration

Solaris registers a KubeJS plugin (`net.phoenixvine.solaris.integration.kubejs.SolarisKubeJSPlugin`,
via `kubejs.plugins.txt`) that's only ever loaded if KubeJS itself is installed — Solaris works
identically without it.

## Script type: **client only**

Everything Solaris exposes is client-side. Solaris is a client-rendered map mod — there is no
server component, no dedicated world data, nothing that runs on a dedicated server. Every binding
and class below is only usable from **`kubejs/client_scripts`**.

- Putting these calls in `server_scripts` will fail (the classes/instances simply don't exist
  there — `Minecraft.getInstance()` and everything built on it is client-only).
- There is nothing to register in `startup_scripts` — Solaris doesn't define blocks, items,
  recipes, or anything else startup scripts are for.

## Bindings (global names available in scripts)

| Global name | Class | Purpose |
|---|---|---|
| `SolarisAPI` | `net.phoenixvine.solaris.api.SolarisAPI` | Everything below — feature gates, direct value control, overlays, waypoints |
| `SolarisFeatureState` | `net.phoenixvine.solaris.api.SolarisFeatureState` | Enum: `DISABLED`, `VISIBLE`, `ENABLED` |
| `UnexploredStyle` | `net.phoenixvine.solaris.client.render.UnexploredStyle` | Enum: `FOG`, `STARFIELD`, `PHOENIX`, `CLOUD` |
| `SolarisConfig` | `net.phoenixvine.solaris.config.SolarisConfig` | Every display/behavior config value — see below |

Also allowed (for type/return-value access, not bound as a top-level global):
`net.phoenixvine.solaris.client.waypoint.Waypoint`, `net.phoenixvine.solaris.client.overlay.SolarisOverlay`,
`SolarisAPI.ScriptOverlay`, `net.minecraftforge.common.ForgeConfigSpec.ConfigValue`/`BooleanValue`/
`IntValue`/`DoubleValue`/`EnumValue`.

## Feature gating

Same three-layer model for every registered feature id — gate, tier requirement, explicit state —
checked in that order by `SolarisAPI.isFeatureEnabled(id, dimension)`:

```js
// Server-driven gate (e.g. re-applied every login from your own save data)
SolarisAPI.setFeatureEnabled('globe_view', false)

// Per-dimension tier requirement
SolarisAPI.setTier(ResourceLocation.of('minecraft:overworld'), 3)
SolarisAPI.requireTier('hillshading', ResourceLocation.of('minecraft:overworld'), 3)

// Explicit per-dimension state (DISABLED / VISIBLE / ENABLED)
SolarisAPI.setFeatureState('waypoints', ResourceLocation.of('minecraft:the_nether'), SolarisFeatureState.VISIBLE)
```

Built-in feature ids (constants on `SolarisAPI`, e.g. `SolarisAPI.FEATURE_GLOBE_VIEW`):
`globe_view`, `shape_planner`, `png_export`, `web_export`, `guild_share`, `fullscreen_map`,
`show_coordinates`, `waypoints`, `minimap`, `world_map`, `underground_map`, `settings_menu`,
`goto_coordinate`, `theme_select`, `hillshading`, `vignette`, `black_and_white`, `chunk_grid`,
`show_mobs`, `rail_network`, `unexplored_style`.

You can also register your own feature id and gate it the same way — `warnIfUnknown` only logs a
debug-level note, it never blocks anything.

## Direct value control

Getter/setter pairs that actively drive Solaris's state (not just permit/deny it), matching exactly
what the in-game button click would do — including invalidating the right caches so the change
shows immediately:

```js
SolarisAPI.getTheme()                              // -> "NEBULA"
SolarisAPI.getAvailableThemes()                     // -> ["VOID", "NEBULA", "SOLAR_FLARE", "ECLIPSE", ...custom]
SolarisAPI.setTheme('SOLAR_FLARE')

SolarisAPI.getUnexploredStyle()                     // global default
SolarisAPI.setUnexploredStyle(UnexploredStyle.PHOENIX)

SolarisAPI.getUnexploredStyle(dimension)            // per-dimension, falls back to global if unset
SolarisAPI.setUnexploredStyle(dimension, UnexploredStyle.STARFIELD)
SolarisAPI.clearUnexploredStyleOverride(dimension)

SolarisAPI.isHillshadingEnabled() / setHillshadingEnabled(bool)
SolarisAPI.isVignetteEnabled() / setVignetteEnabled(bool)
SolarisAPI.isBlackAndWhiteEnabled() / setBlackAndWhiteEnabled(bool)
SolarisAPI.isChunkGridEnabled() / setChunkGridEnabled(bool)
SolarisAPI.isShowMobsEnabled() / setShowMobsEnabled(bool)
SolarisAPI.isRailNetworkEnabled() / setRailNetworkEnabled(bool)
```

These setters deliberately do **not** call `.save()` — they're meant to reflect the calling
script's own state (e.g. reapplied every login), not get written into Solaris's global client
config. If the player later opens the Solaris settings menu themselves, that still saves normally.

### Everything else: raw config access

Every other slider/toggle (saturation, contrast, water opacity, minimap zoom, etc.) is reachable
directly through `SolarisConfig`, exactly like Java code in the mod itself already does:

```js
SolarisConfig.SATURATION.get()
SolarisConfig.SATURATION.set(1.5)
SolarisAPI.refreshRendering()   // force an immediate visual refresh after a raw SolarisConfig write
```

`refreshRendering()` is the one thing to remember when mutating `SolarisConfig` directly — the
dedicated `SolarisAPI` setters above already call it for you.

## Custom overlays from script

`SolarisOverlay`'s own `colorAt` returns `Optional<Integer>`, which doesn't translate cleanly to
JS — use the script-friendly wrapper instead. `fn` returns a plain ARGB `Integer` to tint a chunk,
or `null`/`undefined` for no tint:

```js
SolarisAPI.registerScriptOverlay('my_pack:claims', (dimension, chunkX, chunkZ) => {
    return isClaimed(dimension, chunkX, chunkZ) ? 0x8800FF00 : null
})

SolarisAPI.unregisterScriptOverlay('my_pack:claims')
```

Registrations are keyed by `id` — re-registering the same id (e.g. on a `/kubejs reload`) cleanly
replaces the previous instance instead of leaking a duplicate overlay, so it's safe to call
`registerScriptOverlay` unconditionally at the top of a client script.

## Waypoint API

```js
let wp = SolarisAPI.addWaypoint('Quest Giver', dimension, x, y, z, 'FFFF6A1A')
SolarisAPI.getWaypoints()
SolarisAPI.removeWaypoint(wp.id)   // returns false if cancelled or not found — see events below
```

## Events

Plain Forge events posted on the client event bus — hook them with KubeJS's generic
`onEvent('<fully.qualified.ClassName>', event => {...})`, the same mechanism KubeJS already
supports for any Forge event, not something specific to Solaris:

| Event class | Cancelable | Fires when |
|---|---|---|
| `net.phoenixvine.solaris.api.event.WaypointEvent$Added` | No | A waypoint is created |
| `net.phoenixvine.solaris.api.event.WaypointEvent$Removed` | **Yes** | A waypoint is about to be removed — cancel to protect it (e.g. a quest-critical waypoint) |
| `net.phoenixvine.solaris.api.event.WaypointEvent$Reached` | No | A player comes within ~8 blocks of a waypoint (once per approach, not every tick they stay there) |

```js
onEvent('net.phoenixvine.solaris.api.event.WaypointEvent$Reached', event => {
    console.log(`${event.player.name} reached ${event.waypoint.name}`)
})

onEvent('net.phoenixvine.solaris.api.event.WaypointEvent$Removed', event => {
    if (event.waypoint.name === 'Quest Giver') event.cancel()
})
```
