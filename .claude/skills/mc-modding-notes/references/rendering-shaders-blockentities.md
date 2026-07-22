# Custom rendering: BlockEntityRenderers, custom shaders, and render-layer gotchas

## `@EventBusSubscriber` bus rules

- Default `bus` is `GAME` - correct for most gameplay events (`RegisterCommandsEvent`, block/entity
  interaction events, etc).
- Client-only *registration* events (`RegisterShadersEvent`, `EntityRenderersEvent.RegisterRenderers`,
  and similar `Register*Event`s used during client setup) fire on the **MOD** bus instead.
- **Do not** write `bus = EventBusSubscriber.Bus.MOD` explicitly - as of this NeoForge version that
  parameter (and the `Bus` enum) is deprecated and marked for removal (confirmed via `javap -v` on
  the annotation class showing `@Deprecated(forRemoval=true)`). Just omit `bus` entirely:
  ```java
  @EventBusSubscriber(modid = MyMod.MODID, value = Dist.CLIENT)
  public class MyClientEvents {
      @SubscribeEvent
      public static void onRegisterShaders(RegisterShadersEvent event) { ... }
  }
  ```
  The framework auto-infers the correct bus by checking whether the event class implements
  `IModBusEvent`.
- `@EventBusSubscriber` itself lives in `net.neoforged.fml.common.EventBusSubscriber`
  (FancyModLoader) - not obviously discoverable from the NeoForge/vanilla sources jars alone.

## Render layer defaults to "solid" - alpha=0 pixels render as opaque black, not transparent

If a block's texture has fully transparent pixels (alpha=0) but the block hasn't been told to use
a transparency-aware render layer, those pixels render with their raw RGB value as if fully
opaque. This is why a texture with alpha=0 "cutout window" pixels showed up as solid **black**
squares instead of transparent - the underlying RGB happened to be `(0,0,0)`.

Fix: register the block with `RenderType.cutout()` (binary alpha test - fully transparent or fully
opaque, no blending) or `RenderType.translucent()` (real alpha blending, more expensive) instead of
leaving it on the default solid layer. This has to happen client-side, after registries exist:

```java
@SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(MyBlocks.MY_BLOCK.get(), RenderType.cutout()));
}
```

`enqueueWork` matters - `FMLClientSetupEvent` handlers run off the render thread by default, and
this touches a render-thread-owned lookup table. `ItemBlockRenderTypes.setRenderLayer` is the
standard way non-vanilla blocks register their render layer, even though it's marked deprecated in
current NeoForge (still functional; no clean replacement found yet - re-check this if NeoForge
ships a new pattern later).

**This bites any new block with transparent texture pixels, not just doors** - hit it three times
now: the door windows, a `block/cross`-parented flower (Datura), and a reused-vanilla-geometry
`IronBarsBlock` (Dark Iron Bars). Every time, the symptom is the same - transparent regions render
as solid black instead of see-through. Vanilla's own blocks that use these exact same
models/parents (poppy's `block/cross`, real `iron_bars`) look correct out of the box only because
vanilla hardcodes its *own* blocks into this render-layer list internally - that coverage does
**not** extend to a mod's blocks just because the model/parent/geometry is identical. **Any custom
block with alpha-cutout pixels needs its own explicit `setRenderLayer(..., RenderType.cutout())`
call, full stop, regardless of whether it reuses a vanilla model.** Cheap to batch multiple blocks
in one `enqueueWork` call - just keep adding to the same lambda as new alpha-using blocks are added,
don't create a new `FMLClientSetupEvent` handler per block.

**For multipart-connected geometry (fences, walls, iron bars), design the texture as a
tileable lattice, not "a small bar icon on a big background."** The individual "post" piece (a lone,
unconnected block) only ever samples a thin strip of the texture, but the "side"/"cap" connector
pieces used when blocks link together (a wall of bars, which is the normal case for something like
a cage) stretch a MUCH WIDER UV sample - roughly half the texture - across each connecting panel.
If most of the texture outside that thin center strip is a flat "background" fill color, a wall of
connected bars reads as a solid opaque panel instead of a lattice, even with correct
`RenderType.cutout()`, because the wide-sampled region just doesn't contain any transparent pixels
to show through. Fix: make the *entire* 16x16 canvas a repeating criss-cross pattern (e.g. opaque
2px stripes / transparent 2px gaps on both axes) so that ANY sub-rectangle any model piece samples
still reads as "metal with real gaps," regardless of which exact UV window it happens to be.

## Custom BlockEntityRenderer + custom core shader (portal-style effects)

Vanilla's End Portal swirl is **not** a simple animated texture. It's a `BlockEntityRenderer`
(`TheEndPortalRenderer`) drawing geometry with a special `RenderType`
(`RenderType.endPortal()`) whose vertex format is **POSITION-only** (no UV attribute at all) - the
fragment shader derives texture coordinates purely from screen-space projection
(`projection_from_position`, from clip-space position). Critically, **the actual color does not
come from the texture's RGB** - it comes from a hardcoded `vec3[] COLORS` array baked directly
into the fragment shader (`rendertype_end_portal.fsh`), which gets multiplied against the sampled
texture per depth-layer and summed across up to 15 layers (each layer rotated/scaled/translated
differently based on `GameTime`, giving the swirling parallax look that changes with view angle).

**This means to re-theme a portal-style effect to a different color palette, you don't need new
texture assets at all** - fork the shader (`.vsh`/`.fsh`/`.json` triple) and just change the
`COLORS` array. You can keep reusing vanilla's own texture resources:
`TheEndPortalRenderer.END_SKY_LOCATION` (`textures/environment/end_sky.png`) and
`.END_PORTAL_LOCATION` (`textures/entity/end_portal.png`) - referencing them by resource location,
not copying the files (see the IP note in `blocks-doors-models.md`).

Steps to build a re-themed version:

1. **Copy the shader files** into `assets/<modid>/shaders/core/`, keeping the vertex shader
   identical to vanilla's and only changing the `COLORS` array in the fragment shader. Reference
   the shaders by namespaced name in the `.json` (`"vertex": "<modid>:my_shader_name"`) so it
   resolves to your own files instead of vanilla's.

2. **Register the shader** via `RegisterShadersEvent` (MOD bus, client-only):
   ```java
   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
       event.registerShader(
           new ShaderInstance(event.getResourceProvider(),
               ResourceLocation.fromNamespaceAndPath(MODID, "rendertype_my_portal"),
               DefaultVertexFormat.POSITION),
           shader -> MyRenderTypes.myShader = shader); // stash it in a static field
   }
   ```

3. **Build a custom `RenderType`**, mirroring vanilla's `RenderType.END_PORTAL` construction
   exactly (read `RenderType.java` directly for the current exact overload/argument order):
   ```java
   public static final RenderType MY_PORTAL = RenderType.create(
       "my_portal", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 1536, false, false,
       RenderType.CompositeState.builder()
           .setShaderState(new RenderStateShard.ShaderStateShard(() -> MyRenderTypes.myShader))
           .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
               .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
               .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
               .build())
           .createCompositeState(false));
   ```

4. **Register a `BlockEntityRenderer`** that draws quads using this render type, via
   `EntityRenderersEvent.RegisterRenderers` (also MOD bus, client-only):
   ```java
   @SubscribeEvent
   public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
       event.registerBlockEntityRenderer(MyBlockEntities.MY_TYPE.get(), MyRenderer::new);
   }
   ```
   Since the vertex format is POSITION-only, drawing a quad is just 4 `consumer.addVertex(pose, x,
   y, z)` calls (no UV/color/normal needed) - add each quad twice, once per winding order, if you
   need it visible from both sides and aren't sure the render type disables backface culling.

5. **This custom quad geometry doesn't need to match a full block's bounding box** - since the
   shader computes everything from screen-space projection rather than model UVs, you can draw
   small quads (e.g. sized to line up with transparent cutout pixels in a block's texture) or
   quads that span multiple stacked blocks (e.g. a 2-tall door's block entity, anchored at the
   lower half, can draw geometry up through local Y=2 to cover the upper half too - chunk-section
   render culling is coarse enough that this works fine in practice).

## Block entities for otherwise-normal blocks

A block only needs a `BlockEntity` if something about it needs per-instance dynamic
rendering/state beyond what a baked model can express (this is why vanilla's End Portal, and our
portal-effect door, have one, even though visually they're mostly "just a block/door"). Implement
`EntityBlock.newBlockEntity(BlockPos, BlockState)` on the block class; for a multi-block structure
(like a 2-tall door), only give ONE of the constituent blockstates (e.g. the lower half) a real
block entity, and have its renderer draw geometry covering the others - avoids duplicate render
calls fighting each other.
