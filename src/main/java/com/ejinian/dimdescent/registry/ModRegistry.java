package com.ejinian.dimdescent.registry;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlock;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlockEntity;
import java.util.List;

import com.ejinian.dimdescent.effect.AttunementMobEffect;
import com.ejinian.dimdescent.effect.DaturaTripMobEffect;
import com.ejinian.dimdescent.effect.DryMouthMobEffect;
import com.ejinian.dimdescent.effect.PsychosisMobEffect;
import com.ejinian.dimdescent.effect.TachycardiaMobEffect;
import com.ejinian.dimdescent.entity.HallucinationGhost;
import com.ejinian.dimdescent.block.DaemonlightBlock;
import com.ejinian.dimdescent.block.DaemonlightWallBlock;
import com.ejinian.dimdescent.block.DaturaBlock;
import com.ejinian.dimdescent.item.AlmanacusItem;
import com.ejinian.dimdescent.item.DaturaSeedsItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DimDescent.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DimDescent.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DimDescent.MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, DimDescent.MODID);
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, DimDescent.MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, DimDescent.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DimDescent.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, DimDescent.MODID);

    // The Daemonlight's flame. Held as a plain instance as well as a registry entry, because the
    // torch blocks need to pass it to their constructor while BLOCK registration is running - and
    // PARTICLE_TYPE may not have been filled yet at that point, so resolving it through the
    // DeferredHolder there could fail. The object itself is inert; it only has to be registered by
    // the time a particle is actually spawned, which is far later.
    public static final SimpleParticleType DAEMON_FLAME = new SimpleParticleType(false);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DAEMON_FLAME_TYPE =
            PARTICLE_TYPES.register("daemon_flame", () -> DAEMON_FLAME);

    public static final DeferredBlock<RiftDoorBlock> RIFT_DOOR = BLOCKS.register("rift_door", () -> new RiftDoorBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(5.0F)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<Item> RIFT_DOOR_ITEM = ITEMS.register("rift_door",
            () -> new DoubleHighBlockItem(RIFT_DOOR.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftDoorBlockEntity>> RIFT_DOOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("rift_door", () -> BlockEntityType.Builder.of(
                    RiftDoorBlockEntity::new, RIFT_DOOR.get()).build(null));

    // Rift-dimension equivalent of Dimensional Doors' "Fabric of Reality": an insta-break void
    // floor. Look is meant to tie into the depth mechanic later (more unstable the deeper you
    // are) rather than being flat black. noLootTable() makes the no-drop intentional and explicit
    // (matching its sibling FORSAKEN_FIBER below) instead of silently missing a loot table file.
    public static final DeferredBlock<Block> NULLSTONE = BLOCKS.register("nullstone", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instabreak()
                    .sound(SoundType.GLASS)
                    .noLootTable()));

    public static final DeferredItem<Item> NULLSTONE_ITEM = ITEMS.register("nullstone",
            () -> new BlockItem(NULLSTONE.get(), new Item.Properties()));

    // Rift-dimension equivalent of Dimensional Doors' "Ancient Fabric": the unbreakable outer
    // boundary of every dungeon room, so players can't dig their way out of a room's confines.
    // Mechanically identical to bedrock (unbreakable in survival), just our own name/texture.
    public static final DeferredBlock<Block> FORSAKEN_FIBER = BLOCKS.register("forsaken_fiber", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .isValidSpawn(Blocks::never)));

    public static final DeferredItem<Item> FORSAKEN_FIBER_ITEM = ITEMS.register("forsaken_fiber",
            () -> new BlockItem(FORSAKEN_FIBER.get(), new Item.Properties()));

    // Attunement Gate ruin material: the reinforced bars that cage the rift door until broken.
    // Same class/geometry as vanilla iron_bars (just a darker texture) with hardness bumped to
    // obsidian's tier so it can't be shortcut with early tools - requiresCorrectToolForDrops()
    // + the needs_diamond_tool/mineable_pickaxe tags (see data/minecraft/tags/block) are what
    // actually enforce that, matching how obsidian itself is tagged.
    public static final DeferredBlock<Block> DARK_IRON_BARS = BLOCKS.register("dark_iron_bars", () -> new IronBarsBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredItem<Item> DARK_IRON_BARS_ITEM = ITEMS.register("dark_iron_bars",
            () -> new BlockItem(DARK_IRON_BARS.get(), new Item.Properties()));

    // Altar block set (Phase 4). Unbreakable in survival - same strength(-1) + noLootTable() that
    // makes FORSAKEN_FIBER bedrock-like - so a naturally-spawned altar can't be dismantled for its
    // (unbreakable) blocks, while still being editable in creative for authoring. DEEPSLATE sound
    // to match the black-basalt look.
    private static BlockBehaviour.Properties altarProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .isValidSpawn(Blocks::never)
                .sound(SoundType.DEEPSLATE);
    }

    public static final DeferredBlock<Block> ALTAR_STONE = BLOCKS.register("altar_stone",
            () -> new Block(altarProps()));
    public static final DeferredItem<Item> ALTAR_STONE_ITEM = ITEMS.register("altar_stone",
            () -> new BlockItem(ALTAR_STONE.get(), new Item.Properties()));

    public static final DeferredBlock<Block> CARVED_ALTAR_STONE = BLOCKS.register("carved_altar_stone",
            () -> new Block(altarProps()));
    public static final DeferredItem<Item> CARVED_ALTAR_STONE_ITEM = ITEMS.register("carved_altar_stone",
            () -> new BlockItem(CARVED_ALTAR_STONE.get(), new Item.Properties()));

    // The ritual's focal block and spatial anchor: the bell has to be struck near one of these for
    // the ritual to fire (so the check has a definite altar to look outward from). Emits a low red
    // glow to read as the "live" heart of the altar.
    public static final DeferredBlock<Block> ALTAR_HEART = BLOCKS.register("altar_heart",
            () -> new Block(altarProps().lightLevel(state -> 7)));
    public static final DeferredItem<Item> ALTAR_HEART_ITEM = ITEMS.register("altar_heart",
            () -> new BlockItem(ALTAR_HEART.get(), new Item.Properties()));

    // Brick dressing for the altar, so it can be built with vanilla-style masonry rather than only
    // flat cubes. Same unbreakable altarProps() as the rest of the set - these are structure
    // material, not something a player mines - so there are no drops and no recipes by design.
    // SlabBlock/StairBlock bring all the normal vanilla behaviour with them (top/bottom/double
    // placement, the five stair shapes, waterlogging), which is why they're the vanilla classes
    // rather than custom ones.
    public static final DeferredBlock<Block> ALTAR_STONE_BRICKS = BLOCKS.register("altar_stone_bricks",
            () -> new Block(altarProps()));
    public static final DeferredItem<Item> ALTAR_STONE_BRICKS_ITEM = ITEMS.register("altar_stone_bricks",
            () -> new BlockItem(ALTAR_STONE_BRICKS.get(), new Item.Properties()));

    public static final DeferredBlock<Block> CRACKED_ALTAR_STONE_BRICKS =
            BLOCKS.register("cracked_altar_stone_bricks", () -> new Block(altarProps()));
    public static final DeferredItem<Item> CRACKED_ALTAR_STONE_BRICKS_ITEM =
            ITEMS.register("cracked_altar_stone_bricks",
                    () -> new BlockItem(CRACKED_ALTAR_STONE_BRICKS.get(), new Item.Properties()));

    public static final DeferredBlock<SlabBlock> ALTAR_STONE_BRICK_SLAB =
            BLOCKS.register("altar_stone_brick_slab", () -> new SlabBlock(altarProps()));
    public static final DeferredItem<Item> ALTAR_STONE_BRICK_SLAB_ITEM =
            ITEMS.register("altar_stone_brick_slab",
                    () -> new BlockItem(ALTAR_STONE_BRICK_SLAB.get(), new Item.Properties()));

    // StairBlock needs the block it's "made of" for its base state. ALTAR_STONE_BRICKS is declared
    // above so it is already registered by the time this supplier runs.
    public static final DeferredBlock<StairBlock> ALTAR_STONE_BRICK_STAIRS =
            BLOCKS.register("altar_stone_brick_stairs",
                    () -> new StairBlock(ALTAR_STONE_BRICKS.get().defaultBlockState(), altarProps()));
    public static final DeferredItem<Item> ALTAR_STONE_BRICK_STAIRS_ITEM =
            ITEMS.register("altar_stone_brick_stairs",
                    () -> new BlockItem(ALTAR_STONE_BRICK_STAIRS.get(), new Item.Properties()));

    // Daemonlight: a demonic torch. Ordinary torch mechanics on purpose - it is a light and nothing
    // else. Plain TorchBlock/WallTorchBlock rather than RedstoneTorchBlock, so it carries no lit
    // state, emits no redstone signal and can't be used as a redstone component; only the light
    // LEVEL (7) is borrowed from the redstone torch. Otherwise it matches vanilla torch behaviour:
    // no collision, instabreak, wood sound, popped off by pistons, and placeable on floor or wall.
    // The flame is a PARTICLE, not geometry. Modelled flame planes tilt with the wall variant, which
    // looked wrong mounted on a wall; a particle always rises vertically whatever the orientation.
    // TorchBlock adds dark smoke of its own on top of it.
    private static BlockBehaviour.Properties daemonlightProps() {
        return BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .lightLevel(state -> 7)
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY);
    }

    public static final DeferredBlock<DaemonlightBlock> DAEMONLIGHT = BLOCKS.register("daemonlight",
            () -> new DaemonlightBlock(DAEMON_FLAME, daemonlightProps()));

    // lootFrom (not a loot table of its own) so breaking a wall-mounted one drops the same item,
    // exactly as vanilla's wall torches do.
    public static final DeferredBlock<DaemonlightWallBlock> DAEMONLIGHT_WALL = BLOCKS.register("daemonlight_wall",
            () -> new DaemonlightWallBlock(DAEMON_FLAME, daemonlightProps().lootFrom(DAEMONLIGHT)));

    // One item for both blocks. StandingAndWallBlockItem picks the wall variant when placed against
    // a side, and maps BOTH blocks to this item, so the wall version reports the right name too.
    public static final DeferredItem<Item> DAEMONLIGHT_ITEM = ITEMS.register("daemonlight",
            () -> new StandingAndWallBlockItem(DAEMONLIGHT.get(), DAEMONLIGHT_WALL.get(),
                    new Item.Properties(), Direction.DOWN));

    // Source plant for Datura Seeds (potion-brewing ingredient). A FlowerBlock, except DaturaBlock
    // widens the ground it grows on to include sand and terracotta, so it can actually inhabit the
    // desert and badlands biomes it's seeded into (a plain flower only takes dirt/grass). No
    // suspicious-stew effect - SuspiciousStewEffects.EMPTY keeps the plain flower behaviour (random
    // XZ placement offset, instabreak).
    public static final DeferredBlock<Block> DATURA = BLOCKS.register("datura", () -> new DaturaBlock(
            SuspiciousStewEffects.EMPTY,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PLANT)
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<Item> DATURA_ITEM = ITEMS.register("datura",
            () -> new BlockItem(DATURA.get(), new Item.Properties()));

    // Edible, but not food: nutrition 0 and alwaysEdible() so it can be eaten on a full hunger bar
    // (you don't eat datura because you're hungry). fast() keeps the eat animation short - the
    // punishment is what comes after, not the two seconds of chewing.
    private static final FoodProperties DATURA_SEEDS_FOOD = new FoodProperties.Builder()
            .nutrition(0)
            .saturationModifier(0.0F)
            .alwaysEdible()
            .fast()
            .build();

    public static final DeferredItem<Item> DATURA_SEEDS = ITEMS.register("datura_seeds",
            () -> new DaturaSeedsItem(new Item.Properties().food(DATURA_SEEDS_FOOD)));

    // The mod's only written lore, found in the chest in the altar's room of eight beds.
    // stacksTo(1) because it's a singular artefact, not a supply.
    public static final DeferredItem<Item> ALMANACUS = ITEMS.register("almanacus_inferni_abditi",
            () -> new AlmanacusItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // Marker effect for "safe to be inside a rift dimension right now" - the rift-lethality check
    // (not built yet) will look for this on the player. 3600 ticks (3 min) matches vanilla's base
    // duration for other awkward-potion-derived effects like Night Vision; easy to retune later.
    public static final DeferredHolder<MobEffect, AttunementMobEffect> ATTUNEMENT_EFFECT =
            MOB_EFFECTS.register("attunement", AttunementMobEffect::new);

    // Invisible, and runs for the whole trip including the gaps between symptoms - it's what tells
    // the client to draw the crack vignette, and what the Devil's Trumpet potion carries. Declared
    // above the potions because Java forbids a forward reference to it from their initialisers.
    public static final DeferredHolder<MobEffect, DaturaTripMobEffect> DATURA_TRIP_EFFECT =
            MOB_EFFECTS.register("datura_trip", DaturaTripMobEffect::new);

    // The weaker half of the pipeline: the same poisoning Attunement represents at full strength.
    // Its only payload is the (invisible) trip marker, which does three jobs at once - it colours
    // the bottle pitch black, it drives the client's crack vignette, and its application is what
    // tells DaturaTrip to roll a potion trip. That last part is why splash and lingering variants
    // work with no extra code: they apply the effect to everyone they touch.
    public static final DeferredHolder<Potion, Potion> POTION_OF_DEVILS_TRUMPET = POTIONS.register("devils_trumpet",
            () -> new Potion(new MobEffectInstance(DATURA_TRIP_EFFECT, 3600)));

    public static final DeferredHolder<Potion, Potion> LONG_POTION_OF_DEVILS_TRUMPET =
            POTIONS.register("long_devils_trumpet",
                    () -> new Potion("devils_trumpet", new MobEffectInstance(DATURA_TRIP_EFFECT, 9600)));

    public static final DeferredHolder<Potion, Potion> POTION_OF_ATTUNEMENT = POTIONS.register("attunement",
            () -> new Potion(new MobEffectInstance(ATTUNEMENT_EFFECT, 3600)));

    // Redstone-extended variant, same 3600 -> 9600 tick (3 -> 8 min) ratio vanilla uses for every
    // other awkward-derived potion (Night Vision, Swiftness, etc). The "name" constructor param
    // reuses "attunement" rather than "long_attunement" deliberately - that's what vanilla itself
    // does for its own long potions (confirmed: there's no long_night_vision lang key either), so
    // it shows as "Potion of Attunement" with the longer duration in the tooltip, not a renamed item.
    public static final DeferredHolder<Potion, Potion> LONG_POTION_OF_ATTUNEMENT = POTIONS.register("long_attunement",
            () -> new Potion("attunement", new MobEffectInstance(ATTUNEMENT_EFFECT, 9600)));

    // --- Datura trip: the symptom effects (see com.ejinian.dimdescent.trip.TripStage) ---
    // Each is its own registered effect rather than a vanilla one so it reads by symptom name in the
    // status list. Where a vanilla VISUAL is needed too (Psychosis's night vision), the real vanilla
    // effect is applied alongside and hidden - see CompanionEffectManager.

    public static final DeferredHolder<MobEffect, DryMouthMobEffect> DRY_MOUTH_EFFECT =
            MOB_EFFECTS.register("dry_mouth", DryMouthMobEffect::new);

    public static final DeferredHolder<MobEffect, TachycardiaMobEffect> TACHYCARDIA_EFFECT =
            MOB_EFFECTS.register("tachycardia", TachycardiaMobEffect::new);

    public static final DeferredHolder<MobEffect, PsychosisMobEffect> PSYCHOSIS_EFFECT =
            MOB_EFFECTS.register("psychosis", PsychosisMobEffect::new);

    // Heartbeat that fades up and back down, played once when Tachycardia starts.
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT_SOUND =
            SOUND_EVENTS.register("heartbeat", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, "heartbeat")));

    // Indistinct voices, in the Psychosis noise pool. Synthesised from scratch (see the asset
    // generator in the scratchpad): whispering is unvoiced speech, i.e. turbulent noise shaped by
    // vocal-tract formants, which is why it can be built convincingly without recording anything.
    //
    // Three separate events rather than one event with three variants, deliberately: vanilla would
    // pick among variants of a single event at random, which is fine in play but makes an individual
    // take impossible to audition with /playsound.
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> WHISPER_SOUNDS = List.of(
            registerSound("whispers_1"),
            registerSound("whispers_2"),
            registerSound("whispers_3"));

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(DimDescent.MODID, name)));
    }


    // MobCategory.MISC keeps it out of natural spawning entirely - this only ever exists because
    // something spawned it deliberately. Sized to match a zombie, since it wears a zombie's model.
    public static final DeferredHolder<EntityType<?>, EntityType<HallucinationGhost>> HALLUCINATION_GHOST =
            ENTITY_TYPES.register("hallucination_ghost", () -> EntityType.Builder
                    .of(HallucinationGhost::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build("hallucination_ghost"));

    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(HALLUCINATION_GHOST.get(), HallucinationGhost.createAttributes().build());
    }

    public static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(RIFT_DOOR_ITEM);
            event.accept(NULLSTONE_ITEM);
            event.accept(FORSAKEN_FIBER_ITEM);
            event.accept(DARK_IRON_BARS_ITEM);
            event.accept(ALTAR_STONE_ITEM);
            event.accept(CARVED_ALTAR_STONE_ITEM);
            event.accept(ALTAR_HEART_ITEM);
            event.accept(ALTAR_STONE_BRICKS_ITEM);
            event.accept(CRACKED_ALTAR_STONE_BRICKS_ITEM);
            event.accept(ALTAR_STONE_BRICK_SLAB_ITEM);
            event.accept(ALTAR_STONE_BRICK_STAIRS_ITEM);
            event.accept(DAEMONLIGHT_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(DATURA_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DATURA_SEEDS);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ALMANACUS);
        }
    }

    private ModRegistry() {
    }
}
