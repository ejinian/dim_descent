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

It also closes its own loop for free. The **Unicursal** proves its Hilbert-curve corridor never branches (every cell has exactly two
corridor neighbours bar the two ends) and that the shortest walk between the Nexus beds stays above a
floor — which catches an end pocket accidentally opening a shortcut between two arms of the curve.
Its beds are 10 blocks apart in a straight line and 66 on foot — about twenty seconds. An earlier cut
ran an order-4 curve end to end at 502 blocks and three minutes, which played as a commute rather
than a room; the fix was a smaller curve plus putting the dark Nexus partway along it instead of at
the far end, leaving 56 cells of corridor running on past the exit that the player never has to
enter. A maze you have completely traversed is solved; one that visibly continues past your exit is
not. The **Hypostyle** is the largest room the structure format can hold — 47³, the 48-block cap
minus one — and is a colonnade standing on a **Cantor dust**, which is chosen because a totally
disconnected set has a connected complement: every floor cell is walkable without designing a route.
Three iterations give 64 columns with aisles 1, 4 and 15 wide, so every view is a scaled copy of every
other. It asserts those three scales survived integer rounding, and that the floor really is one
connected region. It is also the first authored room with **three dark Nexus beds** — a genuine
three-way fork rather than a corridor with one way on. Its fractal was also a mistake worth
recording: a Cantor dust used as a floor *plan* and extruded into columns is **invisible from inside**,
because the player stands in the one place the pattern cannot be seen. The **Lattice** is the
correction — a level-3 3D Cantor dust, 512 unconnected blocks recursing in all three axes, suspended
in a box whose every face is Nullstone. Flat black with no shading gives the eye nothing to judge
distance against, so the walls read as absence and the blocks appear to hang in nothing; nothing in
the room is load-bearing, connected, or a surface. A Sierpinski tetrahedron was the obvious pick and
is the wrong one, because its projection along every coordinate axis is a filled square and it
collapses into a slab viewed square-on — a Cantor dust projects to a Cantor dust on all three. The Lattice in turn could never fill its own
centre — deleting the middle third at every level is what a Cantor set *is* — so the **Knot** answers
that with the opposite kind of object: an order-3 3D **Hilbert curve** rendered as a one-block pipe on
a 3-block pitch, 1534 blocks of single unbranching line folded through the entire volume at 14%
density, centre included, because a space-filling curve passes within a fixed distance of every point
by construction. It verifies the curve rather than trusting it (all 512 cells visited exactly once,
every step adjacent on one axis — Skilling's transform is easy to get subtly wrong and a wrong one
still looks plausible), and since 14% density is unwalkable it drives a three-wide brick bridge
straight through the middle, leaving the severed pipe ends hanging as the feature. The **Pyramid** is the one non-fractal of the
set — a hollow stepped pyramid in altar brick with a single lava source in the underside of its
capstone, falling 42 blocks into a basin sunk in the floor — and it turns on one constant: `RISE`, the
blocks the wall climbs before stepping one inward. At 1 the interior is a 45° staircase the player
walks up and the room becomes a ramp; at 2 every ledge is a two-block riser, unclimbable, and the
space stays something you look up into. The lava's containment is arithmetic rather than luck: the
topmost interior cell is the one under the capstone, where the wall ring has closed to half-width 1,
so it has brick on four sides and above with the only opening downward — asserted, along with the
fall being unobstructed the whole way. The **Throat** is the first room to exploit the fact that the
pale Nexus fixes arrival position *and* facing. It is a circular bore tapering from 11 across to 4
over thirty-four blocks with the floor rising to meet it, so the eye — which assumes a constant bore —
puts the far end at about ninety-four. Walk back and the taper runs the other way and it reads far
shorter than it is, so out and back feel like different distances. Two things are asserted: the bore
never widens (which would invert the illusion), and the dark Nexus sits off the sightline in the end
chamber, since anything of known size at the far end gives the scale away.

It took three cuts, and the two failures are the lesson. Rectangular resolved in about a second,
because **a rectangular tapering corridor has a wall/ceiling join running its whole length** — a
straight edge the eye measures along, which gives the taper away. Circular-but-floored was no better:
a wide flat floor removes most of the lower half of a bore, and everything that makes a circle look
circular lives there. The bore is now a **complete** circle crossed by a three-wide catwalk suspended
on the axis with open bore beneath, at radius 8.5 rather than 5.5 (a discretised circle of radius 5
is an octagon), with a circumferential rib every five blocks stating the circle outright instead of
leaving the eye to infer it. Only the *underside* is lined in Nullstone — so looking over the catwalk
edge gives void while the rest of the ring stays brick and legible; an all-black bore is a circle
nobody can see. On top of
that the bore is **rifled** — three helical ribs, 2.5 turns, winding faster as it narrows (`t**1.6`).
Moving along the axis of a helix makes the ribs appear to rotate, and because the player is the thing
moving, the rotation reads as their own. The ribs stop three blocks above the floor so the walkway
stays clear and the spin happens entirely in peripheral vision, which is considerably worse than
something you can look at directly. Vanilla sleeping sets the player's spawn to
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
why **an authored room's walls and ceiling must be sealed**). The **floor is exempt on purpose**: the
flood is seeded from the search volume's sides and top but never its bottom plane, because
`clampToWorld` pins that plane to the world floor — which is the room's own floor layer. Seeding it
would treat any hole in a room's floor as a way in and Nullstone-coat the whole interior. Skipping it
is what actually delivers the intent, and it makes a deliberate drop-into-the-void gap a supported
thing to author — one of the few genuinely lethal features available in a dimension where nothing
spawns. Five blocks further out sits a containment box: an inner
Nullstone shell backed by unbreakable Forsaken Essence. A player can chew through the Nullstone and
reach the Essence, which is the point — the escape stays plausible right until it isn't. The box has
walls and a ceiling but deliberately **no floor**, and its walls run down to the dimension's minimum
build height, so there is nothing to stand on outside a room and nothing to tunnel under. That is
also why rooms are now stamped at `FLOOR_Y = 0`: anchoring both to the build floor is what closes the
last gap. Break a room's floor and you fall out of the world.

**Allstone** is Nullstone's polar opposite: pure white, identical in every other respect (instabreak,
dropless, flat and untextured). Both are deliberately featureless — every other block in the mod has
grain to read, and these two have none, so a surface built from them gives the eye nothing to measure
distance or scale against. That is what makes a Nullstone void look bottomless and an Allstone room
look like it has no far wall; adding noise "to break it up" would undo the only thing they are for.
Both break with a **stone** sound rather than glass, which read as fragile scenery for what are walls
and floors. No room uses Allstone yet — it exists for interiors that should feel clinical rather than
ruined.

Getting a *flat white* block took a custom model, and the reason is worth remembering: Minecraft
shades every face by direction (top 1.0, sides 0.8/0.6, bottom 0.5), darkens corners with ambient
occlusion, and multiplies by the lightmap. **Nullstone survives all three because black times
anything is black.** White does not, so plain `cube_all` Allstone read as quartz. `block/allstone.json`
therefore declares its own element with `"shade": false`, `"ambientocclusion": false` and NeoForge's
`"neoforge_data": {"block_light": 15, "sky_light": 15}`, which pins the lightmap to full. That is
render-only — the block still emits no light. Any future block that must look unlit and unshaded
needs the same three, and none of them are reachable through a vanilla parent model.

The **Gibbet Chain** (`gibbet_chain`) is Dark Iron Bars' hanging sibling — vanilla's `ChainBlock` is
public, so axis rotation and waterlogging come free, and it is stone-tier rather than the bars'
diamond-tier since a chain needing a diamond pick is silly. Its texture is **drawn from scratch by
`tools/generate_gibbet_chain_texture.py`**, not traced from vanilla's — the mod ships none of Mojang's
art — using dark_iron_bars' own three metal tones so the pair match, with old blood dried into the
links. Two things it asserts: the *metal* mask is period-8 in y, because chains hang in columns and a
non-periodic tile shows a seam at every block boundary; and every blood pixel lands on metal, since
blood on a transparent pixel of a cutout texture hangs in mid-air beside the chain. The blood is
deliberately exempt from the periodicity check — repeating it would look printed rather than used.

**Forsaken Essence** (renamed from Forsaken Fiber) is the cage's unbreakable outer shell, and its
animated texture is **generated, not drawn** — `tools/generate_forsaken_essence_texture.py` builds
24 frames from a sum of sine waves whose frequencies are all integers over the 16px tile and the
frame loop. That is what makes it tile seamlessly against neighbouring blocks on both axes *and*
loop without a visible snap; the script asserts all three seams and fails rather than shipping one.
Retune the look by editing `BASE`/`DEEP`/`CORE` and re-running it — never edit the PNG.

**Rooms are hand-authored** `.nbt` structures, and building them is the main ongoing work. Twelve exist
so far (`hallway`, `hangul`, `left`, `t`, `u`, plus `spiral`, `rotunda`, `lavafall`, `basin`,
`oubliette`, `causeway` and `carpet`). The pool is discovered at runtime from `data/dimdescent/structure/rooms/`, so a new
file joins the rotation with no code change — which also means a bad `.nbt` reaches players with no
compile error to catch it. **`tools/verify_room_nbt.py` is the gate**: a read-only NBT parse checking
the room is sealed (flooding air from outside the box and asserting it cannot reach the space above
any Nexus bed — `RoomContainment`'s own algorithm), has exactly one pale bed and at least one dark
bed, fits in 48³, and caught no stray terrain. Run it after hand-tweaking too, not just on rooms from
a collaborator: a generator proves its own output is sealed, but nothing proves it still is once
someone has dug a hole in the floor to see what is under it. Authoring happens in-game with WorldEdit
(installed in the gitignored `run/mods/`) — see the `room-authoring` skill and
[WORLDEDIT.md](WORLDEDIT.md).

**`tools/` is where anything the mod can't draw by hand gets generated**, and the convention is the
same in every case: a small Python script owns the artefact, the artefact is never edited directly,
and the script *asserts its own invariants* so a bad constant fails in the terminal rather than
shipping. `generate_forsaken_essence_texture.py` proves its own x/y tiling and animation loop, and
`generate_void_stone_textures.py` emits Nullstone and Allstone together and proves every channel of
every pixel sums to 255 — "polar opposite" as a test rather than a description. Seven room scripts
(`generate_spiral_function.py`, `generate_basin_room.py`, `generate_causeway_room.py`,
`generate_oubliette_room.py`, `generate_carpet_room.py`, `generate_unicursal_room.py`,
`generate_hypostyle_room.py`, `generate_lattice_room.py`, `generate_knot_room.py`, `generate_pyramid_room.py`, `generate_throat_room.py`) emit
thousands of relative `setblock` lines as a **datapack function** into the builder world
(`/function build:<name>`), which is the only practical way to build a shape defined per block. These
functions live in the builder world's datapack and must never ship in the mod's own `data/`.

Every room generator proves — using the same flood-fill `RoomContainment` runs at placement — that
the room is **sealed**, which is the one authoring rule the shrink-wrap depends on, plus whatever
else that room can get wrong. The **Basin** checks it is walkable (no floor step over 1) and passable
(headroom ≥ 3); its shape comes from mirroring the ceiling against the floor (`CLEARANCE - h(r)`, not
`+`), so headroom swings by twice the ripple amplitude — a 13-block vault pinching to a 3-block
crawl. The **Causeway** checks that its walkway is one connected path reachable from the entrance
(otherwise part of the room is decoration the player can only look at), that both beds stand on it,
and that its single lava source is fully enclosed at walkway level so it cannot spread. Its whole
design is one block of elevation: an all-Nullstone room renders perfectly flat, so a floor is
indistinguishable from a hole, and a lit brick walkway one block above it reads as a bridge over
void. Stepping off is a one-block drop onto a floor the player was certain was not there. The
**Oubliette** checks headroom never drops under the 2 blocks a player occupies, the dark bed is
reachable on foot from the pale one, and the walk between them is at least `MIN_PATH` long — that
last one catches the open doors drifting onto the same side of each nested ring, which collapses the
labyrinth into a straight line without breaking anything a seal check would notice. The **Carpet**
proves its Sierpinski floor is one connected surface and that the dark Nexus is reachable on foot
(with falling) from the pale one. Its fractal was chosen for exactly that connectivity: the carpet is
a connected set whose complement is not, so filled-as-floor is walkable at every scale, while the
inverse — walls on the carpet — shatters the walkable space into eight sealed chambers. Self-similar
geometry also defeats scale judgement outright, which is the most liminal thing a room can do for
free.

Still not built, and the mod's central premise: **the depth axis itself**. Room selection is a flat
random pick, so nothing gets harder, richer or stranger the further in you go — and with it,
depth-tiered enemies and loot scaled to risk. Treat the current build as a traversal skeleton with
the atmosphere already on it. The dimension is also still registered as `dimdescent:rift` rather
than `null_domain`; renaming would orphan saved data, so it is deferred and tracked.

## Cool build ideas

A backlog for `tools/` generators — things the per-block-maths toolchain makes possible that nothing
else in the mod could do. Roughly ordered by appetite, not by difficulty.

1. **Anamorphic room.** The pale Nexus fixes arrival position *and* facing, so the player's eye
   position on entry is known exactly. Ray-cast from it and scatter blocks through the void so they
   align into a coherent image — a doorway, a figure, a word — from that one spot, collapsing into
   meaningless debris the moment they step aside.
2. **Forced-perspective corridor.** Walls, floor and ceiling converging so a short hall reads as a
   long one. **Built — see the Throat.**
3. **Cellular automaton growth.** Seed a 3D CA, run N steps, freeze it. Non-repeating and
   non-architectural: reads as something that *grew* rather than something built. The only organic
   thing the pool would have.
4. **Gyroid.** The triply-periodic minimal surface, `sin x cos y + sin y cos z + sin z cos x`. A thin
   shell around the zero level set splits space into two interwoven labyrinths that never meet.
   (Note the threshold: the surface is at 0, not at 0.7 — that gives disconnected blobs.)
5. **Voronoi cavern.** Chambers on Voronoi cells, walls on the boundaries. Irregular but obviously
   deliberate — "someone planned this and I cannot tell why".
6. **Möbius walkway.** A band with a half twist in a tall shaft; you walk what was the underside.
7. **Penrose staircase.** Same viewpoint lever as (1) — an impossible object that resolves only from
   the arrival point.
8. **Droste room.** The room contains a 1/3-scale model of itself, containing a 1/9. The dark Nexus
   sits in the innermost, exactly where the next copy should go and cannot fit; the recursion
   terminating on Minecraft's block size *is* the horror.
9. **Note-block floor.** Pressure plates over tuned note blocks so crossing the room plays a
   descending phrase. Vanilla blocks only, no code, and it plugs into the existing sound design.
10. **A depth-tier generator.** One script emitting the same room at five tiers — tighter geometry,
    more decay, more hostile. Less flashy than the rest and worth more than all of them, because it
    is the actual unbuilt half of the mod.

(1) and (7) are the standouts, and for the same reason: the pale bed makes arrival deterministic.
That is a lever nothing has used yet, and it enables illusions that only work because we know exactly
where someone is standing.

See [ROADMAP.md](ROADMAP.md) for the ordered build plan, [README.md](README.md) for the outward-facing
summary, and the `mc-modding-notes` skill (`.claude/skills/mc-modding-notes/`) for implementation
details, gotchas, and conventions.