package com.hwmods.overpressure.tube;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A small server-timestamped buffer: packet arrival never resets the rendered segment. */
public final class CargoAnimation {
    public static final int RENDER_DELAY_TICKS = 4;
    public record Path(UUID section, boolean sectionStart, BlockPos block, Vec3 entry, Vec3 exit, double length) {
        public boolean isSection() { return section != null; }
    }
    public record Sample(long tick, Vec3 position, Vec3 direction, UUID section, double distance,
                         Path path, double pathDistance) {
        public Sample(long tick, Vec3 position, Vec3 direction, UUID section, double distance) {
            this(tick, position, direction, section, distance, null, distance);
        }
    }
    public record Pose(Vec3 position, Vec3 direction, UUID section, double distance,
                       Path path, double pathDistance) {}
    private final List<Sample> samples = new ArrayList<>();
    private long removedAt = Long.MAX_VALUE;

    public void add(Sample sample) {
        if (!samples.isEmpty() && sample.tick() <= samples.getLast().tick()) return;
        samples.add(sample);
        removedAt = Long.MAX_VALUE;
        while (samples.size() > 32) samples.removeFirst();
    }

    public void removed(long tick) { removedAt = Math.min(removedAt, tick); }
    public boolean expired(double tick) { return tick >= removedAt; }

    public Pose at(double tick) {
        if (samples.isEmpty()) return null;
        Sample a = samples.getFirst();
        if (tick <= a.tick()) return pose(a);
        for (int i = 1; i < samples.size(); i++) {
            Sample b = samples.get(i);
            if (tick <= b.tick()) {
                double t = (tick - a.tick()) / (b.tick() - a.tick());
                Vec3 direction = a.direction().lerp(b.direction(), t);
                if (direction.lengthSqr() < 1.0E-8) direction = a.direction();
                if (a.path() != null && a.path().equals(b.path())) {
                    return poseOnPath(a.path(), a.pathDistance()
                            + (b.pathDistance() - a.pathDistance()) * t, direction, a.position().lerp(b.position(), t));
                }
                if (a.path() != null && b.path() != null
                        && a.path().exit().distanceToSqr(b.path().entry()) < 1.0E-8) {
                    double remaining = Math.max(0, a.path().length() - a.pathDistance());
                    double total = remaining + Math.max(0, b.pathDistance());
                    double traveled = total * t;
                    return traveled <= remaining
                            ? poseOnPath(a.path(), a.pathDistance() + traveled, direction, a.position().lerp(b.position(), t))
                            : poseOnPath(b.path(), traveled - remaining, direction, a.position().lerp(b.position(), t));
                }
                UUID section = a.section() != null && a.section().equals(b.section()) ? a.section() : null;
                return new Pose(a.position().lerp(b.position(), t), direction.normalize(), section,
                        a.distance() + (b.distance() - a.distance()) * t, null, 0);
            }
            a = b;
        }
        return pose(a); // Never predict through a closed valve or a queued item.
    }

    private static Pose pose(Sample sample) {
        return sample.path() == null
                ? new Pose(sample.position(), sample.direction(), sample.section(), sample.distance(), null, 0)
                : poseOnPath(sample.path(), sample.pathDistance(), sample.direction(), sample.position());
    }

    private static Pose poseOnPath(Path path, double distance, Vec3 fallbackDirection, Vec3 fallbackPosition) {
        double clamped = Math.max(0, Math.min(path.length(), distance));
        double sectionDistance = path.isSection()
                ? (path.sectionStart() ? clamped : path.length() - clamped) : 0;
        if (path.isSection()) {
            return new Pose(fallbackPosition, fallbackDirection.normalize(), path.section(), sectionDistance,
                    path, clamped);
        }
        Vec3 position = blockPosition(path, clamped);
        Vec3 before = blockPosition(path, Math.max(0, clamped - 0.01));
        Vec3 after = blockPosition(path, Math.min(path.length(), clamped + 0.01));
        Vec3 direction = after.subtract(before);
        if (direction.lengthSqr() < 1.0E-8) direction = fallbackDirection;
        return new Pose(position, direction.normalize(), null, 0, path, clamped);
    }

    private static Vec3 blockPosition(Path path, double distance) {
        Vec3 center = Vec3.atCenterOf(path.block());
        double first = path.entry().distanceTo(center);
        double second = center.distanceTo(path.exit());
        return distance < first
                ? path.entry().lerp(center, Math.min(1, distance / Math.max(first, 1.0E-6)))
                : center.lerp(path.exit(), Math.min(1, (distance - first) / Math.max(second, 1.0E-6)));
    }
}
