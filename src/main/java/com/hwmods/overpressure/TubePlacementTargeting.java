package com.hwmods.overpressure;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Shared by the preview and server placement; contains no client state. */
public final class TubePlacementTargeting {
    public static final int MAX_DISTANCE = 32;
    private static final double STRAIGHT_SNAP_DISTANCE = 1.5;

    public record FreeEnd(BlockPos pos, Direction outgoing) {
    }

    public static FreeEnd resolve(
            BlockPos firstTube,
            Direction incoming,
            @Nullable Direction branch,
            Vec3 aim,
            Vec3 look
    ) {
        Vec3 delta = aim.subtract(Vec3.atCenterOf(firstTube));
        if (delta.lengthSqr() > MAX_DISTANCE * MAX_DISTANCE) {
            delta = delta.normalize().scale(MAX_DISTANCE);
        }

        // Keep the start tangent in the construction plane. A horizontal start
        // stays level unless the player clearly aims farther up/down than sideways.
        Direction.Axis lateralAxis = null;
        double lateralDistance = -1.0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == incoming.getAxis()) {
                continue;
            }
            if (branch != null && branch.getAxis() != incoming.getAxis()
                    && axis != branch.getAxis()) {
                continue;
            }
            double distance = Math.abs(coordinate(delta, axis));
            if (distance > lateralDistance
                    || distance == lateralDistance && axis != Direction.Axis.Y) {
                lateralAxis = axis;
                lateralDistance = distance;
            }
        }

        int forwardOffset = (int) Math.round(coordinate(delta, incoming.getAxis()));
        int sideOffset = lateralDistance < STRAIGHT_SNAP_DISTANCE && branch == null
                ? 0 : (int) Math.round(coordinate(delta, lateralAxis));
        BlockPos end = offset(firstTube, incoming.getAxis(), forwardOffset);
        end = offset(end, lateralAxis, sideOffset);
        if (end.equals(firstTube)) {
            end = firstTube.relative(incoming, 2);
        }

        Direction outgoing = incoming;
        if (sideOffset != 0) {
            double forwardDistance = forwardOffset * incoming.getAxisDirection().getStep();
            double lookAlongStart = look.dot(Vec3.atLowerCornerOf(incoming.getNormal()));
            if (forwardDistance < Math.abs(sideOffset) * 2.0 || lookAlongStart < 0.75) {
                outgoing = Direction.fromAxisAndDirection(lateralAxis,
                        sideOffset > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            }
        }
        return new FreeEnd(end, outgoing);
    }

    private static double coordinate(Vec3 point, Direction.Axis axis) {
        return axis.choose(point.x, point.y, point.z);
    }

    private static BlockPos offset(BlockPos pos, Direction.Axis axis, int distance) {
        return pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), distance);
    }

    private TubePlacementTargeting() {
    }
}
