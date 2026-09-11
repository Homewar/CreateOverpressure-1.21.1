package com.hwmods.overpressure.tube;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** A small server-timestamped buffer: packet arrival never resets the rendered segment. */
public final class CargoAnimation {
    public static final int RENDER_DELAY_TICKS = 4;
    public record Sample(long tick, Vec3 position, Vec3 direction, UUID section, double distance) {}
    public record Pose(Vec3 position, Vec3 direction, UUID section, double distance) {}
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
                UUID section = a.section() != null && a.section().equals(b.section()) ? a.section() : null;
                return new Pose(a.position().lerp(b.position(), t), direction.normalize(), section,
                        a.distance() + (b.distance() - a.distance()) * t);
            }
            a = b;
        }
        return pose(a); // Never predict through a closed valve or a queued item.
    }

    private static Pose pose(Sample sample) {
        return new Pose(sample.position(), sample.direction(), sample.section(), sample.distance());
    }
}
