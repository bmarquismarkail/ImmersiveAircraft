package immersive_aircraft.entity;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import immersive_aircraft.Items;
import immersive_aircraft.Sounds;
import immersive_aircraft.entity.misc.AircraftProperties;
import immersive_aircraft.entity.misc.Trail;
import immersive_aircraft.entity.misc.VehicleInventoryDescription;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AirshipEntity extends Rotorcraft {
    private final AircraftProperties properties = new AircraftProperties(this)
            .setYawSpeed(5.0f)
            .setEngineSpeed(0.02f)
            .setVerticalSpeed(0.025f)
            .setGlideFactor(0.0f)
            .setDriftDrag(0.01f)
            .setLift(0.1f)
            .setRollFactor(5.0f)
            .setWindSensitivity(0.05f)
            .setMass(3.0f);

    private static final VehicleInventoryDescription inventoryDescription = new VehicleInventoryDescription()
            .addSlot(VehicleInventoryDescription.SlotType.BOILER, 8 + 9, 8 + 36)
            .addSlot(VehicleInventoryDescription.SlotType.WEAPON, 8 + 18 * 2 + 6, 8 + 6)
            .addSlot(VehicleInventoryDescription.SlotType.WEAPON, 8 + 18 * 2 + 28, 8 + 6)
            .addSlot(VehicleInventoryDescription.SlotType.UPGRADE, 8 + 18 * 2 + 6, 8 + 6 + 22)
            .addSlot(VehicleInventoryDescription.SlotType.UPGRADE, 8 + 18 * 2 + 28, 8 + 6 + 22)
            .addSlot(VehicleInventoryDescription.SlotType.BANNER, 8 + 18 * 2 + 6, 8 + 6 + 22 * 2)
            .addSlot(VehicleInventoryDescription.SlotType.DYE, 8 + 18 * 2 + 28, 8 + 6 + 22 * 2)
            .addSlots(VehicleInventoryDescription.SlotType.INVENTORY, 8 + 18 * 5, 8, 4, 4)
            .build();

    @Override
    public VehicleInventoryDescription getInventoryDescription() {
        return inventoryDescription;
    }

    public AirshipEntity(EntityType<? extends AircraftEntity> entityType, Level world) {
        super(entityType, world, true);
    }

    @Override
    protected float getEngineReactionSpeed() {
        return 50.0f;
    }

    protected SoundEvent getEngineSound() {
        return Sounds.PROPELLER_SMALL.get();
    }

    @Override
    public AircraftProperties getProperties() {
        return properties;
    }

    @Override
    protected float getGroundVelocityDecay() {
        return 0.5f;
    }

    @Override
    protected float getHorizontalVelocityDelay() {
        return 0.97f;
    }

    @Override
    protected float getVerticalVelocityDelay() {
        return 0.925f;
    }

    @Override
    protected float getStabilizer() {
        return 0.1f;
    }

    @Override
    public Item asItem() {
        return Items.AIRSHIP.get();
    }

    final List<List<Vec3>> PASSENGER_POSITIONS = List.of(
            List.of(
                    new Vec3(0.0f, -0.1f, 0.0f)
            ),
            List.of(
                    new Vec3(0.0f, -0.1f, 0.4f),
                    new Vec3(0.0f, -0.1f, -0.3f)
            )
    );

    private final List<Trail> trails = List.of(new Trail(15, 0.5f));

    public List<Trail> getTrails() {
        return trails;
    }

    void trail(Matrix4f transform) {
        trail(transform, 0);
    }

    void trail(Matrix4f transform, int index) {
        Vector4f p0 = transformPosition(transform, (float) 0.0 - 0.15f, 0.0f, 0.0f);
        Vector4f p1 = transformPosition(transform, (float) 0.0 + 0.15f, 0.0f, 0.0f);

        float trailStrength = Math.max(0.0f, Math.min(1.0f, (float) (getDeltaMovement().length() - 0.05f)));
        getTrails().get(index).add(p0, p1, trailStrength);
    }

    protected List<List<Vec3>> getPassengerPositions() {
        return PASSENGER_POSITIONS;
    }

    @Override
    protected float getGravity() {
        return wasTouchingWater ? 0.04f : (1.0f - getEnginePower()) * super.getGravity();
    }

    @Override
    protected void updateController() {
        super.updateController();

        setEngineTarget(1.0f);

        // up and down
        setDeltaMovement(getDeltaMovement().add(0.0f, getEnginePower() * properties.getVerticalSpeed() * pressingInterpolatedY.getSmooth(), 0.0f));

        // get pointing direction
        Vec3 direction = getForwardDirection();

        // accelerate
        float thrust = (float) (Math.pow(getEnginePower(), 5.0) * properties.getEngineSpeed()) * pressingInterpolatedZ.getSmooth();
        setDeltaMovement(getDeltaMovement().add(direction.scale(thrust)));
    }

    @Override
    public void tick() {
        super.tick();

        float power = getEnginePower();

        if (level.isClientSide) {
            if (isWithinParticleRange() && power > 0.01) {
                Matrix4f transform = getVehicleTransform();

                // Trails
                addTrails(transform);

                // Smoke
                if (tickCount % 2 == 0) {
                    Vector4f p = transformPosition(transform, (random.nextFloat() - 0.5f) * 0.4f, 0.8f, -0.8f);
                    Vec3 velocity = getDeltaMovement();
                    level.addParticle(ParticleTypes.SMOKE, p.x(), p.y(), p.z(), velocity.x, velocity.y, velocity.z);
                }
            } else {
                trails.get(0).add(ZERO_VEC4, ZERO_VEC4, 0.0f);
            }
        }
    }

    protected void addTrails(Matrix4f transform) {
        Matrix4f tr = transform.copy();
        tr.multiplyWithTranslation(0.0f, 0.4f, -1.2f);
        tr.multiply(Vector3f.ZP.rotationDegrees(engineRotation.getSmooth() * 50.0f));
        trail(tr);
    }
}
