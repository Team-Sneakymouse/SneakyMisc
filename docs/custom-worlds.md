# Configured worlds

SneakyMisc reads `plugins/SneakyMisc/worlds.yml` during Paper's datapack discovery phase and writes `plugins/SneakyMisc/generated-worlds.zip`. Paper loads this pack before worlds start. Multiverse 5 manages any worlds declared under `worlds` after Minecraft creates them.

On the first start, SneakyMisc writes `plugins/SneakyMisc/worlds.example.yml`. Copy it to `worlds.yml`, then edit the values. The config has no reference to the old datapack files. SneakyMisc generates complete definitions from it. The example uses the directory version of `plots`, including its `terrain-y: 35` density function, plus the supplied `rebeii` and `peak` IDs. The older `plots.zip` in the server backup has different terrain density and surface rules, so switching to this example changes newly generated plots terrain. Restart the server after edits. Confirm the generated pack is enabled with `/datapack list enabled`.

## Existing worlds

Leave an existing Multiverse world's key out of the `worlds` section. Its saved `world_gen_settings.dat` already specifies the dimension type and generator. Keep the same dimension type, biome, and noise settings IDs in `worlds.yml` so the saved references continue to resolve. Do not enable both the original pack and this generated pack with the same IDs; remove the old pack after validating a copy of the server.

Each saved dimension has its own `world_gen_settings.dat` under `world/dimensions/<namespace>/<dimension>/data/minecraft/`. These files can contain references to other dimensions' generator settings. Before renaming or removing a generator ID, check every saved file, including the Nether and worlds Multiverse does not normally load. For example, an old Nether settings file may still refer to `plots:plots_generator` even after the active overworld switches to `plots:plots_generator_inner`. Keep a `plots-generators` entry under the old ID until all saved references are migrated; otherwise Paper can fail with `Overworld settings missing` while loading that dimension.

Minecraft 26.2 no longer uses `allow-nether` in `server.properties`. On Paper, `misc.enable-nether: false` in `config/paper-global.yml` prevents Paper from loading the Nether at startup. Multiverse can still load an imported Nether independently, so set `auto-load: false` for `minecraft:the_nether` in `plugins/Multiverse-Core/worlds.yml`, or remove that world from Multiverse without deleting its files. The `allowEnteringNetherUsingPortals` game rule controls portal travel only.

`build-min-y` and `build-height` set a dimension's build range. Both must be multiples of 16. The highest buildable Y is `build-min-y + build-height - 1`. `logical-height` is Minecraft's separate logical cap; keep the original value unless you intend to change that behavior. Changing biome colors affects the same biome ID wherever that biome is used. Existing chunks keep their blocks and stored biomes.

Set `sky-color` and `fog-color` on a biome for the colors a player sees in that biome. Biome attributes override dimension attributes. The optional dimension fields are named `fallback-sky-color` and `fallback-fog-color` because they apply only where the active biome does not override them. The example leaves them out. Vanilla biomes normally supply their own colors, so dimension fallbacks do not recolor vanilla biomes.

The `plots-generators` section contains the custom 26.2 noise preset. `terrain-y` sets the surface level for newly generated chunks. `noise-min-y` and `noise-height` describe the range over which terrain is calculated; they are separate from the dimension type's build range, as in the supplied `plots` pack. Existing chunks do not move when `terrain-y` changes.

If `surface` is omitted, the generator uses the grass surface from the older `plots.zip`. Set `surface: corruption` for the biome-specific colored surface used by the directory `plots` pack. Corruption uses white, light gray, green, gray, and cyan glazed terracotta according to its fixed surface rule. This choice belongs to each generator, so an adventure world can use grass while a plots world uses corruption. The surface rule changes only newly generated chunks. Existing configs that omitted `surface` will generate grass in new chunks after this update; add `surface: corruption` to preserve their previous output.

## Values Minecraft requires

Minecraft 26.2 requires several dimension fields, so omitting them does not invoke a Minecraft default. SneakyMisc makes these fixed, overworld-like choices:

| Generated value | Effect |
| --- | --- |
| `has_skylight: true`, `has_ceiling: false` | Uses outdoor skylight and an open sky. |
| `has_ender_dragon_fight: false` | Does not create an End dragon fight. |
| `coordinate_scale: 1.0` | Does not scale travel coordinates between dimensions. |
| `ambient_light: 0.0` | Adds no fixed ambient light. |
| `infiniburn: #minecraft:infiniburn_overworld` | Uses the overworld block tag for blocks on which fire can burn indefinitely. |
| `monster_spawn_block_light_limit: 0`, `monster_spawn_light_level: 0..7` | Uses the supplied packs' low-light monster-spawn thresholds. |
| `default_clock: minecraft:overworld`, `timelines: #minecraft:in_overworld` | Keeps the overworld day/night timeline, ordinary `/time` commands, and time markers used by sleep. These fields are optional in Minecraft, but omitting them changes time behavior. |

The optional dimension `attributes` map is omitted unless you configure fallback visual colors or `ambient-light-color`. Piglin, portal, bed, respawn-anchor, music, cloud, and cave-mood attributes are never copied from the old packs; Minecraft applies its own behavior when those attributes are absent.

Minecraft also requires each biome's `temperature`, `downfall`, `precipitation`, and `water-color`, so every custom biome lists them explicitly. Temperature affects snow, ice, and weather; downfall and precipitation affect rain and snow. Water color affects water rendering. Minecraft requires `spawners`, `spawn_costs`, `carvers`, and `features` too. SneakyMisc emits empty values for them except for any `vegetation-features` you list. These custom biomes therefore have no biome-defined mob spawns or carvers. Other biome visual colors are optional and use Minecraft's behavior when omitted. This creates deliberately sparse custom biomes; it is not a general vanilla-biome cloning tool.

SneakyMisc rejects unknown keys, malformed lists, and references to missing custom definitions before writing the pack. `minecraft:` references are allowed and checked by Paper when it loads the datapack. A config error stops startup before worlds load, with the YAML section path in the error. There is no separate validation command because registry resolution still needs a server start. Validate a server copy before editing production.

## New worlds

Add a new namespaced key under `worlds`. SneakyMisc generates a full `dimension/<key>.json` entry, so Minecraft creates the dimension at startup and Multiverse 5 discovers it as a `CUSTOM` world. Use `/mv info <key>` to confirm Multiverse knows it. A flat generator needs a `biome` and `layers` list; use `layers: []` for a void world. A noise generator needs a `settings` ID and a fixed `biome` ID.

Keep the world entry and its referenced definitions in the config for as long as that world exists. Removing them can prevent the saved world from loading. Adding a `worlds` entry for a world that Multiverse already created can produce a duplicate world-config error. The feature does not rewrite saved `world_gen_settings.dat` or automatically migrate existing generator settings.

`skybox` overrides five vanilla biome IDs globally and uses an older biome format. This feature currently handles the custom `plots` and `peak` biomes; it does not translate those vanilla biome overrides. Keep a separate 26.2-compatible pack for them until that migration is designed.
