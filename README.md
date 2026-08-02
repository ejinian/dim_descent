# dim_descent

A liminal-horror dungeon mod for **Minecraft 1.21.1 / NeoForge**, heavily inspired by Dimensional
Doors but built from scratch rather than forked.

You find a datura plant. You brew it. You lie down. You wake somewhere that is not a place, and the
only thing keeping you there is the poison in your blood — which is on a timer.

> **The moral is blunt: do not take deliriants.**
>
> Everything occult in this mod is the delusion, not the setting. Real deliriant poisoning is
> characterised by people wholly believing in places and entities that are not there, and by an
> inability to tell that anything is wrong — so the altar, the demonic staging and the Null Domain
> itself are rendered exactly as the poisoned player experiences them, with no authorial confirmation
> that any of it is real. Sober bystanders see a person lie down in a bed and then behave as though
> they had gone somewhere.

Design and lore live in [CLAUDE.md](CLAUDE.md); the ordered build plan lives in
[ROADMAP.md](ROADMAP.md).

---

## The loop

1. **Find Datura** — an eerie white trumpet flower that grows only in savanna, desert and badlands.
2. **Brew it.** Awkward Potion + Datura Seeds → **Potion of the Devil's Trumpet**. Add Fermented
   Spider Eye → **Potion of Attunement**. (The first Attunement ever completed in a world triggers a
   one-time server-wide thunderstorm.) Both extend with Redstone and support Splash and Lingering —
   which means a player can be dosed, or attuned, *involuntarily*.
3. **Sleep.** Under Attunement, at night, in any bed — you don't skip to morning, you are pulled into
   the **Null Domain**.
4. **Descend.** Each room holds a **Nexus of Eternal Slumber**. The dark, tattered one takes you
   deeper. Its pale twin takes you back.
5. **Get out before the potion does.** When Attunement expires you are ejected to your respawn point.
   No death — the trip is simply over.

## The Null Domain

A shared pocket dungeon of hand-authored rooms, laid out on a 512-block grid and stamped lazily the
first time a bed opens them.

**Travel is keyed on beds, not players.** A given bed opens the same room forever, for everyone: sleep
in the bed in your house and you and every other player on the server arrive in the *same* room, today
and next month. The Domain is therefore one fixed graph the whole server explores together — a forest
of trees, one per waking-world bed, branching wherever a room holds more than one dark Nexus.

Inside, it is deliberately hostile to orientation:

- **Light does nothing.** Every block renders at full brightness whether or not anything lights it.
  Torches are decoration; you cannot light your way out.
- **The sky is pure black.** Not dark — absent. Nothing resolves in any direction.
- **Nothing lives there.** No mob spawns, by any means. The only thing you will ever see moving is a
  hallucination, and it belongs to you rather than to the place.

### Refusing the trip

Using the pale Nexus in an outermost room puts you back in the waking world, but backing out is never
free: you come to a few blocks from your bed rather than in it; that bed is permanently **corrupted**
and can never be slept in again; you get the comedown (nausea, dry mouth, weakness); and Attunement is
cleared outright.

You can leave the dream. You cannot leave the drug.

## The trip

Eating raw Datura Seeds sets off a **datura trip**, driven by a server-side sequencer. Seven symptoms:

| | |
|---|---|
| **Dry Mouth** | desaturates the world |
| **Nausea** | vanilla confusion |
| **Tachycardia** | a heartbeat only you can hear, surging at irregular intervals |
| **Darkness** | |
| **Poison** | non-lethal |
| **Weakness** | |
| **Delirium** | the hallucinations |

**Raw seeds**: ten seconds of nothing, then Dry Mouth always leads, then four more drawn at random
from the other six — five events at their natural durations, separated by twenty seconds of calm.
**Devil's Trumpet**: all seven, random order, random lengths, packed back to back.

**Delirium** carries the worst of it — a pool of noises (cave ambience, a door breaking, a creeper
fuse, a descending note-block run, and three original synthesised whisper takes); an 85% chance of a
silent, translucent, black-eyed figure appearing partway through, visible only to you, staring without
ever looking down; and a warped soundscape where every sound *the outside world* makes is pitched
down, randomly detuned, and 12% of the time simply never arrives. The hallucinated sounds come through
clean. That is the point: the voices are the only thing you hear clearly.

Every trip sound is delivered privately to one player and bound to them, so none of it is audible to
anyone else. Black fractal cracks creep in from the corners of the screen for the whole trip.

## Content

**Blocks** — Nullstone · Forsaken Essence · Dark Iron Bars · Datura · the altar set (Altar Stone,
Carved Altar Stone, Altar Heart, bricks, cracked bricks, slab, stairs) · Daemonlight (a 3D demonic
torch, placed unlit and lit with flint and steel) · three beds (dark Nexus, pale Nexus, corrupted).

**Items** — Datura Seeds (brewing ingredient, and edible if you're foolish) · **Almanacus Inferni
Abditi**, a readable grimoire found in altar chests, and the mod's only piece of written lore.

**Worldgen** — Datura in the dry biomes; **altars** at roughly village rarity, each with an adjoining
room of eight empty beds. The beds are the teaching device: find eight of them laid out beside a
demonic altar and you work out what beds are for here without being told.

## Building from source

```bash
./gradlew runClient
```

```bash
./gradlew test
```

The test suite is plain JUnit over the resource tree — no Minecraft launch, ~1 second. It enforces
that block models using transparent textures declare a see-through `render_type`, and that every
texture and model reference actually resolves. It exists because a deleted class once silently made
every transparent block in the mod render as opaque black, and nothing caught it but a screenshot.

Debug helpers: `/rift enter`, `/rift leave`.

## Authoring rooms

Setting up from scratch to build rooms? See **[CONTRIBUTING-ROOMS.md](CONTRIBUTING-ROOMS.md)** —
prerequisites, clone-to-running-client, WorldEdit, and how to hand a finished room over.


Rooms are the content, and the pool is where most ongoing work goes. A room is an ordinary Minecraft
build captured with vanilla **Structure Blocks** — no special tooling.

1. Build it in a creative world (48×48×48 max — the Structure Block limit).
2. Place exactly one **pale** Nexus of Eternal Slumber. This is both the entrance and the way back:
   players arrive beside it, facing into the room, so it doubles as the spawn marker and supplies the
   arrival direction. No marker block or per-room config file is needed.
3. Place one or more **dark** Nexus beds — each one becomes its own branch to its own room.
4. Capture with Structure Blocks (two in Corner mode, one in Save mode; the region is *exclusive* of
   the corner blocks) and save as `dimdescent:rooms/<name>`.
5. Copy the `.nbt` from `run/saves/<world>/generated/dimdescent/structures/` into
   `src/main/resources/data/dimdescent/structure/rooms/`.

The pool is discovered at runtime, so a new `.nbt` in that folder joins the rotation with no code
change.

## Status

The full loop works end to end: find datura → brew → sleep → explore → get out or get ejected.

Not built yet: **enemies**, **loot scaled to depth**, and the **depth axis** itself — room selection is
currently a flat random pick, so nothing gets harder the further you go. That is the next major piece
of work, and the mod's whole premise, so treat the current build as a traversal skeleton with the
atmosphere already on it.

## Credits

All textures, models and sounds are original to this project, including the synthesised whispers.
No assets are taken from Dimensional Doors or from Minecraft itself.

Mappings are the official Mojang names; see
[the mapping licence](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
