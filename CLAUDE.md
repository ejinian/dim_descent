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
- **Room variety**: a large pool of hand-authored rooms, branching rather than
  linear — each room can hold several onward beds, so the dungeon is a tree the
  whole server explores, not a finite corridor
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
  stronger: *all seven* symptoms occur within the potion's duration, in random
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

Attunement **supersedes** the raw poisoning rather than adding to it. Drinking it
while already tripping on seeds or Devil's Trumpet immediately clears every
symptom and starts Attunement's own opening 10 seconds of darkness. One dose
takes over from the other; the two never run at once.

### Entry: the Waking Dream

Entry piggybacks on vanilla's own sleep mechanic rather than adding anything new.

- **Under Attunement, at night, sleeping in ANY bed** does not skip to morning —
  it pulls the player into the Null Domain, framed as a nightmare they cannot
  wake from until the poison passes.
- **Under the raw poisoning** (seeds or Devil's Trumpet), sleep is simply
  refused, with a narrated line: *"I can't sleep right now, I feel strange."*
- Sober players sleep normally.

This is deliberately near-zero build cost — beds already exist everywhere — and
it is the most truthful mechanic in the mod. Real deliriant intoxication very
commonly produces exactly this: an inability to separate dream from waking life,
often with partial or total amnesia of the episode afterwards.

It also closes its own loop for free. Vanilla sleeping sets the player's spawn to
that bed, and expiry already ejects them to their spawn — so a player doses,
lies down, and wakes up in the very bed they lay down in, with the intervening
hours unaccounted for.

### Entry is no longer a ritual

The bell/candles/altar ritual is **cut**. The altar keeps no mechanical role.

### The altars — lore landmarks

Naturally-spawning **altars**, rarity comparable to villages. Ominous
black/dark, warlock-style. Authored in-game and imported as NBT rather than
hand-written.

The altar block set is **breakable but dropless** — it used to be unbreakable, but
the Null Domain's containment design needs those same blocks to look diggable (see
the Status section), and a block can't be unbreakable in one dimension and not the
other. Dropless keeps them from being farmed out of either place.

Each altar spawns together with an adjoining **room containing eight empty
beds** — both are one structure (`dimdescent:altar`), so they always appear as a
pair. The beds are the teaching device: a player who finds eight beds laid out
beside a demonic altar works out what beds are for here without being told.

The room also holds a chest containing the **Almanacus Inferni Abditi**, a book
with its own black-and-red binding — the mod's only piece of written lore.

### Inside the Null Domain

- Players are always under Attunement, so no in-place danger mechanic is needed
- The Darkness in Attunement's final 10 seconds is the built-in warning that
  time is nearly up
- The moment Attunement expires the player is instantly returned to their
  respawn point — no death, no Wither, no lingering danger. The trip is simply
  over, unless they redose in time
- The voluntary exit is the **pale Nexus**: it walks you back one room, and in an
  outermost room it puts you out of the trip entirely, at a cost (see the Status
  section). Expiry and voluntary exit remain different things — expiry is free and
  involuntary, refusing the trip is chosen and costs you

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
altar, the demonic staging and the Null Domain itself are all rendered exactly as
the poisoned player experiences them, with no authorial confirmation that any of
it is real. Sober bystanders see a person lie down in a bed and then behave as
though they had gone somewhere.

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
Forsaken Essence unbreakable boundary). The Rift Door is **fully retired** (block, block entity,
portal renderer and all — deleted); onward travel is now the **Nexus of Eternal Slumber**
(block `dream_bed`), a gray, tattered, zero-saturation bed. Right-clicked inside the Null Domain it
pulls you into the next room exactly as the door did; right-clicked anywhere else it detonates like a
vanilla bed used in the Nether/End (the lore: sleep is how you fell into the trip, so lying back down
is how you sink deeper — and the waking world can't hold the thing). Sleeping in it is impossible.
(The overworld detonation is temporarily gated off — `EXPLODE_OUTSIDE_DOMAIN` — while rooms are being
hand-authored in the overworld, so a stray click can't wreck a build.)

There are **two** Nexus beds, and they share a name on purpose — telling them apart is the whole
decision. The **dark, tattered** one takes you deeper. Its **pale, near-white** twin takes you back
one room, and is also the room's **entrance**: you always arrive beside it, so it doubles as the
spawn marker and gives arrival facing for free (no marker block, no per-room data file — the same
trick Dimensional Doors uses with its entrance door).

**Travel is keyed on beds, not players** (`BedLinkData`), which is what makes the Domain a shared
place. A bed opens the same room forever, for everyone: sleep in the bed in your house and you and
every other player on the server arrive in the same room, today and next month. One bed, one room —
never mixed, never a fresh copy. The Domain is therefore a single fixed graph the whole server
explores together, exactly as Dimensional Doors does it (its links live on the rift, not the
traveller). Two maps are written the moment a room is born: the bed → the room it opens, and that
room's pale bed → the bed that opened it. The pale Nexus just asks "what made me?" and goes there.

Using the pale bed in an **outermost** room — one opened by a bed in the waking world — refuses the trip
(`NexusReturn`). Backing out is survivable but never free: you come to a few blocks from your bed
rather than in it; that bed is permanently **corrupted** (wears the pale tattering, can never be
slept in again — "This bed does not look comfortable..." — breakable but dropless, and still a valid
respawn point); you get the comedown (nausea 10s, dry mouth and weakness 60s); and Attunement is
cleared outright. You can leave the dream; you cannot leave the drug. Planned gear will soften the
comedown, which is why it lives behind one method. The Null Domain is a
Dimensional-Doors-style pocket dungeon (`NullDomainRooms`, reverse-engineered from DD's own
pocket/grid code): every crossing in — and every Nexus bed used once inside — opens a fresh,
randomly-chosen room ~512 blocks away on a persisted spiral grid, stamped lazily on use. Five
code-generated room types (pillar hall, wide gallery, tall altar-heart chamber, low barred cells,
hall of bars) are picked at random; three can hold an altar-loot chest. Each room places one Dream
Bed against the far wall, so travel only ever leads deeper. (These code-generated rooms are interim
scaffolding — a hand-built room pool is being authored to replace them.) Dark Iron Bars are placed
as room decor (cages, screens, gateposts). Datura spawns in the wild (see below). Potion of
Attunement is brewable (see the potion pipeline above) and the first time one is ever completed in
a world it triggers a one-time server-wide thunderstorm plus a lightning flash. Its survival
mechanic is live: a per-tick presence check ejects any player who is in the Null Domain without the
effect active — every gamemode included, creative and spectator too — teleporting them to their
respawn point (bed/anchor, else world spawn) rather than killing them. This covers both walking in
unprotected (a door leads nowhere for the unattuned) and the potion expiring while you're inside
(the trip is simply over). Leaving is otherwise only `/rift leave` or that expiry — no door leads
out yet.

Datura Seeds are also edible, which sets off the "datura trip", driven by a server-side tick
sequencer since vanilla can't chain one effect into a different one. There are seven symptoms:
Dry Mouth, Nausea, Tachycardia, Darkness, Poison, Weakness and Delirium. Eating raw seeds gives
10 seconds of nothing, then Dry Mouth always leads, then four more drawn at random from the other
six — five events, each at its natural duration, separated by 20 seconds of calm. Drinking Devil's
Trumpet instead runs *all seven*, in random order at random lengths, packed back to back inside the
potion's own window.

Dry Mouth / Tachycardia / Delirium are custom effects named for the symptom; Delirium and the
Attunement effect each apply a real vanilla effect underneath (night vision, darkness) purely to
borrow its visual, hidden from the HUD and inventory so the player sees one effect with one name.
Screen desaturation is a post-process chain reusing vanilla's `color_convolve` program. Tachycardia
surges a heartbeat on arrival and again at irregular intervals.

Delirium carries the hallucinations: 3–6 noises per minute from a pool (cave ambience, a zombie
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
Attunement chain is obtainable in survival rather than creative-only.

Content built so far for the altar and its lore: a full altar block set (altar_stone, carved
variant, brick/cracked-brick/slab/stairs, and the altar_heart focal block); the **Daemonlight**, a
3D demonic torch that is placed unlit and lit with flint and steel (a `lit` blockstate drives both
light 7/0 and the red flame particle), crafted from one datura over one stick like a vanilla torch;
the **Almanacus Inferni Abditi**, a readable custom book in the register of a grimoire, found via a
chest loot table (`dimdescent:chests/altar`); and the authored altar-and-bed-room structure itself
(`dimdescent:structure/altar.nbt`), with its chest already pointed at that loot table.

The Domain's atmosphere is enforced at the dimension level, and all three rules are absolute:
**light does nothing** (`ambient_light: 1.0` plus `forceBrightLightmap`/`constantAmbientLight`, so
every block renders at full brightness and torches are decoration rather than a tool), **the sky is
pure black** (a custom `dimdescent:rift` `DimensionSpecialEffects` with `SkyType.NONE` and a fog
colour of `Vec3.ZERO` — it used to point at `minecraft:the_end`, whose purple starfield showed
through gaps and read as "somewhere"), and **nothing spawns** (`NullDomainSpawns` cancels
`FinalizeSpawnEvent` outright, the sole exception being the Hallucination, which is a symptom rather
than an inhabitant).

**Every room is caged, and the cage is a lie** (`RoomContainment`). Room blocks are all breakable
now, so digging out looks viable. On placement each room is shrink-wrapped in a single layer of
Nullstone that follows its exact outer shape — worked out by flooding air inward from outside and
stopping at solids, so only outward-facing surfaces are wrapped and interiors are untouched (which is
why **an authored room must be sealed**). Five blocks further out sits a containment box: an inner
Nullstone shell backed by unbreakable Forsaken Essence. A player can chew through the Nullstone and
reach the Essence, which is the point — the escape stays plausible right until it isn't. The box has
walls and a ceiling but deliberately **no floor**, and its walls run down to the dimension's minimum
build height, so there is nothing to stand on outside a room and nothing to tunnel under. That is
also why rooms are now stamped at `FLOOR_Y = 0`: anchoring both to the build floor is what closes the
last gap. Break a room's floor and you fall out of the world.

**Forsaken Essence** (renamed from Forsaken Fiber) is the cage's unbreakable outer shell, and its
animated texture is **generated, not drawn** — `tools/generate_forsaken_essence_texture.py` builds
24 frames from a sum of sine waves whose frequencies are all integers over the 16px tile and the
frame loop. That is what makes it tile seamlessly against neighbouring blocks on both axes *and*
loop without a visible snap; the script asserts all three seams and fails rather than shipping one.
Retune the look by editing `BASE`/`DEEP`/`CORE` and re-running it — never edit the PNG.

**Rooms are hand-authored** `.nbt` structures, and building them is the main ongoing work. Five
exist so far. The pool is discovered at runtime from `data/dimdescent/structure/rooms/`, so a new
file joins the rotation with no code change. Authoring happens in-game with WorldEdit (installed in
the gitignored `run/mods/`) — see the `room-authoring` skill and [WORLDEDIT.md](WORLDEDIT.md).

Still not built, and the mod's central premise: **the depth axis itself**. Room selection is a flat
random pick, so nothing gets harder, richer or stranger the further in you go — and with it,
depth-tiered enemies and loot scaled to risk. Treat the current build as a traversal skeleton with
the atmosphere already on it. The dimension is also still registered as `dimdescent:rift` rather
than `null_domain`; renaming would orphan saved data, so it is deferred and tracked.

See [ROADMAP.md](ROADMAP.md) for the ordered build plan, [README.md](README.md) for the outward-facing
summary, and the `mc-modding-notes` skill (`.claude/skills/mc-modding-notes/`) for implementation
details, gotchas, and conventions.