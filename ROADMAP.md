# Roadmap

Build order for the Progression Framework in [CLAUDE.md](CLAUDE.md). That file is
the design; this one is the sequence and the loose ends.

Ordered so that each phase leaves the mod in a coherent, testable state.

---

## Phase 1 — Potion pipeline ✅

- [x] **Potion of the Devil's Trumpet**: Awkward + Datura Seeds, pitch black
- [x] Drinking it starts the trip 10s later, with all 8 symptoms compressed into
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

## Phase 2 — Make the chain reachable ⚠️ BLOCKER

Nothing below Phase 1 is obtainable in survival right now, because **Datura does
not spawn.** The entire progression currently requires creative mode to start.
This is the highest-value unblocking work in the project.

- [ ] World-gen placement for **Datura**, rarity comparable to uncommon vanilla
      flowers — exact comparison to settle at implementation time
- [ ] Decide which biomes it belongs in (thematically: dry, disturbed, roadside —
      real datura is a weed of waste ground, which suits "found where people used
      to be")
- [ ] Verify the full survival path end to end: find Datura → seeds → Awkward →
      Devil's Trumpet → Attunement

## Phase 3 — Null Domain behaviour

Aligns the shipped dimension with the new design. Each of these *changes existing
working code*, so they want testing together.

- [ ] Replace instant-death-without-Attunement with **teleport to overworld
      spawn on expiry** (`RiftLethalityEvents`). Death is a punishment; a hard
      ejection timer is the push-your-luck loop the Core Concept actually asks for
- [ ] Remove the **Rift Door as an overworld entrance** — entry is altar-only and
      has no visible portal. The door survives only as the future voluntary exit
- [ ] Rename the dimension `dimdescent:rift` → `dimdescent:null_domain`.
      **Note:** changing the dimension ID orphans any existing saved data in that
      dimension. Worth doing now while worlds are disposable, not later
- [ ] Retire `dimdescent:rift_unattuned` damage type and its three tag files if
      nothing else ends up using them

## Phase 4 — Altars and the ritual

The largest phase. Depends on Phase 2 (you need Attunement to test entry).

- [ ] Author the altar structure in-game, export via structure block to NBT
- [ ] Altar blocks unbreakable in survival
- [ ] World-gen placement, rarity comparable to villages
- [ ] Ritual condition checks: attuned **now**, candles lit, night, bell struck
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
