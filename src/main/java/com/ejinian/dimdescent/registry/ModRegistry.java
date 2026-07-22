package com.ejinian.dimdescent.registry;

import com.ejinian.dimdescent.DimDescent;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlock;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

    public static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(RIFT_DOOR_ITEM);
            event.accept(NULLSTONE_ITEM);
            event.accept(FORSAKEN_FIBER_ITEM);
        }
    }

    private ModRegistry() {
    }
}
