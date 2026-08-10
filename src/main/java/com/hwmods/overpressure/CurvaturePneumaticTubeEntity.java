package com.hwmods.overpressure;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CurvaturePneumaticTubeEntity extends PneumaticTubeBlockEntity {
    private Vec3 p0 = new Vec3(0.5, 0.5, 0.0);
    private Vec3 p1 = new Vec3(0.5, 0.5, 0.35);
    private Vec3 p2 = new Vec3(0.65, 0.5, 0.5);
    private Vec3 p3 = new Vec3(1.0, 0.5, 0.5);
    private UUID sectionId = UUID.randomUUID();

    public CurvaturePneumaticTubeEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CURVATURE_PNEUMATIC_TUBE.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CurvaturePneumaticTubeEntity tube) {
        PneumaticTubeBlockEntity.serverTick(level, pos, state, tube);
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        BlockState state = getBlockState();

        if (!(state.getBlock() instanceof PneumaticTubeBlock)
                && !(state.getBlock() instanceof CurvaturePneumaticTubeBlock)) {
            return false;
        }

        return state.getValue(getConnectionProperty(direction));
    }

    public void setCurve(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        boolean changed = !this.p0.equals(p0)
                || !this.p1.equals(p1)
                || !this.p2.equals(p2)
                || !this.p3.equals(p3);
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        setChanged();

        if (level != null) {
            if (changed) {
                PneumaticTubeBlockEntity.invalidateClientPathAt(level, worldPosition);
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = sectionId;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public Vec3 getPoint(float t) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;

        return p0.scale(uu * u)
                .add(p1.scale(3.0 * uu * t))
                .add(p2.scale(3.0 * u * tt))
                .add(p3.scale(tt * t));
    }

    public Vec3 getP0() {
        return p0;
    }

    public Vec3 getP1() {
        return p1;
    }

    public Vec3 getP2() {
        return p2;
    }

    public Vec3 getP3() {
        return p3;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        savePoint(tag, "p0", p0);
        savePoint(tag, "p1", p1);
        savePoint(tag, "p2", p2);
        savePoint(tag, "p3", p3);
        tag.putUUID("section_id", sectionId);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        Vec3 previousP0 = p0;
        Vec3 previousP1 = p1;
        Vec3 previousP2 = p2;
        Vec3 previousP3 = p3;
        super.read(tag, registries, clientPacket);
        p0 = loadPoint(tag, "p0", p0);
        p1 = loadPoint(tag, "p1", p1);
        p2 = loadPoint(tag, "p2", p2);
        p3 = loadPoint(tag, "p3", p3);

        if (tag.hasUUID("section_id")) {
            sectionId = tag.getUUID("section_id");
        }

        if (clientPacket && level != null
                && (!previousP0.equals(p0)
                || !previousP1.equals(p1)
                || !previousP2.equals(p2)
                || !previousP3.equals(p3))) {
            PneumaticTubeBlockEntity.invalidateClientPathAt(level, worldPosition);
        }
    }

    private void savePoint(CompoundTag tag, String name, Vec3 point) {
        CompoundTag pointTag = new CompoundTag();
        pointTag.putDouble("x", point.x);
        pointTag.putDouble("y", point.y);
        pointTag.putDouble("z", point.z);
        tag.put(name, pointTag);
    }

    private Vec3 loadPoint(CompoundTag tag, String name, Vec3 fallback) {
        if (!tag.contains(name)) {
            return fallback;
        }

        CompoundTag pointTag = tag.getCompound(name);
        return new Vec3(pointTag.getDouble("x"), pointTag.getDouble("y"), pointTag.getDouble("z"));
    }
}
