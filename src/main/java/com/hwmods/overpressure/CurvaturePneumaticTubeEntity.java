package com.hwmods.overpressure;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CurvaturePneumaticTubeEntity extends PneumaticTubeBlockEntity {
    private Vec3 p0 = new Vec3(0.5, 0.5, 0.0);
    private Vec3 p1 = new Vec3(0.5, 0.5, 0.35);
    private Vec3 p2 = new Vec3(0.65, 0.5, 0.5);
    private Vec3 p3 = new Vec3(1.0, 0.5, 0.5);
    private UUID sectionId = UUID.randomUUID();
    private VoxelShape tubeShape;

    public CurvaturePneumaticTubeEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CURVATURE_PNEUMATIC_TUBE.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        CurveCollisionIndex.update(this);
    }

    @Override
    public void invalidate() {
        CurveCollisionIndex.remove(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        CurveCollisionIndex.remove(this);
        super.onChunkUnloaded();
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
        if (changed) {
            tubeShape = null;
            CurveCollisionIndex.update(this);
        }
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

    public Vec3 getTangent(float t) {
        double clampedT = Math.max(0.0, Math.min(1.0, t));
        double u = 1.0 - clampedT;
        Vec3 tangent = p1.subtract(p0).scale(3.0 * u * u)
                .add(p2.subtract(p1).scale(6.0 * u * clampedT))
                .add(p3.subtract(p2).scale(3.0 * clampedT * clampedT));
        return tangent.lengthSqr() < 1.0E-8 ? Vec3.ZERO : tangent.normalize();
    }

    public VoxelShape getTubeShape() {
        if (tubeShape != null) {
            return tubeShape;
        }

        // Match the renderer's square cross sections, using short boxes along the bend.
        // Keep coordinates relative to the owning block, including parts outside its cell.
        VoxelShape shape = Shapes.empty();
        Vec3 previousRight = null;
        Vec3 previousMin = null;
        Vec3 previousMax = null;
        for (int i = 0; i <= 18; i++) {
            float t = i / 18.0f;
            Vec3 center = getPoint(t);
            Vec3 tangent = getPoint(Math.min(1, t + 0.01f))
                    .subtract(getPoint(Math.max(0, t - 0.01f)));
            tangent = tangent.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : tangent.normalize();
            Vec3 right = previousRight == null ? Vec3.ZERO
                    : previousRight.subtract(tangent.scale(previousRight.dot(tangent)));
            if (right.lengthSqr() < 1.0E-6) {
                right = tangent.cross(Math.abs(tangent.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
            }
            right = right.normalize();
            Vec3 up = right.cross(tangent).normalize();
            Vec3 extent = new Vec3(Math.abs(right.x) + Math.abs(up.x),
                    Math.abs(right.y) + Math.abs(up.y), Math.abs(right.z) + Math.abs(up.z)).scale(0.2425);
            Vec3 min = center.subtract(extent);
            Vec3 max = center.add(extent);
            if (previousMin != null) {
                shape = Shapes.or(shape, Shapes.box(
                        Math.min(previousMin.x, min.x), Math.min(previousMin.y, min.y), Math.min(previousMin.z, min.z),
                        Math.max(previousMax.x, max.x), Math.max(previousMax.y, max.y), Math.max(previousMax.z, max.z)));
            }
            previousRight = right;
            previousMin = min;
            previousMax = max;
        }
        tubeShape = shape;
        return tubeShape;
    }

    public Vec3 getP0() {
        return p0;
    }

    public boolean needsEndTrim(boolean start) {
        if (level == null) return false;
        Vec3 endpoint = Vec3.atLowerCornerOf(worldPosition).add(start ? p0 : p3);
        // Logical path cells can be displaced from the rendered curve. Compare actual joins.
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof CurvaturePneumaticTubeEntity next) {
                Vec3 origin = Vec3.atLowerCornerOf(next.getBlockPos());
                if (endpoint.distanceTo(origin.add(next.getP0())) < 1.0E-4
                        || endpoint.distanceTo(origin.add(next.getP3())) < 1.0E-4) return false;
            }
        }
        Vec3 outward = getTangent(start ? 0 : 1).scale(start ? -1 : 1);
        BlockPos beyond = BlockPos.containing(endpoint.add(outward.scale(0.05)));
        BlockState neighbor = level.getBlockState(beyond);
        if (neighbor.is(ModBlocks.PNEUMATIC_TUBE.get()) || neighbor.getBlock() instanceof EncasedPneumaticTubeBlock) {
            Direction intoTube = Direction.getNearest(outward.x, outward.y, outward.z).getOpposite();
            if (neighbor.getValue(PneumaticTubeBlock.getConnectionProperty(intoTube))) return false;
        }
        return true;
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
        CurveCollisionIndex.update(this);
        if (!previousP0.equals(p0) || !previousP1.equals(p1)
                || !previousP2.equals(p2) || !previousP3.equals(p3)) {
            tubeShape = null;
        }

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
