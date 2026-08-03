package com.hwmods.overpressure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GrindRailBlockItem extends BlockItem {
    private static final double MAX_DIRECT_DISTANCE = 64.0;
    private static final int SAMPLES_PER_BLOCK = 16;
    private static final Map<UUID, RailAnchor> SERVER_STARTS = new HashMap<>();
    private static final Map<UUID, RailAnchor> CLIENT_STARTS = new HashMap<>();

    public GrindRailBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Map<UUID, RailAnchor> starts = context.getLevel().isClientSide ? CLIENT_STARTS : SERVER_STARTS;
        UUID playerId = player.getUUID();
        if (player.isShiftKeyDown()) {
            starts.remove(playerId);
            return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        RailAnchor target = getAnchor(context.getLevel(), context.getClickedPos(), context.getClickedFace(),
                context.getClickLocation());
        RailAnchor start = starts.get(playerId);
        if (start == null) {
            starts.put(playerId, target);
            return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        if (start.point.distanceToSqr(target.point) < 0.04) {
            starts.remove(playerId);
            return context.getLevel().isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }

        RailPlan plan = createPlan(context.getLevel(), start, target);
        if (context.getLevel().isClientSide) {
            if (plan.valid()) {
                starts.remove(playerId);
            }
            return InteractionResult.SUCCESS;
        }

        if (!plan.valid()) {
            player.displayClientMessage(Component.translatable(plan.errorKey()), true);
            return InteractionResult.CONSUME;
        }

        if (!placeRail(context.getLevel(), player, context.getItemInHand(), plan)) {
            return InteractionResult.CONSUME;
        }

        starts.remove(playerId);
        return InteractionResult.CONSUME;
    }

    private boolean placeRail(Level level, Player player, ItemStack stack, RailPlan plan) {
        List<BlockPos> newPositions = new ArrayList<>();
        for (BlockPos pos : plan.positions()) {
            if (!level.getBlockState(pos).is(ModBlocks.GRIND_RAIL.get())) {
                newPositions.add(pos);
            }
        }

        if (newPositions.isEmpty()) {
            player.displayClientMessage(Component.translatable("overpressure.grind_rail.error.overlap"), true);
            return false;
        }
        if (!player.getAbilities().instabuild && stack.getCount() < newPositions.size()) {
            player.displayClientMessage(Component.translatable("overpressure.grind_rail.error.items"), true);
            return false;
        }

        UUID sectionId = UUID.randomUUID();
        Map<BlockPos, RailSpan> spans = sampleMarkerSpans(plan.p0(), plan.p1(), plan.p2(), plan.p3());
        BlockPos firstNewPosition = newPositions.get(0);
        BlockPos lastNewPosition = newPositions.get(newPositions.size() - 1);
        BlockState railState = ModBlocks.GRIND_RAIL.get().defaultBlockState();
        for (BlockPos pos : newPositions) {
            level.setBlock(pos, railState, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof GrindRailBlockEntity rail) {
                RailSpan span = spans.get(pos);
                double renderStart = pos.equals(firstNewPosition) ? 0.0 : span.start();
                double renderEnd = pos.equals(lastNewPosition) ? 1.0 : span.end();
                rail.setCurve(plan.p0(), plan.p1(), plan.p2(), plan.p3(), sectionId, renderStart, renderEnd);
            }
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(newPositions.size());
        }
        return true;
    }

    public RailPlan createClientPlan(Level level, RailAnchor start, BlockPos pos, Direction face, Vec3 hitLocation) {
        return createPlan(level, start, getAnchor(level, pos, face, hitLocation));
    }

    public static RailPlan createPlan(Level level, RailAnchor start, RailAnchor end) {
        double distance = start.point.distanceTo(end.point);
        if (distance < 0.75) {
            return RailPlan.invalid("overpressure.grind_rail.error.short");
        }
        if (distance > MAX_DIRECT_DISTANCE) {
            return RailPlan.invalid("overpressure.grind_rail.error.long");
        }

        double handle = Math.max(1.0, distance * 0.38);
        Vec3 p0 = start.point;
        Vec3 p1 = p0.add(start.tangent.scale(handle));
        Vec3 p3 = end.point;
        Vec3 p2 = p3.add(end.tangent.scale(handle));
        Set<BlockPos> sampledPositions = sampleMarkerSpans(p0, p1, p2, p3).keySet();

        if (sampledPositions.size() < 2) {
            return RailPlan.invalid("overpressure.grind_rail.error.short");
        }

        for (BlockPos pos : sampledPositions) {
            if (level.isOutsideBuildHeight(pos)) {
                return RailPlan.invalid("overpressure.grind_rail.error.blocked");
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.GRIND_RAIL.get()) && !state.canBeReplaced()) {
                return RailPlan.invalid("overpressure.grind_rail.error.blocked");
            }
        }

        return new RailPlan(p0, p1, p2, p3, List.copyOf(sampledPositions), null);
    }

    private static Map<BlockPos, RailSpan> sampleMarkerSpans(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        double controlLength = p0.distanceTo(p1) + p1.distanceTo(p2) + p2.distanceTo(p3);
        int samples = Math.max(32, (int) Math.ceil(controlLength * SAMPLES_PER_BLOCK));
        double overlap = 1.0 / samples;
        Map<BlockPos, RailSpan> spans = new LinkedHashMap<>();
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            BlockPos pos = BlockPos.containing(getPoint(p0, p1, p2, p3, t));
            RailSpan previous = spans.get(pos);
            double sampleStart = Math.max(0.0, t - overlap);
            double sampleEnd = Math.min(1.0, t + overlap);
            spans.put(pos, previous == null
                    ? new RailSpan(sampleStart, sampleEnd)
                    : new RailSpan(
                            Math.min(previous.start(), sampleStart),
                            Math.max(previous.end(), sampleEnd)
                    ));
        }
        return spans;
    }

    public static Vec3 getPoint(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        return p0.scale(u * u * u)
                .add(p1.scale(3.0 * u * u * t))
                .add(p2.scale(3.0 * u * t * t))
                .add(p3.scale(t * t * t));
    }

    public static Vec3 getTangent(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double u = 1.0 - t;
        Vec3 tangent = p1.subtract(p0).scale(3.0 * u * u)
                .add(p2.subtract(p1).scale(6.0 * u * t))
                .add(p3.subtract(p2).scale(3.0 * t * t));
        return tangent.lengthSqr() < 1.0E-8 ? Vec3.ZERO : tangent.normalize();
    }

    public static RailAnchor getAnchor(Level level, BlockPos pos, Direction face, Vec3 hitLocation) {
        if (level.getBlockEntity(pos) instanceof GrindRailBlockEntity rail) {
            Vec3 p0 = rail.getWorldP0();
            Vec3 p3 = rail.getWorldP3();
            if (hitLocation.distanceToSqr(p0) <= hitLocation.distanceToSqr(p3)) {
                Vec3 tangent = p0.subtract(rail.getWorldP1()).normalize();
                return new RailAnchor(p0, tangent);
            }
            Vec3 tangent = p3.subtract(rail.getWorldP2()).normalize();
            return new RailAnchor(p3, tangent);
        }

        Vec3 point = Vec3.atCenterOf(pos.relative(face));
        return new RailAnchor(point, Vec3.atLowerCornerOf(face.getNormal()));
    }

    @Nullable
    public static RailAnchor getClientStart(UUID playerId) {
        return CLIENT_STARTS.get(playerId);
    }

    public record RailAnchor(Vec3 point, Vec3 tangent) {
    }

    private record RailSpan(double start, double end) {
    }

    public record RailPlan(
            @Nullable Vec3 p0,
            @Nullable Vec3 p1,
            @Nullable Vec3 p2,
            @Nullable Vec3 p3,
            List<BlockPos> positions,
            @Nullable String errorKey
    ) {
        public static RailPlan invalid(String errorKey) {
            return new RailPlan(null, null, null, null, List.of(), errorKey);
        }

        public boolean valid() {
            return errorKey == null;
        }
    }
}
