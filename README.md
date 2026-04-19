# Class Selector (Forge 1.20.1, Kotlin)

A Forge mod that forces new players to pick a class before starting:
- On first join, player is put into spectator mode.
- Client is prompted to open class menu with `K`.
- Class menu shows title, blurb, and description.
- Players lock a class and lock a respawn point separately, then press `Begin`.
- Kits are JSON-defined in `data/classselector/class_kits/kits.json`.
- Respawning returns the player to that saved class location with the scripted sound and particle FX.

## Admin commands

- `/classselector resetrespawn <targets>` clears stored permanent respawn points for online players.

## Kit slot targeting

Each item entry can include an optional `slot` field:
- `inventory` (or omitted): normal inventory insertion.
- `offhand`: attempts to equip directly into the offhand slot.
- `armor:head`, `armor:chest`, `armor:legs`, `armor:feet`: attempts to equip directly into armor slots.
- `curio:<identifier>`: attempts to equip into the first free Curios slot with that identifier (example: `curio:charm`, `curio:ring`).

If a requested slot is missing or full, the item falls back to player inventory.

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

### Unit tests
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./gradlew test
```


### Local client
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./run-client
```

`run-client` is a small helper that runs `gradle runClient` for convenience.

### Headless Forge GameTests
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./gradlew headlessGameTest
```

This runs Forge's `runGameTestServer` task through a verification alias.
