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

The dimension is called **The Null Domain**.

Build order and open questions live in [ROADMAP.md](ROADMAP.md); this section is
the design itself.

### Potion pipeline

Two potions, brewed in sequence. Both still require Nether Wart for the Awkward
base, same as every vanilla potion.

- **Potion of the Devil's Trumpet** (Awkward Potion + Datura Seeds). Pitch
  black. Inflicts the same non-lethal poisoning as eating raw Datura Seeds, but
  stronger: *all eight* symptoms occur within the potion's duration, in random
  order and at random lengths (minimum 10s each), starting 10 seconds after
  drinking. 3 minutes base, 8 minutes with Redstone.
- **Potion of Attunement** (Devil's Trumpet + Fermented Spider Eye). Fermented
  Spider Eye is vanilla's established "corrupt this potion" reagent, which is
  exactly what this step is narratively. The first time one is ever completed in
  a world it triggers a one-time server-wide thunderstorm and lightning flash.
- Both support **Splash** and **Lingering** (vanilla's container recipes are
  generic, so this is free) and both extend with **Redstone**.
- Splash/Lingering Attunement means a player can be attuned — or dosed with the
  trip — **involuntarily**. This is intended.

Attunement is deliberately *the same poisoning* as Devil's Trumpet, at full
strength. That is the whole trick of the design: there is no separate "the
dimension hurts you" rule, because being in the Null Domain simply requires
being poisoned.

### Entry: the altars

Naturally-spawning **altars**, rarity comparable to villages. Ominous
black/dark, warlock-style; blocks unbreakable in survival. The structure is
authored in-game and imported as NBT rather than hand-written.

The ritual needs all four, and the bell is the trigger:

1. The player is **currently under** the Potion of Attunement's effect (having
   drunk it before is not enough)
2. **Candles** placed and lit on the altar
3. **Nighttime**
4. A **bell struck** near the altar — exactly 3 seconds later, everyone in
   range who is under the effect is taken to the Null Domain

Missing or wrong conditions each give their own narrated failure, so the ritual
teaches itself. At dawn, candles simply go out — not destroyed, not consumed.

**There is no visible or persistent portal.** Entry is a personal event,
perceptible and enterable only by players currently under Attunement within
range. A sober bystander sees nothing but someone lighting candles, ringing a
bell, and then not being there any more.

### Inside the Null Domain

- Players are always under Attunement, so no in-place danger mechanic is needed
- The Darkness in Attunement's final 10 seconds is the built-in warning that
  time is nearly up
- The moment Attunement expires the player is instantly returned to their
  overworld spawn — no death, no Wither, no lingering danger. The trip is simply
  over, unless they redose in time
- Returning to the *specific* altar you came in by is reserved for a separate,
  not-yet-built voluntary exit (a door). Expiry and voluntary exit are different
  things

### Datura Seeds — exactly two functions

1. Brewing, via the pipeline above
2. Eaten raw, inducing the Datura Trip

Nothing else. Resist adding a third.

The **Datura plant** spawns in the dry biomes — savanna, desert and badlands — as
small, uncommon stands, and nowhere else. Keeping it off the temperate biomes
where players base preserves its "eerie, out-of-place weed" reading and makes
finding it a deliberate errand rather than an accident. The block grows on sand
and terracotta as well as dirt, both to survive those biomes and because real
datura is a weed of sandy waste ground.

### The allegory

The moral is blunt: **do not take deliriants.**

Everything occult in this mod is the delusion, not the setting. Real deliriant
poisoning is characterised by people wholly believing in places and entities
that are not there, and by an inability to tell that anything is wrong — so the
altar, the ritual, the demonic staging and the Null Domain itself are all
rendered exactly as the poisoned player experiences them, with no authorial
confirmation that any of it is real. Sober bystanders see a person lighting
candles and then behaving as though they had gone somewhere.

The mod never resolves whether the player travelled anywhere or simply became
unreachable, because to the player it makes no difference. What it does confirm
is the cost: the deeper they push the worse it gets, the only thing keeping them
alive in there is the poison itself, and the poison is on a timer. The depth
mechanic delivers this on its own — no dialogue, no quest system, no narrator
telling anyone what to think.

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
world-gen yet. Potion of Attunement is brewable (see the potion pipeline above) and the first time one is ever
completed in a world it triggers a one-time server-wide thunderstorm plus a lightning flash. Its
survival mechanic is live: a per-tick presence check ejects any survival/adventure player who is in
the Null Domain without the effect active — teleporting them to their respawn point (bed/anchor,
else world spawn) rather than killing them. This covers both walking in unprotected (a door leads
nowhere for the unattuned) and the potion expiring while you're inside (the trip is simply over).
Creative and spectator are exempt.

Datura Seeds are also edible, which sets off the "datura trip", driven by a server-side tick
sequencer since vanilla can't chain one effect into a different one. There are seven symptoms:
Dry Mouth, Nausea, Tachycardia, Darkness, Poison, Weakness and Psychosis. Eating raw seeds gives
10 seconds of nothing, then Dry Mouth always leads, then four more drawn at random from the other
six — five events, each at its natural duration, separated by 20 seconds of calm. Drinking Devil's
Trumpet instead runs *all seven*, in random order at random lengths, packed back to back inside the
potion's own window.

Dry Mouth / Tachycardia / Psychosis are custom effects named for the symptom; Psychosis and the
Attunement effect each apply a real vanilla effect underneath (night vision, darkness) purely to
borrow its visual, hidden from the HUD and inventory so the player sees one effect with one name.
Screen desaturation is a post-process chain reusing vanilla's `color_convolve` program. Tachycardia
surges a heartbeat on arrival and again at irregular intervals.

Psychosis carries the hallucinations: 3–6 noises per minute from a pool (cave ambience, a zombie
breaking a door, soul sand valley additions, wither skeleton, creeper fuse, a descending note-block
run, and three original synthesised whisper takes); an 85% chance of a silent, translucent,
black-eyed figure appearing partway through, visible only to the afflicted player, staring without
ever looking down; and a warped soundscape — every sound the *outside world* makes is pitched down,
randomly detuned, and 12% of the time simply never arrives. The hallucinated sounds themselves come
through clean, which is the point: the voices are the only thing you hear clearly.

Every trip sound is delivered privately to the one player and bound to them so it travels with them
— none of it is audible to anyone else. For the whole trip, including the gaps between symptoms,
black fractal cracks creep in from the corners of the screen, fading in and out over 8 seconds each
way; an invisible marker effect carries that state to the client, since the sequencer is
server-side.

Datura now spawns in the wild (savanna/desert/badlands), so the seeds → Devil's Trumpet →
Attunement chain is obtainable in survival rather than creative-only. Still just design, not built:
the altar ritual and the Null Domain's altar-only entry. The Rift Door still works as an overworld
entrance for now (it's the only way in until the altars exist) and the dimension is still registered
as `dimdescent:rift` — both are known and tracked. See
[ROADMAP.md](ROADMAP.md) for the ordered build plan, and the `mc-modding-notes` skill
(`.claude/skills/mc-modding-notes/`) for implementation details, gotchas, and conventions.