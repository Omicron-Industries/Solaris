# Configuration

Solaris does **not** use Forge's per-world `serverconfig` mechanism — everything
it saves lives under the game instance's **global** `config/` folder instead,
and applies the same way regardless of which world/server you're in. Some data
is further namespaced by world name as a subfolder/filename *within* that
global folder (so it doesn't collide between worlds), but it's still not the
same as a true per-world save (it isn't stored inside `saves/<world>/`, and it
persists even if you delete and recreate a world with the same name only if
the name matches).

- `config/solaris_themes.json` — UI theme
- `config/solaris_mob_icons.json` — mob icon overrides
- `config/solaris/presets.json` — saved presets
- `config/solaris/shapes/<name>.json` — saved plan/shape data
- `config/solaris/mapdata/<world>/<dimension>.dat` — cached map tile colors, namespaced per world name
- `config/solaris/waypoints/<world>.json` — waypoints, namespaced per world name
- `config/solaris/waypoint_icons/` — custom waypoint icon assets
- `config/solaris/gt_veins/<world>.json` — GTCEu vein overlay data, namespaced per world name (if GTCEu integration is active)

If you want a clean settings reset, delete the relevant file(s) above rather
than looking in a world's save folder — nothing Solaris-related lives there.
