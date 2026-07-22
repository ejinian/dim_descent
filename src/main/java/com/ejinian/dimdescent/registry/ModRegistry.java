package com.ejinian.dimdescent.registry;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlock;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlockEntity;
import com.ejinian.dimdescent.effect.AttunementMobEffect;
import com.ejinian.dimdescent.item.DaturaSeedsItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
    // are) rather than being flat black.
    public static final DeferredBlock<Block> NULLSTONE = BLOCKS.register("nullstone", () -> new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instabreak()
                    .sound(SoundType.GLASS)));

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

    // Source plant for Datura Seeds (potion-brewing ingredient). No special suspicious-stew
    // effect - SuspiciousStewEffects.EMPTY still gets the plain FlowerBlock behavior (random XZ
    // placement offset, instabreak) without needing a custom Block subclass.
    public static final DeferredBlock<Block> DATURA = BLOCKS.register("datura", () -> new FlowerBlock(
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

    public static final DeferredItem<Item> DATURA_SEEDS = ITEMS.register("datura_seeds",
            () -> new DaturaSeedsItem(new Item.Properties()));

    // Marker effect for "safe to be inside a rift dimension right now" - the rift-lethality check
    // (not built yet) will look for this on the player. 3600 ticks (3 min) matches vanilla's base
    // duration for other awkward-potion-derived effects like Night Vision; easy to retune later.
    public static final DeferredHolder<MobEffect, AttunementMobEffect> ATTUNEMENT_EFFECT =
            MOB_EFFECTS.register("attunement", AttunementMobEffect::new);

    public static final DeferredHolder<Potion, Potion> POTION_OF_ATTUNEMENT = POTIONS.register("attunement",
            () -> new Potion(new MobEffectInstance(ATTUNEMENT_EFFECT, 3600)));

    public static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(RIFT_DOOR_ITEM);
            event.accept(NULLSTONE_ITEM);
            event.accept(FORSAKEN_FIBER_ITEM);
            event.accept(DARK_IRON_BARS_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(DATURA_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DATURA_SEEDS);
        }
    }

    private ModRegistry() {
    }
}
