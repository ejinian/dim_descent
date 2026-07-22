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
- [ ] **Deferred**: removing the Rift Door as an overworld entrance. It stays for
      now — it's the only way in until the altars exist (Phase 4). Ejection already
      enforces the attunement requirement on it, so an unattuned player gets nowhere
- [ ] **Deferred**: renaming the dimension `dimdescent:rift` → `dimdescent:null_domain`.
      Changing the ID orphans saved data in that dimension; batching it with the
      door removal avoids doing that churn twice
- [ ] **Needs a human**: confirm ejection works both ways in survival — walk in
      without Attunement, and let a dose expire while inside

## Phase 4 — Altars and the ritual (in progress)

The largest phase. Depends on Phase 2 (you need Attunement to test entry).

Decisions locked: a **small custom block set** for the altar (not one block, not
vanilla-only), and a **player-struck bell only** as the trigger (no redstone).

- [x] Altar block set: `altar_stone`, `carved_altar_stone`, and `altar_heart`
      (the ritual's focal/anchor block, low red glow). All unbreakable in survival
      via the FORSAKEN_FIBER strength(-1)+noLootTable pattern, editable in creative
- [ ] **Author the altar structure in-game** (human task): build with the blocks
      above + vanilla dark blocks + candles + a bell, capture with a structure block
      to `dimdescent:altar`, hand over the `.nbt`
- [ ] Register the structure + a `random_spread` structure_set at village-like
      rarity (villages are spacing 34 / separation 8)
- [ ] Ritual condition checks: attuned **now**, candles lit, night, bell struck
      near an `altar_heart`
- [ ] Bell as the trigger → 3s delay → pull everyone in range who is attuned
- [ ] Per-condition narrated failure feedback
- [ ] Candles go out at dawn (not destroyed, not consumed)
- [ ] Entry perceptible only to attuned players in range
- [ ] Repurpose **Dark Iron Bars** as altar/Null Domain decor — it's already
      built, robust and tested, it just isn't a gate any more

## Phase 5 — Lore delivery

- [ ] Village lectern room with a book. **The existing plan is stale**: it
      described "the legend of the industrial entrance," and the industrial
      framing is gone. Rewrite around the altars and, more importantly, around
      the deliriant warning
- [ ] Keep it terse and menacing; foreshadow, never explain mechanics

## Phase 6 — The actual core concept

Still entirely unbuilt. Everything above is the *door*; this is the room behind it.

- [ ] Depth axis and a legible "how deep am I" signal (fog, ambient, colour grading)
- [ ] Modular/procedural room composition
- [ ] Depth-tiered enemies
- [ ] Loot tied to depth/risk
- [ ] Voluntary exit door back to the altar you entered by

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
