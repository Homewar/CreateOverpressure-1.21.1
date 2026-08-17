package com.hwmods.overpressure;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GrindRailBlockEntity extends SmartBlockEntity {
    private Vec3 p0 = new Vec3(0.0, 0.5, 0.5);
    private Vec3 p1 = new Vec3(0.35, 0.5, 0.5);
    private Vec3 p2 = new Vec3(0.65, 0.5, 0.5);
    private Vec3 p3 = new Vec3(1.0, 0.5, 0.5);
    private UUID sectionId = UUID.randomUUID();
    private double renderStart;
    private double renderEnd = 1.0;
    private boolean curveInitialized;
    private final Map<Direction, UUID> edgeSections = new EnumMap<>(Direction.class);

    public GrindRailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRIND_RAIL.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void setCurve(
            Vec3 worldP0,
            Vec3 worldP1,
            Vec3 worldP2,
            Vec3 worldP3,
            UUID id,
            double start,
            double end
    ) {
        Vec3 origin = Vec3.atLowerCornerOf(worldPosition);
        p0 = worldP0.subtract(origin);
        p1 = worldP1.subtract(origin);
        p2 = worldP2.subtract(origin);
        p3 = worldP3.subtract(origin);
        sectionId = id;
        renderStart = start;
        renderEnd = end;
        curveInitialized = true;

        if (level != null && getBlockState().getBlock() instanceof GrindRailSupportBlock
                && !getBlockState().getValue(GrindRailSupportBlock.CONNECTED)) {
            level.setBlock(
                    worldPosition,
                    getBlockState().setValue(GrindRailSupportBlock.CONNECTED, true),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            );
        }
        setChanged();

        if (level != null) {
            sendData();
        }
    }

    public Vec3 getPoint(double t) {
        double u = 1.0 - t;
        return p0.scale(u * u * u)
                .add(p1.scale(3.0 * u * u * t))
                .add(p2.scale(3.0 * u * t * t))
                .add(p3.scale(t * t * t));
    }

    public Vec3 getWorldPoint(double t) {
        return Vec3.atLowerCornerOf(worldPosition).add(getPoint(t));
    }

    public Vec3 getWorldTangent(double t) {
        double u = 1.0 - t;
        Vec3 tangent = p1.subtract(p0).scale(3.0 * u * u)
                .add(p2.subtract(p1).scale(6.0 * u * t))
                .add(p3.subtract(p2).scale(3.0 * t * t));
        return tangent.lengthSqr() < 1.0E-8 ? Vec3.ZERO : tangent.normalize();
    }

    public Vec3 getWorldP0() {
        return Vec3.atLowerCornerOf(worldPosition).add(p0);
    }

    public Vec3 getWorldP1() {
        return Vec3.atLowerCornerOf(worldPosition).add(p1);
    }

    public Vec3 getWorldP2() {
        return Vec3.atLowerCornerOf(worldPosition).add(p2);
    }

    public Vec3 getWorldP3() {
        return Vec3.atLowerCornerOf(worldPosition).add(p3);
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public boolean shouldRenderCurve() {
        return curveInitialized && renderEnd > renderStart;
    }

    public boolean hasCurve() {
        return curveInitialized;
    }

    public boolean isEdgeOccupied(Direction edge) {
        if (edgeSections.containsKey(edge)) {
            return true;
        }
        return curveInitialized
                && getBlockState().getBlock() instanceof GrindRailSupportBlock
                && edgeSections.isEmpty();
    }

    public boolean hasSection(UUID id) {
        return sectionId.equals(id) || edgeSections.containsValue(id);
    }

    public Set<UUID> getConnectedSectionIds() {
        Set<UUID> sections = new HashSet<>(edgeSections.values());
        if (curveInitialized && getBlockState().getBlock() instanceof GrindRailSupportBlock) {
            sections.add(sectionId);
        }
        return sections;
    }

    public void connectEdge(Direction edge, UUID id) {
        if (!edge.getAxis().isHorizontal()) {
            return;
        }
        edgeSections.put(edge, id);
        updateSupportState();
        setChanged();
        if (level != null) {
            sendData();
        }
    }

    public void disconnectSection(UUID id) {
        boolean changed = edgeSections.entrySet().removeIf(entry -> entry.getValue().equals(id));
        if (curveInitialized && sectionId.equals(id)
                && getBlockState().getBlock() instanceof GrindRailSupportBlock) {
            curveInitialized = false;
            renderStart = 0.0;
            renderEnd = 0.0;
            changed = true;
        }
        if (!changed) {
            return;
        }

        updateSupportState();
        setChanged();
        if (level != null) {
            sendData();
        }
    }

    private void updateSupportState() {
        if (level == null || !(getBlockState().getBlock() instanceof GrindRailSupportBlock)) {
            return;
        }
        boolean connected = !edgeSections.isEmpty() || curveInitialized;
        if (getBlockState().getValue(GrindRailSupportBlock.CONNECTED) != connected) {
            level.setBlock(
                    worldPosition,
                    getBlockState().setValue(GrindRailSupportBlock.CONNECTED, connected),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            );
        }
    }

    public double getRenderStart() {
        return renderStart;
    }

    public double getRenderEnd() {
        return renderEnd;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        savePoint(tag, "p0", p0);
        savePoint(tag, "p1", p1);
        savePoint(tag, "p2", p2);
        savePoint(tag, "p3", p3);
        tag.putUUID("section_id", sectionId);
        tag.putDouble("render_start", renderStart);
        tag.putDouble("render_end", renderEnd);
        tag.putBoolean("curve_initialized", curveInitialized);
        for (Map.Entry<Direction, UUID> entry : edgeSections.entrySet()) {
            tag.putUUID("edge_" + entry.getKey().getName(), entry.getValue());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        p0 = loadPoint(tag, "p0", p0);
        p1 = loadPoint(tag, "p1", p1);
        p2 = loadPoint(tag, "p2", p2);
        p3 = loadPoint(tag, "p3", p3);
        if (tag.hasUUID("section_id")) {
            sectionId = tag.getUUID("section_id");
        }
        renderStart = tag.getDouble("render_start");
        renderEnd = tag.contains("render_end") ? tag.getDouble("render_end") : 1.0;
        curveInitialized = tag.contains("curve_initialized")
                ? tag.getBoolean("curve_initialized")
                : getBlockState().is(ModBlocks.GRIND_RAIL.get());
        edgeSections.clear();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            String key = "edge_" + direction.getName();
            if (tag.hasUUID(key)) {
                edgeSections.put(direction, tag.getUUID(key));
            }
        }
    }

    private static void savePoint(CompoundTag tag, String key, Vec3 point) {
        CompoundTag pointTag = new CompoundTag();
        pointTag.putDouble("x", point.x);
        pointTag.putDouble("y", point.y);
        pointTag.putDouble("z", point.z);
        tag.put(key, pointTag);
    }

    private static Vec3 loadPoint(CompoundTag tag, String key, Vec3 fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        CompoundTag pointTag = tag.getCompound(key);
        return new Vec3(pointTag.getDouble("x"), pointTag.getDouble("y"), pointTag.getDouble("z"));
    }
}
