# Class Selector (Forge 1.20.1, Kotlin)

A Forge mod that forces new players to pick a class before starting:
- On first join, player is put into spectator mode.
- Client is prompted to open class menu with `K`.
- Class menu shows title, blurb, and description.
- Kits are JSON-defined in `data/classselector/class_kits/kits.json`.
- Includes a respawn hub system with exact scripted spooky SFX/VFX and spectator hub voting.

## Respawn hubs

`/rrhubs enable` turns on respawn hub routing. If a respawning player does not have a personal bed/anchor spawn, they are routed to the least-used finalized hub.

Hub FX match the ported script:
- crying obsidian is placed beneath the hub pad
- the same bell / end portal / warden / evoker sound stack is played
- the same scheduled `sculk_soul`, `sculk_charge`, `sculk_charge_pop`, and `soul_fire_flame` particle pulses are emitted for ~3 seconds

Voting flow:
- currently logged-in spectators without a class can run `/rrhubs suggest_here` to propose their current location
- `/rrhubs proposals` lists active proposals with clickable vote actions
- `/rrhubs vote <proposal>` casts or changes a vote
- when every currently logged-in unclassed spectator has voted and there is a clear winner, that proposal is finalized into a respawn hub automatically

Admin override commands:
- `/rrhubs add_here`
- `/rrhubs remove_here`
- `/rrhubs remove_index <index>`
- `/rrhubs clear`
- `/rrhubs list`
- `/rrhubs tp <index>`

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


### Local client
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH ./run-client
```

`run-client` is a small helper that runs `gradle runClient` for convenience.

### Headless Forge GameTests
```bash
JAVA_HOME=$HOME/.local/share/mise/installs/java/17.0.2 PATH=$JAVA_HOME/bin:$PATH gradle headlessGameTest
```

This runs Forge's `runGameTestServer` task through a verification alias.
