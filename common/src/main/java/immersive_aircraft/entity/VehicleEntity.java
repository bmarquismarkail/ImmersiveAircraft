package immersive_aircraft.entity;

import com.google.common.collect.Lists;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import immersive_aircraft.Main;
import immersive_aircraft.client.KeyBindings;
import immersive_aircraft.cobalt.network.NetworkHandler;
import immersive_aircraft.config.Config;
import immersive_aircraft.network.c2s.CollisionMessage;
import immersive_aircraft.network.c2s.CommandMessage;
import immersive_aircraft.util.InterpolatedFloat;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract vehicle which handles player input, collisions, passengers and destruction
 */
public abstract class VehicleEntity extends Entity {
	protected static final EntityDataAccessor<Integer> DAMAGE_WOBBLE_TICKS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Integer> DAMAGE_WOBBLE_SIDE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	protected static final EntityDataAccessor<Float> DAMAGE_WOBBLE_STRENGTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);

    protected final boolean canExplodeOnCrash;

	protected static final EntityDataAccessor<Integer> BOOST = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

	protected int interpolationSteps;

	protected double x;
	protected double y;
	protected double z;

	protected double clientYaw;
	protected double clientPitch;

	protected float movementX;
	protected float movementY;
	protected float movementZ;

    public final InterpolatedFloat pressingInterpolatedX;
    public final InterpolatedFloat pressingInterpolatedY;
    public final InterpolatedFloat pressingInterpolatedZ;

    public float roll;
    public float prevRoll;

    public float getRoll() {
        return roll;
    }

    public float getRoll(float tickDelta) {
        return Mth.lerp(tickDelta, prevRoll, getRoll());
    }

    public void boost() {
        entityData.set(BOOST, 100);
    }

    protected void applyBoost() {

    }

    public boolean canBoost() {
        return false;
    }

    public int getBoost() {
        return entityData.get(BOOST);
    }

    protected abstract List<List<Vec3>> getPassengerPositions();

    protected int getPassengerSpace() {
        return getPassengerPositions().size();
    }

    @Override
    public void setXRot(float pitch) {
        float loops = (float) (Math.floor((pitch + 180f) / 360f) * 360f);
        pitch -= loops;
        xRotO -= loops;
        super.setXRot(pitch);
    }

    public VehicleEntity(EntityType<? extends AircraftEntity> entityType, Level world, boolean canExplodeOnCrash) {
        super(entityType, world);
        this.canExplodeOnCrash = canExplodeOnCrash;
        blocksBuilding = true;
        maxUpStep = 0.55f;

        pressingInterpolatedX = new InterpolatedFloat(getInputInterpolationSteps());
        pressingInterpolatedY = new InterpolatedFloat(getInputInterpolationSteps());
        pressingInterpolatedZ = new InterpolatedFloat(getInputInterpolationSteps());
    }

    protected float getInputInterpolationSteps() {
        return 10;
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DAMAGE_WOBBLE_TICKS, 0);
        entityData.define(DAMAGE_WOBBLE_SIDE, 1);
        entityData.define(DAMAGE_WOBBLE_STRENGTH, 0.0f);
        entityData.define(BOOST, 0);
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return AircraftEntity.canCollide(this, other);
    }

    public static boolean canCollide(Entity entity, Entity other) {
        return (other.canBeCollidedWith() || other.isPushable()) && !entity.isPassengerOfSameVehicle(other);
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected Vec3 getRelativePortalPosition(Direction.Axis portalAxis, BlockUtil.FoundRectangle portalRect) {
        return LivingEntity.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(portalAxis, portalRect));
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return hasPassenger(attacker) || super.skipAttackInteraction(attacker);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (level.isClientSide || isRemoved()) {
            return true;
        }
        setDamageWobbleSide(-getDamageWobbleSide());
        setDamageWobbleTicks(10);
        setDamageWobbleStrength(getDamageWobbleStrength() + amount * 10.0f / getDurability());
        gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());

        if(source.getEntity() instanceof Player player) { // Handle player drops separately to destruction drops.
            if(!player.getAbilities().instabuild)
                drop(); // Only drop item if player isn't in creative mode.
            discard();
        }
        else if(getDamageWobbleStrength() > 40.0F) {
            if(!Config.getInstance().onlyPlayerCanDestroyAircraft || isVehicle()) { // If the plane crashed, or was destroyed by a non-player.
                if(level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS) && Config.getInstance().enableDropsForNonPlayer)
                    drop();

                if(canExplodeOnCrash && Config.getInstance().enableCrashExplosion) { // If crashing explosion is enabled.
                    getLevel().explode(this, getX(), getY(), getZ(),
                            Config.getInstance().crashExplosionRadius,
                            Config.getInstance().enableCrashFire,
                            Config.getInstance().enableCrashBlockDestruction ? Explosion.BlockInteraction.BREAK : Explosion.BlockInteraction.NONE);
                }
                discard();
            }
        }

        return true;
    }

    protected float getDurability() {
        return 1.0f;
    }

    protected void drop() {
        spawnAtLocation(asItem());
    }

    @Override
    public void onAboveBubbleCol(boolean drag) {
        level.addParticle(ParticleTypes.SPLASH, getX() + (double) random.nextFloat(), getY() + 0.7, getZ() + (double) random.nextFloat(), 0.0, 0.0, 0.0);
        if (random.nextInt(20) == 0) {
            level.playLocalSound(getX(), getY(), getZ(), getSwimSplashSound(), getSoundSource(), 1.0f, 0.8f + 0.4f * random.nextFloat(), false);
        }
        gameEvent(GameEvent.SPLASH, getControllingPassenger());
    }

    @Override
    public void push(Entity entity) {
        if (entity instanceof AircraftEntity) {
            if (entity.getBoundingBox().minY < getBoundingBox().maxY) {
                super.push(entity);
            }
        } else if (entity.getBoundingBox().minY <= getBoundingBox().minY) {
            super.push(entity);
        }
    }

    public Item asItem() {
        return Items.DARK_OAK_BOAT;
    }

    @Override
    public void animateHurt() {
        setDamageWobbleSide(-getDamageWobbleSide());
        setDamageWobbleTicks(10);
        setDamageWobbleStrength(getDamageWobbleStrength() * 11.0f);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        this.x = x;
        this.y = y;
        this.z = z;
        clientYaw = yaw;
        clientPitch = pitch;
        this.interpolationSteps = 10;
    }

    @Override
    public Direction getMotionDirection() {
        return getDirection().getClockWise();
    }

    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0f;
        }
        return positive ? 1.0f : -1.0f;
    }

    protected boolean useAirplaneControls() {
        return false;
    }

    @Override
    public void tick() {
        // pilot
        if (level.isClientSide() && !getPassengers().isEmpty()) {
            for (Entity entity : getPassengers()) {
                if (entity instanceof Player player) {
                    if (KeyBindings.dismount.consumeClick()) {
                        NetworkHandler.sendToServer(new CommandMessage(CommandMessage.Key.DISMOUNT, getDeltaMovement()));
                        player.setJumping(false);
                    }
                    if (KeyBindings.boost.consumeClick() && canBoost()) {
                        NetworkHandler.sendToServer(new CommandMessage(CommandMessage.Key.BOOST, getDeltaMovement()));
                        Vec3 p = position();
                        level.playLocalSound(p.x(), p.y(), p.z(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.0f, 1.0f, true);
                    }
                }
            }

            //controls
            Entity pilot = getPassengers().get(0);
            if (pilot instanceof Player) {
                setInputs(getMovementMultiplier(
                                KeyBindings.left.isDown(),
                                KeyBindings.right.isDown()
                        ), getMovementMultiplier(
                                KeyBindings.up.isDown(),
                                KeyBindings.down.isDown()
                        ),
                        getMovementMultiplier(
                                useAirplaneControls() ? KeyBindings.push.isDown() : KeyBindings.forward.isDown(),
                                useAirplaneControls() ? KeyBindings.pull.isDown() : KeyBindings.backward.isDown()
                        )
                );
            }
        }

        // wobble
        if (getDamageWobbleTicks() > 0) {
            setDamageWobbleTicks(getDamageWobbleTicks() - 1);
        }
        if (getDamageWobbleStrength() > 0.0f) {
            setDamageWobbleStrength(getDamageWobbleStrength() - 1.0f);
        }

        super.tick();

        // interpolate
        handleClientSync();


        int boost = getBoost();
        if (boost > 0) {
            entityData.set(BOOST, boost - 1);
        }

        // if it's the right side, update the velocity
        if (isControlledByLocalInstance()) {
            updateVelocity();

            // boost
            if (boost > 0) {
                applyBoost();
            }

            if (level.isClientSide) {
                updateController();
            }

            move(MoverType.SELF, getDeltaMovement());
        }

        // auto enter
        checkInsideBlocks();
        List<Entity> list = level.getEntities(this, getBoundingBox().inflate(0.2f, -0.01f, 0.2f), EntitySelector.pushableBy(this));
        if (!list.isEmpty()) {
            boolean bl = !level.isClientSide && !(getControllingPassenger() instanceof Player);
            for (Entity entity : list) {
                if (entity.hasPassenger(this)) continue;
                if (bl && getPassengers().size() < (getPassengerSpace() - 1) && !entity.isPassenger() && entity.getBbWidth() < getBbWidth() && entity instanceof LivingEntity && !(entity instanceof WaterAnimal) && !(entity instanceof Player)) {
                    entity.startRiding(this);
                    continue;
                }
                push(entity);
            }
        }

        // interpolate keys for visual feedback
        if (level.isClientSide) {
            pressingInterpolatedX.update(movementX);
            pressingInterpolatedY.update(movementY);
            pressingInterpolatedZ.update(movementZ);
        }
    }

    private void handleClientSync() {
        if (isControlledByLocalInstance()) {
            interpolationSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (interpolationSteps <= 0) {
            return;
        }
        double interpolatedX = getX() + (x - getX()) / (double) interpolationSteps;
        double interpolatedY = getY() + (y - getY()) / (double) interpolationSteps;
        double interpolatedZ = getZ() + (z - getZ()) / (double) interpolationSteps;
        double interpolatedYaw = Mth.wrapDegrees(clientYaw - (double) getYRot());
        setYRot(getYRot() + (float) interpolatedYaw / (float) interpolationSteps);
        setXRot(getXRot() + (float) (clientPitch - (double) getXRot()) / (float) interpolationSteps);

        setPos(interpolatedX, interpolatedY, interpolatedZ);
        setRot(getYRot(), getXRot());

        --interpolationSteps;
    }

    protected abstract void updateVelocity();

    protected float getGravity() {
        return -0.04f;
    }

    protected abstract void updateController();

    @Override
    public void positionRider(Entity passenger) {
        if (!hasPassenger(passenger)) {
            return;
        }

        Matrix4f transform = getVehicleTransform();

        int size = getPassengers().size() - 1;
        List<List<Vec3>> positions = getPassengerPositions();
        if (size < positions.size()) {
            int i = getPassengers().indexOf(passenger);
            if (i >= 0 && i < positions.get(size).size()) {
                Vec3 position = positions.get(size).get(i);

                //animals are thicc
                if (passenger instanceof Animal) {
                    position.add(0.0f, 0.0f, 0.2f);
                }

                position = position.add(0, passenger.getMyRidingOffset(), 0);

                Vector4f worldPosition = transformPosition(transform, (float) position.x, (float) position.y, (float) position.z);

                passenger.setPos(worldPosition.x(), worldPosition.y(), worldPosition.z());

                passenger.setYRot(passenger.getYRot() + (getYRot() - yRotO));
                passenger.setYHeadRot(passenger.getYHeadRot() + (getYRot() - yRotO));

                copyEntityData(passenger);
                if (passenger instanceof Animal animalEntity && size > 1) {
                    int angle = passenger.getId() % 2 == 0 ? 90 : 270;
                    passenger.setYBodyRot(animalEntity.yBodyRot + (float) angle);
                    passenger.setYHeadRot(passenger.getYHeadRot() + (float) angle);
                }
            }
        }
    }

    private Vec3 getDismountOffset(double vehicleWidth, double passengerWidth) {
        double d = (vehicleWidth + passengerWidth + (double) 1.0E-5f) / 2.0;
        float yaw = getYRot() + 90.0f;
        float f = -Mth.sin(yaw * ((float) Math.PI / 180));
        float g = Mth.cos(yaw * ((float) Math.PI / 180));
        float h = Math.max(Math.abs(f), Math.abs(g));
        return new Vec3((double) f * d / (double) h, 0.0, (double) g * d / (double) h);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (getDeltaMovement().lengthSqr() < 0.1f) {
            double e;
            Vec3 vec3d = getDismountOffset(getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth());
            double d = getX() + vec3d.x;
            BlockPos blockPos = new BlockPos(d, getBoundingBox().maxY, e = getZ() + vec3d.z);
            BlockPos blockPos2 = blockPos.below();
            if (!level.isWaterAt(blockPos2)) {
                double g;
                ArrayList<Vec3> list = Lists.newArrayList();
                double f = level.getBlockFloorHeight(blockPos);
                if (DismountHelper.isBlockFloorValid(f)) {
                    list.add(new Vec3(d, (double) blockPos.getY() + f, e));
                }
                if (DismountHelper.isBlockFloorValid(g = level.getBlockFloorHeight(blockPos2))) {
                    list.add(new Vec3(d, (double) blockPos2.getY() + g, e));
                }
                for (Pose entityPose : passenger.getDismountPoses()) {
                    for (Vec3 vec3d2 : list) {
                        if (!DismountHelper.canDismountTo(level, vec3d2, passenger, entityPose)) continue;
                        passenger.setPose(entityPose);
                        return vec3d2;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    protected void copyEntityData(Entity entity) {
        entity.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(entity.getYRot() - getYRot());
        float g = Mth.clamp(f, -105.0f, 105.0f);
        entity.yRotO += g - f;
        entity.setYRot(entity.getYRot() + g - f);
        entity.setYHeadRot(entity.getYRot());
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        copyEntityData(passenger);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {

    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isValidDimension()) {
            player.displayClientMessage(Component.translatable("immersive_aircraft.invalid_dimension"), true);
            return InteractionResult.FAIL;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (hasPassenger(player)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        Vec3 prediction = position().add(movement);
        super.move(movementType, movement);

        // Collision damage
        if (level.isClientSide && Config.getInstance().collisionDamage) {
            if (verticalCollision || horizontalCollision) {
                double maxPossibleError = movement.length();
                double error = prediction.distanceTo(position());
                if (error <= maxPossibleError) {
                    float collision = (float) (error - (verticalCollision ? Math.abs(getGravity()) : 0.0));
                    if (collision > 0.01f) {
                        float repeat = 1.0f - (getDamageWobbleTicks() + 1) / 10.0f;
                        if (repeat > 0.0001f) {
                            float damage = collision * 20 * repeat * repeat;
                            NetworkHandler.sendToServer(new CollisionMessage(damage));
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {

    }

    public void setDamageWobbleStrength(float wobbleStrength) {
        entityData.set(DAMAGE_WOBBLE_STRENGTH, wobbleStrength);
    }

    public float getDamageWobbleStrength() {
        return entityData.get(DAMAGE_WOBBLE_STRENGTH);
    }

    public void setDamageWobbleTicks(int wobbleTicks) {
        entityData.set(DAMAGE_WOBBLE_TICKS, wobbleTicks);
    }

    public int getDamageWobbleTicks() {
        return entityData.get(DAMAGE_WOBBLE_TICKS);
    }

    public void setDamageWobbleSide(int side) {
        entityData.set(DAMAGE_WOBBLE_SIDE, side);
    }

    public int getDamageWobbleSide() {
        return entityData.get(DAMAGE_WOBBLE_SIDE);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < getPassengerSpace() && !isEyeInFluid(FluidTags.WATER);
    }

    @Override
    @Nullable
    public Entity getControllingPassenger() {
        return getFirstPassenger();
    }

    public void setInputs(float x, float y, float z) {
        this.movementX = x;
        this.movementY = y;
        this.movementZ = z;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(asItem());
    }

    public boolean isWithinParticleRange() {
        return Main.cameraGetter.getPosition().distanceToSqr(position()) < 1024;
    }

    protected Vector4f transformPosition(Matrix4f transform, float x, float y, float z) {
        Vector4f p0 = new Vector4f(x, y, z, 1);
        p0.transform(transform);
        return p0;
    }

    protected Vector3f transformVector(float x, float y, float z) {
        return transformVector(getVehicleNormalTransform(), x, y, z);
    }

    protected Vector3f transformVector(Matrix3f transform, float x, float y, float z) {
        Vector3f p0 = new Vector3f(x, y, z);
        p0.transform(transform);
        return p0;
    }

    protected Matrix4f getVehicleTransform() {
        Matrix4f transform = Matrix4f.createTranslateMatrix((float) getX(), (float) getY(), (float) getZ());
        transform.multiply(Vector3f.YP.rotationDegrees(-getYRot()));
        transform.multiply(Vector3f.XP.rotationDegrees(getXRot()));
        transform.multiply(Vector3f.ZP.rotationDegrees(getRoll()));
        return transform;
    }

    protected Matrix3f getVehicleNormalTransform() {
        Matrix3f transform = Matrix3f.createScaleMatrix(1.0f, 1.0f, 1.0f);
        transform.mul(Vector3f.YP.rotationDegrees(-getYRot()));
        transform.mul(Vector3f.XP.rotationDegrees(getXRot()));
        transform.mul(Vector3f.ZP.rotationDegrees(getRoll()));
        return transform;
    }

    public Vec3 getForwardDirection() {
        Vector3f f = transformVector(0.0f, 0.0f, 1.0f);
        return new Vec3(f.x(), f.y(), f.z());
    }

    public Vec3 getTopDirection() {
        Vector3f f = transformVector(0.0f, 1.0f, 0.0f);
        return new Vec3(f.x(), f.y(), f.z());
    }

    protected static final Vector4f ZERO_VEC4 = new Vector4f();

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = Config.getInstance().renderDistance * getViewScale();
        return distance < d * d;
    }

    public void chill() {

    }

    public boolean isValidDimension() {
        return Config.getInstance().validDimensions.getOrDefault(this.level.dimension().location().toString(), true);
    }
}