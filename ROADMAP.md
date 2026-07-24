# Roadmap

Build order for the Progression Framework in [CLAUDE.md](CLAUDE.md). That file is
the design; this one is the sequence and the loose ends.

Ordered so that each phase leaves the mod in a coherent, testable state.

---

## Phase 1 — Potion pipeline ✅

- [x] **Potion of the Devil's Trumpet**: Awkward + Datura Seeds, pitch black
- [x] Drinking it starts the trip 10s later, with all 7 symptoms compressed into
      the potion's duration at random order and random lengths (min 10s each)
- [x] **Potion of Attunement**: Devil's Trumpet + Fermented Spider Eye
- [x] Redstone extends both (3 min → 8 min)
- [x] Splash and Lingering for both (free — vanilla's container recipes are generic)
- [x] First-ever Attunement brew still triggers the one-time thunderstorm

## Phase 1b — Symptom pass ✅

- [x] Renamed Hysteria → **Psychosis** (hysteria is an obsolete, discredited diagnosis that
      describes emotional excess rather than hallucination)
- [x] Folded the Hallucination stage into Psychosis, at 85% odds, arriving partway through
      rather than at onset — seven symptoms now, not eight
- [x] Psychosis gets a 20s floor in potion trips, and its first noise now comes sooner than
      subsequent ones, so the symptom can never end before anything is heard
- [x] Tachycardia's heartbeat repeats at irregular intervals rather than firing once
- [x] Warped soundscape during Psychosis: pitch-down, random detune and 12% dropout, applied via
      `PlaySoundEvent` to every non-music sound the outside world makes. The hallucinated sounds
      are deliberately exempt — they're the one thing meant to come through clearly

## Phase 2 — Make the chain reachable ✅

The whole chain was creative-only until Datura had no way to spawn; now it does.

- [x] World-gen placement for **Datura**: small, uncommon stands (`random_patch`,
      8 tries) at `rarity_filter` chance 12, gated to the dry biomes via a
      `neoforge:add_features` biome modifier and a `#dimdescent:has_datura` biome tag
- [x] Biomes: savanna, desert and badlands families (arid, off the temperate
      beaten path — the "eerie, out-of-place weed" reading)
- [x] `DaturaBlock` widens valid ground to sand and terracotta, or it would have
      silently placed nothing in desert/badlands (a plain flower only takes dirt)
- [ ] **Needs a human**: confirm in-game that stands actually generate in fresh
      savanna/desert/badlands chunks, and that the full survival path works end to
      end (find Datura → seeds → Awkward → Devil's Trumpet → Attunement)

## Phase 3 — Null Domain behaviour (partial)

Aligns the shipped dimension with the new design.

- [x] Replaced instant-death-without-Attunement with **ejection to the player's
      respawn point** (`RiftEjectionEvents`, was `RiftLethalityEvents`). One per-tick
      check still covers both halves: walking in unattuned bounces you out on the
      next tick, and the effect expiring inside ejects you the tick it ends. Death
      was a punishment that taught "don't go in"; a hard timer teaches "how much
      further dare I push", which is the push-your-luck loop the Core Concept asks for
- [x] Retired the `dimdescent:rift_unattuned` damage type, its three `bypasses_*`
      tag files, and the death message — nothing kills any more, so nothing used them
- [x] **Eject applies to every gamemode** now (standing instruction) — the creative
      and spectator exemption is gone. Trade: building the Null Domain interior in
      creative means keeping Attunement topped up
- [x] The Rift Door **stays functional but is not craftable** (no recipe was ever
      added), so it's already survival-inaccessible while still working as an entrance
- [ ] **Deferred**: renaming the dimension `dimdescent:rift` → `dimdescent:null_domain`.
      Changing the ID orphans saved data in that dimension. (A dimension-name change
      is under discussion — see the naming shortlist.)
- [ ] **Needs a human**: confirm ejection works in survival AND creative — walk in
      without Attunement, and let a dose expire while inside

## Phase 4 — The Waking Dream (entry via sleep)

**The bell/candle ritual is cut.** Entry now piggybacks on vanilla sleep, which
deletes most of what this phase used to be — no bell trigger, no candle tracking,
no 3s delay, no four-condition validation, no per-condition failure narration.
Two event handlers instead.

- [x] Altar block set: `altar_stone`, `carved_altar_stone`, `altar_heart`, the
      brick set (bricks/cracked/slab/stairs) and the `daemonlight` torch (3D, a
      `lit` state, lit by flint & steel, crafted from datura + stick)
- [x] **Altar + bed room authored in-game** and captured as one `dimdescent:altar`
      structure, imported into the mod with its chest pointed at the loot table
- [x] The **Almanacus Inferni Abditi** — readable custom grimoire, in the chest via
      `dimdescent:chests/altar`
- [x] **Sleep under Attunement, at night, in any bed → Null Domain**
      (`SleepEntryEvents`, via `CanPlayerSleepEvent`). Vanilla gates beds to night
      and sets respawn to the bed, both for free
- [x] **Sleep while raw-poisoned (seeds / Devil's Trumpet) → refused**, with the
      narrated line *"I can't sleep right now, I feel strange."*
- [x] **Attunement supersedes the raw trip**: drinking it mid-trip clears every
      symptom instantly (`DaturaTrip.cancel`); its own opening darkness comes from
      CompanionEffectManager on the same event
- [x] Registered the structure + a `random_spread` structure_set at village rarity
      (spacing 34 / separation 8), scoped to overworld LAND biomes so altars don't
      spawn half-submerged
- [ ] **Needs a human**: `/locate structure dimdescent:altar` in a FRESH world,
      then confirm one actually generates on the surface without floating/burying
- [ ] Repurpose **Dark Iron Bars** as altar/Null Domain decor

Worth knowing: expiry ejects to the player's respawn point, and vanilla sleeping
sets spawn to that bed — so entering by sleep means waking up in the very bed you
lay down in, with no extra code.

## Phase 5 — Lore delivery

Largely folded into Phase 4 now: the Almanacus and the eight-bed room ARE the
lore delivery. The old village-lectern plan is dropped — it described "the legend
of the industrial entrance," and the industrial framing is long gone.

- [ ] Keep it terse and menacing; foreshadow, never explain mechanics

## Phase 6 — The actual core concept (started)

Everything above is the *door*; this is the room behind it.

- [x] **DimDoors-style room traversal** (`NullDomainRooms`). Modelled directly on Dimensional
      Doors' pocket dungeon (reverse-engineered from the cloned repo): every crossing in, and
      every Rift Door walked through once inside, opens a fresh randomly-chosen room ~512 blocks
      away on a grid (`SPACING = 512`, their 32-chunk `pocketGridSize`), stamped LAZILY on door
      entry. Rooms are keyed by a monotonic index persisted in a `GridData` SavedData, so they
      never overlap and the grid survives restarts. Five code-generated `RoomType`s — PILLAR_HALL,
      LONG_GALLERY, GRAND_CHAMBER, CRAMPED_CELLS, HALL_OF_BARS — picked uniformly; three can carry
      an altar-loot chest. Each room is a Forsaken Fiber shell with one onward door in the far wall
- [x] Replaced the hole-drop descent + per-player depth counter with the above (no actionbar
      readout, no narration, per user's request). The platform / shared-exit-door / `RiftDoorLinkData`
      pairing machinery is deleted — doors only lead deeper now
- [ ] Depth-weighted selection (DimDoors' `VirtualLocation.depth`) so deeper rooms differ, plus a
      legible "how deep am I" signal (fog, ambient, colour grading)
- [ ] Authored rooms (NBT/`.schem`-style pool) to raise quality beyond the code-generated five
- [ ] Depth-tiered enemies
- [ ] Loot tied to depth/risk
- [ ] Voluntary exit door back to the altar you entered by (the only planned door that leads OUT)

Known POC limitations: room selection is a flat uniform pick with no depth axis yet; rooms are
code-generated (blockier than authored rooms would be); onward doors are one-way (rooms persist, so
two-way/backtracking links are possible later but aren't wired up); and the loot chest reuses the
altar table rather than a depth-scaled one.

---

## Open questions

Not blocking Phase 1, but they'll block Phase 4:

- **Pull radius** for the ritual — proposal: 16 blocks
- **Grace window** for late arrivals after the bell, or strictly the 3s snapshot?
- Does the altar **ship with its own bell**, or must the player place one?
- Can the bell be rung by **redstone**, or does it require a player?
- What happens to a player who is attuned and in range but **does not want to go**?

## Things deliberately not being built

- No mining-based entry. The Dark Iron Bars gate was "gear and patience," which
  is a chore rather than tension, and it is gone on purpose
- No NPCs, no quest system, no in-game explanation of the survival mechanic
- No third use for Datura Seeds
