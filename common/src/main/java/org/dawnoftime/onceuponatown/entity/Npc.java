package org.dawnoftime.onceuponatown.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.dawnoftime.onceuponatown.entity.ai.OuatWalkNodeEvaluator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.entity.ai.OpenFenceGateGoal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.List;

public class Npc extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_IS_READING =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    // Incremented on each block placement; client reads changes to trigger the swing animation.
    private static final EntityDataAccessor<Integer> DATA_BUILD_GENERATION =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.INT);

    // Client-side animation state: written by NpcModel.setupAnim(), never synced or saved.
    public int clientLastBuildGeneration = -1;
    public float clientBuildPlacedAtAge = -1000f;

    private SimpleStateMachine stateMachine;
    // Server-side countdown -- cleared to 0 when the reading animation ends.
    private int readingTicksRemaining = 0;
    // Anchor position of the town this builder belongs to; saved so the builder can self-validate on load.
    private BlockPos townAnchorPos = null;
    // Whether the anchor ownership check has been performed this session.
    private boolean anchorValidated = false;

    public Npc(EntityType<? extends Npc> type, Level level) {
        super(type, level);
        // Builders must never despawn naturally — Minecraft would silently remove them
        // when no player is nearby, breaking the town's builder UUID reference.
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_IS_READING, false);
        this.entityData.define(DATA_BUILD_GENERATION, 0);
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level) {
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes) {
                this.nodeEvaluator = new OuatWalkNodeEvaluator();
                this.nodeEvaluator.setCanPassDoors(true);
                return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
            }
        };
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, false));
        this.goalSelector.addGoal(2, new OpenFenceGateGoal(this));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (!anchorValidated && townAnchorPos != null && level() instanceof ServerLevel sl) {
                anchorValidated = true;
                Town town = LevelTowns.get(sl).getTownAt(townAnchorPos).orElse(null);
                if (town == null || !town.getBuilderNpcIds().contains(getUUID())) {
                    discard();
                    return;
                }
            }
            if (stateMachine == null) stateMachine = new SimpleStateMachine(this);
            stateMachine.tick();
            if (readingTicksRemaining > 0 && --readingTicksRemaining == 0) {
                entityData.set(DATA_IS_READING, false);
                setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (townAnchorPos != null) tag.put("TownAnchorPos", NbtUtils.writeBlockPos(townAnchorPos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TownAnchorPos")) townAnchorPos = NbtUtils.readBlockPos(tag.getCompound("TownAnchorPos"));
    }

    public void setTownAnchorPos(BlockPos pos) { this.townAnchorPos = pos; }
    public BlockPos getTownAnchorPos() { return townAnchorPos; }

    // Called by BuildGoal when construction is complete.
    // rotation and entryConnectorWorldPos are precomputed by SimpleStateMachine/BuildGoal.
    public void onBuildComplete(BlockPos builtAt, String buildingId, ConnectionPoint usedConnection,
                                Rotation rotation, BlockPos entryConnectorWorldPos) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        LevelTowns.get(serverLevel).getAllTowns().stream()
            .filter(t -> t.getBuilderNpcIds().contains(getUUID()))
            .findFirst()
            .ifPresent(town -> {
                List<ConnectionPoint> connections = BuildSchematic.readJigsawPoints(
                    serverLevel, builtAt, buildingId, rotation, entryConnectorWorldPos);
                BoundingBox bb = BuildingDataHandler.get(buildingId)
                    .flatMap(def -> def.terrainMatching
                        ? BuildSchematic.computeFootprintBoundingBox(serverLevel, builtAt, def.nbt, rotation)
                        : BuildSchematic.computeBoundingBox(serverLevel, builtAt, def.nbt, rotation))
                    .orElseGet(() -> new BoundingBox(
                        builtAt.getX(), builtAt.getY(), builtAt.getZ(),
                        builtAt.getX(), builtAt.getY(), builtAt.getZ()
                    ));
                town.registerBuilding(builtAt, buildingId, connections, bb, rotation);
                LevelTowns.get(serverLevel).markDirty();
            });
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.5)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // --- Hand and animation helpers called by BuildGoal ---

    // Starts the "reading plan" animation for the given number of ticks.
    // Puts the town scroll in the main hand; tick() clears it when the timer expires.
    public void startReading(int durationTicks) {
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.MAP));
        readingTicksRemaining = durationTicks;
        entityData.set(DATA_IS_READING, true);
        playSound(SoundEvents.BOOK_PAGE_TURN, 0.6f, 0.9f + getRandom().nextFloat() * 0.2f);
    }

    public void holdInOffHand(ItemStack stack) { setItemInHand(InteractionHand.OFF_HAND, stack); }
    public void holdInMainHand(ItemStack stack) { setItemInHand(InteractionHand.MAIN_HAND, stack); }

    public void freeHands() {
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    // Called by BuildGoal each time a block is placed; increments the synced generation counter
    // so NpcModel.setupAnim() detects the change and triggers the swing animation on the client.
    public void notifyBlockPlaced() {
        entityData.set(DATA_BUILD_GENERATION, entityData.get(DATA_BUILD_GENERATION) + 1);
    }

    // Stub accessors used by NpcModel for animations - simplified in v1
    public int getUnhappyCounter() { return 0; }
    public boolean isCrossingArms() { return false; }
    public boolean isReading() { return entityData.get(DATA_IS_READING); }
    public int getBuildGeneration() { return entityData.get(DATA_BUILD_GENERATION); }
}
