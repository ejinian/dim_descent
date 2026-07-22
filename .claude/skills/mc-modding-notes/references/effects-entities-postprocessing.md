# Custom mob effects, custom mobs, and full-screen post-processing

Everything here was learned building the Datura Seeds "trip" (a chained sequence of symptom effects
ending in a hallucinated figure). Most of it generalises well beyond that feature.

## You cannot chain one mob effect into a different one

`MobEffectInstance` has a `hiddenEffect` field that surfaces when the outer effect expires, which
looks like exactly the mechanism for "Dry Mouth, then Nausea, then Tachycardia". **It isn't.** The
instance's `effect` field is `final`, and `setDetailsFrom()` only copies duration / amplifier /
ambient / visible / showIcon - never the effect type. Vanilla's `update()` even logs a warning when
two instances' types mismatch. So `hiddenEffect` can only ever mean "more of the same effect later
at a different strength" (which is what vanilla uses it for: drinking a weaker potion while a
stronger one is active).

**Any sequence of *different* effects has to be driven externally, tick by tick.** See
`com.ejinian.dimdescent.trip.DaturaTrip` - a `Map<UUID, Progress>` advanced on
`PlayerTickEvent.Post`, with the stage table in `TripStage`.

Re-applying the current stage's effect every tick when it goes missing is also how you make a
sequence immune to milk / `/effect clear` - there is no "uncurable" flag on a custom effect.

## Attribute modifiers reproduce most vanilla effects - but check where vanilla actually implements it

`MobEffect.addAttributeModifier(Holder<Attribute>, ResourceLocation, double, Operation)` in the
constructor is all it takes to make a custom effect that behaves like Speed/Slowness. Copy vanilla's
own numbers from `MobEffects.java` (Speed is `MOVEMENT_SPEED +0.2 ADD_MULTIPLIED_TOTAL` per level,
Slowness is `-0.15`).

**But not every vanilla effect is attribute-driven, and the exceptions are not obvious.** Haste
(`MobEffects.DIG_SPEED`) declares only an `ATTACK_SPEED` modifier. Its actual mining-speed boost is
hardcoded in `Player.getDigSpeed`:

```java
if (MobEffectUtil.hasDigSpeed(this)) {
    f *= 1.0F + (float)(MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
}
```

That checks for the literal `MobEffects.DIG_SPEED` instance, so a custom effect can never trigger it.
The fix is the very next line of the same method: `Attributes.BLOCK_BREAK_SPEED` is a plain
multiplicative attribute anyone can modify. Haste II is x1.4, so `+0.4 ADD_MULTIPLIED_TOTAL` on
`BLOCK_BREAK_SPEED` is an exact equivalent.

**Before assuming an attribute exists for an effect, grep for where vanilla consumes it.**

## Borrowing a vanilla effect's *visual* under a custom effect's name

Some visuals are welded to a specific vanilla effect instance on the client and cannot be
reproduced by a custom effect at all:

- Darkness' closing vignette - driven by `MobEffects.DARKNESS`' blend factor.
- Night vision's brightening - `GameRenderer.getNightVisionScale` reads `MobEffects.NIGHT_VISION`.

The only way to get the genuine article is to **apply the genuine vanilla effect alongside your
custom one, then hide it**:

1. Apply it with `showIcon = false` - that alone removes it from the HUD.
2. The inventory screen lists every active effect *regardless* of `showIcon`, so it also needs
   NeoForge's `IClientMobEffectExtensions.isVisibleInInventory`, registered on the MOD bus via
   `RegisterClientExtensionsEvent`:
   ```java
   event.registerMobEffect(new IClientMobEffectExtensions() {
       @Override public boolean isVisibleInInventory(MobEffectInstance i) { return !localPlayerHasOurEffect(); }
       @Override public boolean isVisibleInGui(MobEffectInstance i) { return !localPlayerHasOurEffect(); }
   }, MobEffects.DARKNESS.value());
   ```
   Gate it on the owning custom effect being active, or you'll hide Warden darkness too. Call
   `.value()` to pick the `MobEffect...` overload and dodge generic-varargs warnings.
3. **Own the retraction.** There's no `onEffectEnded` hook, so a companion applied for effect X will
   outlive X unless something removes it. `CompanionEffectManager` tracks a `Set<UUID>` of "this
   companion is ours" and strips it the tick the owning effect disappears.

Night vision specifically must be refreshed to a duration **above 200 ticks** - vanilla flickers it
during its final 200 ticks, which looks like a bug if you meant it to be steady.

## Full-screen colour post-processing (desaturation, tinting)

1.21.1 ships only six post *chains* (`invert`, `blur`, `creeper`, `spider`, `entity_outline`,
`transparency`) - the old "super secret settings" chains are gone. **But it still ships the
underlying `program`s**, including `color_convolve`, whose fragment shader ends with:

```glsl
float Luma = dot(OutColor, Gray);
vec3 Chroma = OutColor - Luma;
OutColor = (Chroma * Saturation) + Luma;
```

So a saturation effect needs **no new GLSL at all** - write your own chain JSON in your namespace
that references vanilla's program by name (see `assets/dimdescent/shaders/post/desaturate.json`,
modelled on vanilla's `invert.json`). Referencing, not copying, keeps it clear of the asset/IP rule
in `blocks-doors-models.md`.

Program names in a pass resolve through `ResourceLocation.tryParse`, which defaults to the
**`minecraft`** namespace - not the chain's. An unnamespaced `"color_convolve"` finds vanilla's.

Driving it at runtime:

| What | API |
|---|---|
| Load a chain | `minecraft.gameRenderer.loadEffect(ResourceLocation)` (path includes `shaders/post/x.json`) |
| Get the live chain | `minecraft.gameRenderer.currentEffect()` (`@Nullable PostChain`) |
| Set a uniform per frame | `postChain.setUniform("Saturation", value)` - applies to every pass; no-ops safely on passes that don't declare it |
| Tear down | `minecraft.gameRenderer.shutdownEffect()` |

**There is exactly ONE global post-effect slot**, and vanilla uses it for spectating a
creeper/spider/enderman. `loadEffect` closes whatever was there. Before calling `shutdownEffect`,
check `currentEffect().getName().equals(yourChain.toString())` (the name is the chain's
`ResourceLocation.toString()`) so you don't tear down something that isn't yours.

Failures are swallowed into a log warning rather than a crash, so a malformed chain shows up as
"the effect silently does nothing" - grep the client log for the chain's path.

## Sounds that follow one player and only that player

`ServerPlayer.playNotifySound` sends a `ClientboundSoundPacket`, which **bakes in fixed world
coordinates**. For anything meant to be "inside the player's head" (a heartbeat, tinnitus, a
whisper) that's wrong twice over - it stays behind when they walk away, and while it is only sent to
that one player, it's still positional.

Use `ClientboundSoundEntityPacket` instead, sent straight to the one connection:

```java
player.connection.send(new ClientboundSoundEntityPacket(
        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModRegistry.MY_SOUND.get()),
        SoundSource.PLAYERS, player, volume, pitch, player.getRandom().nextLong()));
```

`ClientPacketListener.handleSoundEntityEvent` turns this into an `EntityBoundSoundInstance`, which
re-reads the entity's position every tick. Bind it to the player themselves and it's permanently at
the listener's position - i.e. centred and effectively non-directional. Sending to a single
`connection` is what keeps it private; nobody nearby hears it.

Note the `wrapAsHolder` - vanilla's own `playNotifySound` does the same, and it sidesteps any
question of whether a `DeferredHolder` serialises correctly through the packet's registry codec.

Fade-in/fade-out is best baked into the audio file itself rather than driven by repeated packets at
changing volumes.

**Synthesising a convincing heartbeat**: use a pure pitch-swept sine (~95Hz falling to ~36Hz over
about 50ms) with a soft ~6ms attack and an exponential tail - no noise layer. An early attempt added
lowpassed gaussian noise for "body", but a one-pole filter on noise sustains a low rumble across the
whole envelope, and once consecutive beats overlap, that rumble stops reading as a heartbeat and
starts sounding like shuffling.

**Synthesising whispering** (rather than sourcing a clip, which would mean redistributing someone
else's audio under unknown licensing): whispered speech is *unvoiced* - there is no vocal-fold buzz
to model, just turbulent noise shaped by the vocal tract. So it's reachable with lowpassed white
noise pushed through three resonant bandpass filters (`scipy.signal.iirpeak`, Q~11) tuned to real
vowel formant triples - /i/ 270-2290-3010, /ɑ/ 730-1090-2440, /u/ 300-870-2240 and so on - gated
into 90-190ms syllables with a `sin(pi*t)` envelope (a hard gate clicks and reads as static), with
occasional highpassed bursts standing in for sibilants. Layering three passes at slightly different
formant scales (x0.88, x1.0, x1.14) and staggered start times reads as several people rather than
one. The generator lives in the session scratchpad, not the repo.

**Minecraft needs MONO ogg files for anything positional** - and entity-bound sounds are positional.
A stereo file will not play correctly. Check with `soundfile.read(...)` before shipping.

## Intercepting/altering every sound the client plays

`PlaySoundEvent` (client, GAME bus) fires for every sound about to play and exposes
`setSound(@Nullable SoundInstance)` - pass a replacement to alter it, or `null` to drop it entirely.
This is the only way to affect sounds globally: the engine has **no DSP hooks at all**. There is no
reverse playback, no reverb, no filter; `com.mojang.blaze3d.audio.Channel` exposes only `setPitch`
and `setVolume`. (And note that literal *phase* inversion - multiplying a mono waveform by -1 - is
inaudible, so "invert the sound" cannot be done the way it sounds like it should be.)

**The trap that crashed us:** `SoundEngine.play` posts the event **before** it calls `resolve()`:

```java
p_sound = ClientHooks.playSound(this, p_sound);   // <- event fires here
if (p_sound != null && p_sound.canPlaySound()) {
    p_sound.resolve(this.soundManager);           // <- Sound populated only now
    float f = p_sound.getVolume();
```

`AbstractSoundInstance.getVolume()` and `getPitch()` both do `this.volume * this.sound.getX()...`,
and `this.sound` is null until `resolve()` runs. **Calling either one inside the event handler is a
guaranteed NPE** - it crashed on water ambience within a minute of starting. Only the plain fields
are safe there: `getSource()`, `getLocation()`, `getX()/getY()/getZ()`, `isLooping()`, `getDelay()`.

So anything volume- or pitch-derived must be *deferred into the replacement instance*: have the
wrapper take a volume **multiplier** and compute `delegate.getVolume() * scale` inside its own
`getVolume()`, which the engine calls after it has resolved the wrapper (resolution delegates
through, so the underlying instance is resolved by then).

**Wrap, don't rebuild.** A `SoundInstance` carries looping, attenuation mode, relative-position flag
and world position; reconstructing one from a `SoundEvent` silently drops whichever of those the
original set. Implement `SoundInstance` and delegate everything except the fields being changed.

**Skip `TickableSoundInstance`.** Those recompute position/volume every tick (entity-bound sounds,
minecart loops, boss music) and a plain wrapper strips that behaviour. Skipping them also
conveniently protects your own entity-bound sounds from your own distortion.

## Colouring an item's name (including items you don't own the class of)

The hotbar name comes from `Gui.renderSelectedItemName`, which does:

```java
Component.empty().append(stack.getHoverName()).withStyle(stack.getRarity().getStyleModifier())
```

so the colour normally comes from **`Rarity`**, and only the four vanilla colours exist there.
NeoForge does make `Rarity` an `IExtensibleEnum` (with a `UnaryOperator<Style>` constructor, so a
custom rarity could carry any colour), but that needs an `enumextensions.json` wired into
`neoforge.mods.toml` - and it wouldn't help for potions anyway, since every potion is the same
`minecraft:potion` item and the rarity would have to be attached per-stack via `DataComponents.RARITY`.

**For a potion, just put a legacy `§` code in the lang value:**

```json
"item.minecraft.potion.effect.attunement": "§4Potion of Attunement"
```

`StringDecomposer` processes `§` codes in a component's literal content at render time, and an
explicit code inside the string wins over the style applied to the wrapping component - so the
rarity colour doesn't override it. This colours the hotbar popup and the tooltip title together.
Remember potions have four name keys (`potion`, `splash_potion`, `lingering_potion`,
`tipped_arrow`), all of which need it.

## Custom damage types

A damage type is a datapack registry entry, not code: `data/<modid>/damage_type/<name>.json` with
`exhaustion`, `message_id`, and `scaling`. Reference it from Java with a `ResourceKey<DamageType>`
and apply via `level.damageSources().source(KEY)`.

The death message key is `death.attack.<message_id>` (note `message_id` is camelCase by vanilla
convention, e.g. `genericKill`, not the resource path).

For a kill that genuinely cannot be survived, `Float.MAX_VALUE` alone is not enough - Resistance V
is 100% reduction. Add the type to `minecraft:bypasses_armor`, `bypasses_effects`, and
`bypasses_enchantments` via `data/minecraft/tags/damage_type/*.json` (tag files from a mod merge
additively with vanilla's, same as the block tags this project already ships).

## Full-screen HUD overlays (frost-style vignettes)

Vanilla's powder-snow frost effect is just one full-screen texture blitted at a varying alpha -
`Gui.renderTextureOverlay`, driven by `player.getPercentFrozen()`. Any "something is wrong with your
vision" overlay can copy it directly:

```java
@SubscribeEvent
public static void onRenderGui(RenderGuiEvent.Pre event) {   // Pre -> HUD draws on top, as frost does
    GuiGraphics g = event.getGuiGraphics();
    RenderSystem.disableDepthTest();
    RenderSystem.depthMask(false);
    RenderSystem.enableBlend();
    g.setColor(1, 1, 1, alpha);
    g.blit(TEXTURE, 0, 0, -90, 0.0F, 0.0F, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight());
    g.setColor(1, 1, 1, 1);
    RenderSystem.disableBlend();
    RenderSystem.depthMask(true);
    RenderSystem.enableDepthTest();
}
```

The state dance matters - restore all of it, or it leaks into the rest of the HUD. The overload
used is `blit(ResourceLocation, int x, int y, int blitOffset, float u, float v, int uWidth,
int vHeight, int texWidth, int texHeight)`.

The texture is stretched to the screen, so it distorts with aspect ratio. Design accordingly:
organic/irregular art (cracks, frost) hides the stretch, geometric art does not. Keep the centre
fully transparent and mask alpha by distance from the corners so it never obstructs the crosshair.

For a linear fade whose two directions are genuinely identical in speed, step by `1f / FADE_TICKS`
per client tick rather than using `Mth.lerp` - lerp is exponential and never quite arrives.

**Getting server-side state to a client overlay without networking:** if the driving state is a
server-side sequencer, the client has no idea it's running. Rather than registering a custom payload,
apply an invisible marker `MobEffect` for exactly the state's duration - effect sync is already
handled by vanilla, and the client just checks `player.hasEffect(MARKER)`. Hide it with
`showIcon=false` plus an `IClientMobEffectExtensions` returning false from both visibility methods.
Remember to strip it on logout if the server-side state is dropped there, or the player logs back in
to an overlay with nothing behind it.

## Per-player entity visibility (things only one player can see)

`Entity.broadcastToPlayer(ServerPlayer)` is a plain overridable returning `true` by default, and
`ChunkMap.TrackedEntity.updatePlayer` consults it on every tracking update:

```java
boolean flag = d1 <= d2 && this.entity.broadcastToPlayer(player) && ChunkMap.this.isChunkTracked(...);
```

Returning `false` means that client is never sent the spawn packet at all - no packet interception,
no client-side-only fake entity, no custom networking. This is the whole implementation of "only the
hallucinating player can see the ghost" (`HallucinationGhost.broadcastToPlayer`).

## Building an inert, non-interactable mob

- **Extend `PathfinderMob`, not the mob you want to look like.** Subclassing `Zombie` drags in attack
  AI, sunlight burning, zombie sounds, villager conversion, reinforcement spawning, and - via
  `Monster` - `shouldDespawnInPeaceful() == true`, which deletes it on Peaceful. `Mob`'s own default
  is `false`, so a `PathfinderMob` survives Peaceful for free. **The look is a client-side concern
  and does not require the entity to be that type at all.**
- Inertness is a pile of small overrides: `hurt` -> false, `isInvulnerableTo` -> true, `isPickable`
  -> false (this is the one that removes it as a crosshair/attack target), `isPushable` -> false,
  `canBeCollidedWith` -> false, `doPush`/`pushEntities` -> no-op, `isAttackable` -> false.
- `setSilent(true)` plus null `getAmbientSound`/`getHurtSound`/`getDeathSound` and a no-op
  `playStepSound`.
- `shouldBeSaved() -> false` keeps a transient entity out of the save file entirely.
- Override `checkDespawn()` to a no-op and `removeWhenFarAway -> false` if you're managing lifetime
  yourself.
- Register attributes on the MOD bus via `EntityAttributeCreationEvent`
  (`event.put(TYPE.get(), MyMob.createAttributes().build())`), or the game crashes on first spawn.
- `MobCategory.MISC` keeps it out of natural spawning.

### Making it stare at you without tilting its head

Subclass `LookControl` and return `Optional.empty()` from `getXRotD()`. `LookControl.tick` only
writes a pitch from that optional, and its `resetXRotOnTick()` (true by default) pins `xRot` to 0
every tick - so the mob tracks you in yaw only and never looks down, however far below it you stand.
Assign it to `this.lookControl` after `super(...)` in the constructor, then call
`getLookControl().setLookAt(...)` each tick from `customServerAiStep()`.

Body-follows-head is automatic and free: `BodyRotationControl` swings `yBodyRot` toward `yHeadRot`
once the difference exceeds `getMaxHeadYRot()`, which is exactly vanilla's "pig staring at you" feel.

### Reusing vanilla's model geometry without copying it

`context.bakeLayer(ModelLayers.ZOMBIE)` in the renderer gives you vanilla's zombie mesh; you never
redefine a cube. But **pick the right class in the model hierarchy**:

- `AbstractZombieModel.setupAnim` unconditionally calls `AnimationUtils.animateZombieArms`, which is
  what holds a zombie's arms out in front. Extending plain `HumanoidModel` instead gives the normal
  humanoid rest pose - arms hanging at the sides.
- `HumanoidModel` is also the level that exposes the render-type constructor:
  `super(root, RenderType::entityTranslucent)`. The default `entityCutoutNoCull` snaps alpha to
  fully-on/fully-off, so **translucency must come from this constructor**, not from the texture
  alone. With `entityTranslucent`, alpha baked into the PNG is honoured directly - no need to touch
  `LivingEntityRenderer`'s internal ARGB colour int.
- Use `MobRenderer`, not `HumanoidMobRenderer`, when the mob has no equipment - it skips the
  held-item and armour layers. Shadow radius `0.0F` = no shadow.
- A "faceless" humanoid needs no special modelling: fill the whole 64x64 skin uniformly and blank
  **only** the hat/overlay region at `(32,0)-(64,16)` (a filled hat layer renders a second shell
  around the head). The face simply never gets drawn.
