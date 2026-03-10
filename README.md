# Class Selector (Forge 1.20.1, Kotlin)

A Forge mod that forces new players to pick a class before starting:
- On first join, player is put into spectator mode.
- Client is prompted to open class menu with `K`.
- Class menu shows title, blurb, and description.
- Kits are JSON-defined in `data/classselector/class_kits/kits.json`.

## Kit slot targeting

Each item entry can include an optional `slot` field:
- `inventory` (or omitted): normal inventory insertion.
- `armor:head`, `armor:chest`, `armor:legs`, `armor:feet`: attempts to equip directly into armor slots.
- `curio:<identifier>`: attempts to equip into the first free Curios slot with that identifier (example: `curio:charm`, `curio:ring`).

If a requested slot is missing or full, the item falls back to player inventory.

## Dependencies

Curios is required and loaded as a Forge dependency.

## Test commands

Use Java 17 for all commands.

### Unit tests
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH gradle test
```

### Headless Forge GameTests
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH gradle headlessGameTest
```

This runs Forge's `runGameTestServer` task through a verification alias.
