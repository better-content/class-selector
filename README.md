# Class Selector (Forge 1.20.1, Kotlin)

A Forge mod that forces new players to pick a class before starting:
- On first join, player is put into spectator mode.
- Client is prompted to open class menu with `K`.
- Class menu shows title, blurb, and description.
- Players lock a class and lock a respawn point separately, then press `Begin`.
- Kits are JSON-defined in `config/classselector/kits.json`.
- Alternatively, `config/classselector/embark.json` can switch onboarding to an embark-style point-buy item pool.
- Respawning returns the player to that saved starting location with the scripted sound and particle FX.

## Admin commands

- `/classselector resetrespawn <targets>` clears stored permanent respawn points for online players.

## Kit slot targeting

Each item entry can include an optional `slot` field:
- `inventory` (or omitted): normal inventory insertion.
- `offhand`: attempts to equip directly into the offhand slot.
- `armor:head`, `armor:chest`, `armor:legs`, `armor:feet`: attempts to equip directly into armor slots.
- `curio:<identifier>`: attempts to equip into the first free Curios slot with that identifier (example: `curio:charm`, `curio:ring`).

If a requested slot is missing or full, the item falls back to player inventory.

## Embark point-buy mode

`config/classselector/embark.json` controls the selection mode:
- `"mode": "class"` keeps the existing fixed class selector.
- `"mode": "embark_points"` replaces classes with a point-buy supply screen.
- `"pointQuota"` sets how many points each player can spend.
- `"items"` defines the purchasable pool.

Each embark item supports:
- `id`: unique purchase id.
- `title`: display name.
- `category`: optional grouping label shown in the supply row.
- `blurb`: short display text.
- `item`: item id with optional NBT, using the same syntax as kit items.
- `count`: stack count granted per purchase.
- `cost`: points spent per purchase.
- `maxPurchases`: per-player purchase cap for that entry.
- `slot`: optional target slot using the same values as kit slot targeting.

## Dependencies

Curios is required and loaded as a Forge dependency.

## Release install

Use the jar produced at `build/libs/classselector-<version>.jar`.

- Server: install Class Selector, Kotlin for Forge, and Curios in the server `mods/` folder.
- Client: install the same mod set and matching Forge `47.4.x` on Minecraft `1.20.1`.

Project URLs:
- Repository: `https://github.com/geraldsummers/classselector`
- Issue tracker: `https://github.com/geraldsummers/classselector/issues`

## Test commands

Use Java 17 for all commands.

### Verification
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./gradlew verifyFast
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./gradlew verifyFull
```

`verifyFast` runs the JVM and coverage lane. `verifyFull` adds the headless Forge GameTest pass.

### Local client
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./run-client
```

`run-client` is a small helper that runs `gradle runClient` for convenience.

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).
