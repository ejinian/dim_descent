# dim_descent

A Minecraft mod heavily inspired by Dimensional Doors — liminal horror dungeon
exploration with escalating depth-based difficulty. This is a fresh build,
not a fork; full creative control over mechanics DD didn't get right.

## Core Concept
Players find rifts/doors that lead into pocket dimensions. Unlike DD, this mod
is built around **descent** — dimensions have a depth axis, and the deeper you
go, the harder rooms get (tougher enemies, better loot, more unstable/hostile
environment). The goal is a legible "how deep am I" push-your-luck loop, not
just a flat pool of scary rooms.

## What we're improving on vs. Dimensional Doors
- **Room variety**: hundreds of non-repeating rooms via modular/procedural
  composition, not a finite static pool
- **Depth as a first-class mechanic**: visible signal for how deep the player
  is (fog, ambient sound, color grading), tied directly to difficulty and loot
- **Enemy variety scaled to depth**: depth-tiered enemies, not reskins
- **Loot tied to risk**: better guaranteed loot the deeper/more dangerous
- **Intentional rift placement**: rifts tied to structures/biomes/player
  actions rather than fully random overworld spawns
- **Traversal tension**: meaningful risk/reward for pushing deeper vs.
  retreating, rather than rifts just being an escape-the-maze chore

## Progression Framework

### Overworld hook: the Attunement Gate

The only way into a rift (for now) is a naturally-spawning structure called the
**Attunement Gate** — an abandoned industrial facility built around a sealed
rift door. Spawn rarity target: comparable to villages (placeholder, tune once
world-gen is actually built). Aesthetic: abandoned/liminal industrial — should
read as unsettling and deterring, not inviting. Built primarily from vanilla
blocks (stone brick, quartz block, iron block, gray concrete, deepslate,
chains, hoppers, pistons — dead/non-functional automation implying the
facility stopped abruptly), plus one new block:

- **Dark Iron Bars** (custom block): visually vanilla `iron_bars` (copy the
  real model/blockstate/properties from decompiled vanilla source, don't
  guess), just a darker texture, and hardness bumped to obsidian-tier. Fully
  encases the rift door inside the structure — the door is physically
  inaccessible until every bar around it is broken. This is the "gear and
  patience" gate: no shortcutting it with early tools.

### Survival gate: Potion of Attunement

Even after breaking into the gate, entering the rift itself is lethal without
protection — this is deliberate, and for now there is no in-game explanation
of why (a lore/quest book explaining the mechanic is planned for later, but
intentionally does not exist yet — the first playtesters are meant to find
this out the hard way).

- **Potion of Attunement**: gray potion, brewed normally (Awkward Potion +
  Datura Seeds, the same slot Blaze Powder/Ghast Tear/etc. occupy for other
  potions — still requires Nether Wart first for the Awkward Potion base,
  same as every other potion).
- **Datura Seeds**: harvested from a new plant resembling real-world datura
  (poisonous, eerie flowering plant — thematically deliberate). Spawn rarity
  comparable to existing uncommon vanilla flowers/plants — exact comparison
  TBD at implementation time.
- **Effect**: while the potion's duration is active, the player can enter and
  exist inside rift dimensions safely. If the duration runs out while the
  player is still inside a rift, or if a player enters a rift with no active
  potion at all, they are instantly killed.

### Environmental storytelling: the village lectern room

Every village gets one additional small room: a table with a lectern and a
book. The book is a terse, menacing account of "the legend of the industrial
entrance" (the Attunement Gate) — foreshadowing without explaining game
mechanics. This is the primary lore-delivery mechanism for now; no NPCs, no
quest system.

### The allegory

The Attunement Gate ruins are the remains of a relatively recent industrial
operation that tried to study, contain, or extract from the rifts, and failed
by pushing too deep. The ruins are the warning; the player repeating the same
mistake (pushing deeper into a rift for better loot despite escalating danger)
is the story playing out live, entirely through the existing depth mechanic —
no dialogue or quest system needed to deliver the theme. What separates this
from generic "backrooms"-style liminal horror is the contrast: the entrance
performs order and control (clinical, engineered, industrial), while what's
behind the gate is the opposite (unstable, hostile, worse with depth) — the
horror is "someone thought they could master this, and failed," not "an
inexplicable place exists."

## Tech Stack
- Minecraft 1.21.1
- NeoForge (ModDevGradle)
- Java 21
- IntelliJ IDEA

## Status

Core dimension-travel loop works end to end: a custom Rift dimension (Nullstone floor,
Forsaken Fiber unbreakable boundary), and a Rift Door with a themed portal effect that
generates a single shared exit door in the rift the first time any door is used anywhere,
remembering per player which door to send them back through when they walk out — a door
placed by hand inside the rift just goes to overworld spawn instead. Dark Iron Bars and
Datura (with Datura Seeds) exist as placeable/harvestable blocks, though neither spawns via
world-gen yet. Potion of Attunement is brewable (Awkward Potion + Datura Seeds, Redstone for
the long variant) and the first time it's ever completed in a world it triggers a one-time
server-wide thunderstorm plus a lightning flash. Its survival mechanic is live: a per-tick
presence check kills any survival/adventure player standing in the rift without the effect
active, which covers both walking in unprotected and the potion expiring while you're inside.
Creative and spectator are exempt, and the kill bypasses armour, enchantments and status
effects so it can't be tanked.

Datura Seeds are also edible, which sets off the "datura trip" — an eight-stage poisoning
sequence (Dry Mouth, Nausea, Tachycardia, Darkness, Poison, Weakness, Hysteria, Hallucination),
driven by a server-side tick sequencer since vanilla can't chain one effect into a different one.
Dry Mouth / Tachycardia / Hysteria are custom effects named for the symptom; Hysteria and the
Attunement effect each apply a real vanilla effect underneath (night vision, darkness) purely to
borrow its visual, hidden from the HUD and inventory so the player sees one effect with one name.
Screen desaturation is a post-process chain reusing vanilla's `color_convolve` program. The final
stage spawns a silent, faceless, translucent figure that only the tripping player can see and that
stares without ever looking down, vanishing when the stage ends or the player looks away. All
stage durations are currently clamped to 10s by `DaturaTrip.DEBUG_UNIFORM` and fire in a fixed
order; real durations and randomised ordering are the next step.

The Attunement Gate structure, world-gen placement for Dark Iron Bars/Datura, and the village
lectern lore room are still just design, not built. See the `mc-modding-notes` skill
(`.claude/skills/mc-modding-notes/`) for implementation details, gotchas, and conventions.