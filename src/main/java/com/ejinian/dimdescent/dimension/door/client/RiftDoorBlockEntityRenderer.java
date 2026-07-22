package com.ejinian.dimdescent.dimension.door.client;

import com.ejinian.dimdescent.dimension.door.RiftDoorBlock;
import com.ejinian.dimdescent.dimension.door.RiftDoorBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

// Draws the swirling rift-portal effect behind the door's window cutouts: small "peekaboo" boxes
// while closed, one full-size box filling the doorway once open. The physical door mesh itself
// (with its transparent window pixels) comes from the normal baked block model; this only adds
// the glow that shows through those transparent pixels.
//
// The glow is drawn as a thin BOX (two end-caps + 4 connecting side walls), not a single flat
// plane. A flat plane viewed nearly edge-on (e.g. looking almost straight down the door's own
// plane, which happens easily once the door swings open and you're standing to the side) foreshortens
// to a near-invisible hairline; a box always has some face roughly facing the camera. This mirrors
// how vanilla's own TheEndPortalRenderer renders an actual recessed box, not a flat quad.
public class RiftDoorBlockEntityRenderer implements BlockEntityRenderer<RiftDoorBlockEntity> {

    // Pushes the glow geometry a hair outside the door's real (opaque) collision geometry so the
    // two don't sit exactly coplanar - two surfaces at the exact same depth z-fight (flicker as
    // the camera moves a fraction of a pixel).
    private static final double FACE_EPSILON = 1.0 / 256.0;

    // Pixel bounds of the 4 transparent window cutouts in rift_door_top.png (a 16x16 texture;
    // see gen_door_textures4.py). Deriving the quad positions from these pixel coordinates
    // (rather than hand-picked fractions) is what keeps the glow lined up with the actual
    // transparent hole instead of leaving a gap that shows the world behind it.
    private static final double TEXTURE_SIZE = 16.0;
    private static final double COL_LEFT_MIN_PX = 1, COL_LEFT_MAX_PX = 8;
    private static final double COL_RIGHT_MIN_PX = 8, COL_RIGHT_MAX_PX = 15;
    private static final double ROW_UPPER_MIN_PX = 1, ROW_UPPER_MAX_PX = 8;
    private static final double ROW_LOWER_MIN_PX = 8, ROW_LOWER_MAX_PX = 15;

    // Extra margin drawn past the cutout's exact edge, in pixels. Harmless (the opaque door
    // texture just occludes the overdrawn sliver) and guards against off-by-a-hair gaps.
    private static final double PAD_PX = 0.5;

    public RiftDoorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static double colPixelToFraction(double px) {
        return px / TEXTURE_SIZE;
    }

    // Texture rows map inverted to the vertical block axis: row 0 (top of the PNG) is the top
    // of the block (Y=1), row 16 (bottom of the PNG) is the bottom (Y=0).
    private static double rowPixelToY(double px) {
        return 1.0 - px / TEXTURE_SIZE;
    }

    @Override
    public void render(RiftDoorBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                        MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = level != null ? level.getBlockState(pos) : blockEntity.getBlockState();
        if (level == null || !(state.getBlock() instanceof RiftDoorBlock)) {
            return;
        }

        boolean open = state.getValue(DoorBlock.OPEN);

        // Always derive the frame plane from the door's CLOSED shape (regardless of whether it's
        // actually open right now), sourced from the block's own real collision geometry instead
        // of a hand-written facing/hinge lookup table - this is what keeps the glow fixed in the
        // doorway opening rather than swinging away with the door, and it can't drift out of sync
        // with how DoorBlock actually places its slab for a given facing/hinge.
        BlockState closedState = state.setValue(DoorBlock.OPEN, false);
        AABB bounds = closedState.getShape(level, pos).bounds();

        boolean fixedIsX = (bounds.maxX - bounds.minX) < (bounds.maxZ - bounds.minZ);
        double near = (fixedIsX ? bounds.minX : bounds.minZ) - FACE_EPSILON;
        double far = (fixedIsX ? bounds.maxX : bounds.maxZ) + FACE_EPSILON;

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RiftPortalRenderTypes.RIFT_PORTAL);

        if (open) {
            addGlowBox(consumer, pose, fixedIsX, near, far, 0.06, 0.94, 0.06, 1.94);
        } else {
            double colLeftMin = colPixelToFraction(COL_LEFT_MIN_PX - PAD_PX);
            double colLeftMax = colPixelToFraction(COL_LEFT_MAX_PX + PAD_PX);
            double colRightMin = colPixelToFraction(COL_RIGHT_MIN_PX - PAD_PX);
            double colRightMax = colPixelToFraction(COL_RIGHT_MAX_PX + PAD_PX);

            // +1: the window cutouts live in the upper half's texture, but this renderer's local
            // space starts at the lower half's origin, so the upper half's Y range is [1, 2].
            double upperYMin = rowPixelToY(ROW_UPPER_MAX_PX + PAD_PX) + 1.0;
            double upperYMax = rowPixelToY(ROW_UPPER_MIN_PX - PAD_PX) + 1.0;
            double lowerYMin = rowPixelToY(ROW_LOWER_MAX_PX + PAD_PX) + 1.0;
            double lowerYMax = rowPixelToY(ROW_LOWER_MIN_PX - PAD_PX) + 1.0;

            addGlowBox(consumer, pose, fixedIsX, near, far, colLeftMin, colLeftMax, upperYMin, upperYMax);
            addGlowBox(consumer, pose, fixedIsX, near, far, colRightMin, colRightMax, upperYMin, upperYMax);
            addGlowBox(consumer, pose, fixedIsX, near, far, colLeftMin, colLeftMax, lowerYMin, lowerYMax);
            addGlowBox(consumer, pose, fixedIsX, near, far, colRightMin, colRightMax, lowerYMin, lowerYMax);
        }
    }

    // Draws a thin box spanning [near, far] along the door's thickness axis, [otherMin, otherMax]
    // across the doorway's width, and [yMin, yMax] vertically - two end-caps plus 4 side walls,
    // so there's always some visible surface no matter which angle it's viewed from.
    private static void addGlowBox(VertexConsumer consumer, Matrix4f pose, boolean fixedIsX,
                                    double near, double far, double otherMin, double otherMax,
                                    double yMin, double yMax) {
        // end caps (front/back)
        addFace(consumer, pose, fixedIsX,
                near, otherMin, yMin, near, otherMax, yMin, near, otherMax, yMax, near, otherMin, yMax);
        addFace(consumer, pose, fixedIsX,
                far, otherMin, yMin, far, otherMax, yMin, far, otherMax, yMax, far, otherMin, yMax);
        // side walls (left/right)
        addFace(consumer, pose, fixedIsX,
                near, otherMin, yMin, far, otherMin, yMin, far, otherMin, yMax, near, otherMin, yMax);
        addFace(consumer, pose, fixedIsX,
                near, otherMax, yMin, far, otherMax, yMin, far, otherMax, yMax, near, otherMax, yMax);
        // top/bottom caps
        addFace(consumer, pose, fixedIsX,
                near, otherMin, yMin, far, otherMin, yMin, far, otherMax, yMin, near, otherMax, yMin);
        addFace(consumer, pose, fixedIsX,
                near, otherMin, yMax, far, otherMin, yMax, far, otherMax, yMax, near, otherMax, yMax);
    }

    // Takes 4 corners as (a, b, y) triples, where `a` is the door's thickness axis and `b` is the
    // doorway-width axis - and maps them to world (x, y, z) based on the door's facing, so the
    // face-building code above doesn't need to know or care which real axis is which.
    private static void addFace(VertexConsumer consumer, Matrix4f pose, boolean fixedIsX,
                                 double a0, double b0, double y0,
                                 double a1, double b1, double y1,
                                 double a2, double b2, double y2,
                                 double a3, double b3, double y3) {
        addQuad(consumer, pose,
                fixedIsX ? a0 : b0, y0, fixedIsX ? b0 : a0,
                fixedIsX ? a1 : b1, y1, fixedIsX ? b1 : a1,
                fixedIsX ? a2 : b2, y2, fixedIsX ? b2 : a2,
                fixedIsX ? a3 : b3, y3, fixedIsX ? b3 : a3);
    }

    // Adds the quad in both winding orders so it's visible from either side,
    // regardless of the render type's backface culling state.
    private static void addQuad(VertexConsumer consumer, Matrix4f pose,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 double x3, double y3, double z3) {
        consumer.addVertex(pose, (float) x0, (float) y0, (float) z0);
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2);
        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3);

        consumer.addVertex(pose, (float) x3, (float) y3, (float) z3);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2);
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1);
        consumer.addVertex(pose, (float) x0, (float) y0, (float) z0);
    }
}
