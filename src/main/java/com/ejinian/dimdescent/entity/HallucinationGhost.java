package com.ejinian.dimdescent.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ejinian.dimdescent.registry.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// The "hallucination" stage of the datura trip: a silent, faceless, translucent figure that spawns
// near the player and just watches.
//
// Deliberately NOT a Zombie subclass. Extending Zombie would drag in attack AI, sunlight burning,
// zombie sounds, baby/villager conversion, reinforcement spawning, and Monster's
// shouldDespawnInPeaceful() == true (which would delete it on Peaceful). Extending PathfinderMob
// gives us a blank slate - and the zombie LOOK comes purely from the client side reusing vanilla's
// zombie model layer, which doesn't require the entity to be a Zombie at all.
public class HallucinationGhost extends PathfinderMob {

    private static final String TAG_OWNER = "GhostOwner";

    // How wide a cone counts as "the player is looking at it". Cosine of the angle between the
    // player's look vector and the direction to the ghost: ~0.6 is a comfortable 53-degree cone.
    private static final double SEEN_DOT = 0.6;
    // Hysteresis - it takes a wider swing to lose it than to acquire it, so the ghost doesn't
    // flicker in and out when the player's crosshair is hovering right at the boundary.
    private static final double LOST_DOT = 0.35;

    @Nullable
    private UUID ownerUuid;
    // Only start honouring the look-away rule once the player has actually laid eyes on it -
    // otherwise a ghost that spawns behind the player deletes itself immediately, unseen.
    private boolean hasBeenSeen;
    private int ticksRemaining = -1;

    public HallucinationGhost(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setSilent(true);
        this.setPersistenceRequired();
        this.lookControl = new StareLookControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // A hallucination is by definition private to the person having it. ChunkMap consults this per
    // player on every tracking update (ChunkMap.TrackedEntity.updatePlayer), so returning false
    // means other clients are never even sent the spawn packet - no packet interception needed.
    // A ghost with no owner (i.e. /summon'd by hand for debugging) stays visible to everyone.
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return this.ownerUuid == null || this.ownerUuid.equals(player.getUUID());
    }

    @Override
    protected void registerGoals() {
        // Intentionally none. All this thing does is stare, and that's driven directly in
        // customServerAiStep rather than through a Goal - there's no other behaviour to arbitrate
        // against, so a Goal would just be indirection.
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.ticksRemaining > 0) {
            this.ticksRemaining--;
        } else if (this.ticksRemaining == 0) {
            this.discard();
            return;
        }

        Player owner = this.getOwner();
        if (owner == null) {
            // Owner logged out or changed dimension - the hallucination goes with them.
            if (this.ownerUuid != null) {
                this.discard();
            }
            return;
        }

        // Track the player's eyes, not their feet, so it meets their gaze rather than staring at
        // the floor. The 0 max pitch delta is belt-and-braces on top of StareLookControl.
        this.getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ(), 30.0F, 0.0F);

        double dot = this.viewDotFrom(owner);
        if (dot > SEEN_DOT) {
            this.hasBeenSeen = true;
        } else if (this.hasBeenSeen && dot < LOST_DOT) {
            this.discard();
        }
    }

    // How centred this ghost is in the player's view: 1.0 = dead ahead, 0.0 = exactly side-on.
    private double viewDotFrom(Player player) {
        Vec3 toGhost = this.position()
                .add(0.0, this.getBbHeight() * 0.5, 0.0)
                .subtract(player.getEyePosition());
        if (toGhost.lengthSqr() < 1.0E-4) {
            return 1.0;
        }
        return player.getLookAngle().dot(toGhost.normalize());
    }

    @Nullable
    private Player getOwner() {
        return this.ownerUuid == null ? null : this.level().getPlayerByUUID(this.ownerUuid);
    }

    public void setOwner(Player owner) {
        this.ownerUuid = owner.getUUID();
    }

    public void setLifetime(int ticks) {
        this.ticksRemaining = ticks;
    }

    // --- Completely inert: can't be hit, hit you, block you, or be targeted ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isPickable() {
        // No crosshair target, so it can't be attacked or right-clicked at all.
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
        // Never shove the player around.
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    // --- Silent ---

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    // --- Lifecycle: never despawns on its own, never hits the save file ---

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Vanilla's despawn rules don't apply; ours are lifetime + look-away, in customServerAiStep.
    }

    @Override
    public boolean shouldBeSaved() {
        // A hallucination surviving a server restart would be a bug, not a feature.
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUuid != null) {
            tag.putUUID(TAG_OWNER, this.ownerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_OWNER)) {
            this.ownerUuid = tag.getUUID(TAG_OWNER);
        }
    }

    // Yaw-only look control: getXRotD() returning empty means LookControl.tick never writes a pitch,
    // and its resetXRotOnTick() (true by default) pins xRot at 0 every tick. Net effect - the ghost
    // turns its head to follow you but never tilts it down, even when you're standing right under
    // it or crouched at its feet.
    private static class StareLookControl extends LookControl {

        StareLookControl(HallucinationGhost ghost) {
            super(ghost);
        }

        @Override
        protected Optional<Float> getXRotD() {
            return Optional.empty();
        }
    }

    // Finds somewhere within ~3 blocks of the player with room to stand, and puts a ghost there.
    // Returns null if the player is somewhere too cramped for it to fit.
    @Nullable
    public static HallucinationGhost spawnNear(ServerPlayer owner, int lifetimeTicks) {
        ServerLevel level = owner.serverLevel();
        HallucinationGhost ghost = ModRegistry.HALLUCINATION_GHOST.get().create(level);
        if (ghost == null) {
            return null;
        }

        for (int attempt = 0; attempt < 40; attempt++) {
            // Ring between 1.5 and 3 blocks out - close enough to be startling, far enough not to
            // spawn clipped inside the player.
            double angle = owner.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = 1.5 + owner.getRandom().nextDouble() * 1.5;
            double x = owner.getX() + Math.cos(angle) * distance;
            double z = owner.getZ() + Math.sin(angle) * distance;
            double y = owner.getY() + owner.getRandom().nextInt(3) - 1;

            ghost.moveTo(x, y, z, owner.getRandom().nextFloat() * 360.0F, 0.0F);
            BlockPos below = BlockPos.containing(x, y - 0.1, z);
            boolean hasFloor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            if (hasFloor && level.noCollision(ghost, ghost.getBoundingBox())) {
                ghost.setOwner(owner);
                ghost.setLifetime(lifetimeTicks);
                ghost.finalizeSpawn(level, level.getCurrentDifficultyAt(ghost.blockPosition()),
                        MobSpawnType.EVENT, null);
                level.addFreshEntity(ghost);
                return ghost;
            }
        }
        ghost.discard();
        return null;
    }
}
