package com.hwmods.overpressure;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Spatial lookup for curve geometry that can be far from its logical path cell. */
public final class CurveCollisionIndex {
    // Values contain positions and bounds only, so they do not retain the weak world key.
    private static final Map<Level, CurveCollisionIndex> WORLDS = new WeakHashMap<>();
    private final Map<BlockPos, AABB> bounds = new HashMap<>();
    private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();

    private static long chunkKey(int x, int z) {
        return net.minecraft.world.level.ChunkPos.asLong(x, z);
    }

    private static void forChunks(AABB box, java.util.function.LongConsumer action) {
        for (int x = ((int) Math.floor(box.minX)) >> 4; x <= ((int) Math.floor(box.maxX)) >> 4; x++) {
            for (int z = ((int) Math.floor(box.minZ)) >> 4; z <= ((int) Math.floor(box.maxZ)) >> 4; z++) {
                action.accept(chunkKey(x, z));
            }
        }
    }

    public static synchronized void update(CurvaturePneumaticTubeEntity tube) {
        Level level = tube.getLevel();
        if (level == null || tube.isRemoved()) return;
        remove(tube);
        var index = WORLDS.computeIfAbsent(level, ignored -> new CurveCollisionIndex());
        Vec3 origin = Vec3.atLowerCornerOf(tube.getBlockPos());
        AABB box = new AABB(origin.add(tube.getP0()), origin.add(tube.getP3()))
                .minmax(new AABB(origin.add(tube.getP1()), origin.add(tube.getP2()))).inflate(0.35);
        BlockPos pos = tube.getBlockPos().immutable();
        index.bounds.put(pos, box);
        forChunks(box, key -> index.chunks.computeIfAbsent(key, ignored -> new HashSet<>()).add(pos));
    }

    public static synchronized void remove(CurvaturePneumaticTubeEntity tube) {
        var index = WORLDS.get(tube.getLevel());
        if (index == null) return;
        BlockPos pos = tube.getBlockPos();
        AABB box = index.bounds.remove(pos);
        if (box == null) return;
        forChunks(box, key -> {
            var positions = index.chunks.get(key);
            if (positions != null) {
                positions.remove(pos);
                if (positions.isEmpty()) index.chunks.remove(key);
            }
        });
    }

    public static synchronized List<VoxelShape> collisions(Level level, AABB query) {
        var index = WORLDS.get(level);
        if (index == null) return List.of();
        Set<BlockPos> candidates = new HashSet<>();
        forChunks(query, key -> {
            var positions = index.chunks.get(key);
            if (positions != null) candidates.addAll(positions);
        });
        List<VoxelShape> result = new ArrayList<>();
        VoxelShape queryShape = Shapes.create(query);
        for (BlockPos pos : candidates) {
            if (!index.bounds.get(pos).intersects(query) || !level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof CurvaturePneumaticTubeEntity tube) || tube.isRemoved()) continue;
            VoxelShape shape = tube.getTubeShape().move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(shape, queryShape, BooleanOp.AND)) result.add(shape);
        }
        return result;
    }

    public static synchronized BlockHitResult clip(Level level, ClipContext context, BlockHitResult nearest) {
        var index = WORLDS.get(level);
        if (index == null) return nearest;
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        AABB query = new AABB(from, to).inflate(1.0E-6);
        Set<BlockPos> candidates = new HashSet<>();
        forChunks(query, key -> {
            var positions = index.chunks.get(key);
            if (positions != null) candidates.addAll(positions);
        });
        double nearestDistance = from.distanceToSqr(nearest.getLocation());
        for (BlockPos pos : candidates) {
            if (!index.bounds.get(pos).intersects(query) || !level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof CurvaturePneumaticTubeEntity tube) || tube.isRemoved()) continue;
            // Respect OUTLINE/COLLIDER/VISUAL selection instead of changing camera ray casts.
            VoxelShape shape = context.getBlockShape(tube.getBlockState(), level, pos);
            BlockHitResult hit = shape.clip(from, to, pos);
            if (hit != null && from.distanceToSqr(hit.getLocation()) < nearestDistance) {
                nearest = hit;
                nearestDistance = from.distanceToSqr(hit.getLocation());
            }
        }
        return nearest;
    }
}
