package com.hwmods.overpressure.tube;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hwmods.overpressure.math.CubicBezier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared world-space geometry for drawing, picking, collision and cargo movement. */
public final class TubeSectionGeometry {
    public static final double HALF_WIDTH = 0.2425;
    private final List<Frame> frames;
    private final List<AABB> boxes;
    private final AABB bounds;
    private final double length;

    public TubeSectionGeometry(List<TubeSection.Span> spans) {
        List<Frame> samples = new ArrayList<>();
        List<AABB> collision = new ArrayList<>();
        Vec3 previousRight = null;
        Frame previous = null;
        double distance = 0;
        AABB total = null;
        for (int spanIndex = 0; spanIndex < spans.size(); spanIndex++) {
            CubicBezier curve = spans.get(spanIndex).curve();
            double hullLength = curve.p0().distanceTo(curve.p1()) + curve.p1().distanceTo(curve.p2())
                    + curve.p2().distanceTo(curve.p3());
            if (!Double.isFinite(hullLength) || hullLength > 2048) {
                throw new IllegalArgumentException("Tube geometry is too large");
            }
            int count = Math.max(18, (int) Math.ceil(hullLength * 12));
            for (int i = 0; i <= count; i++) {
                if (spanIndex > 0 && i == 0) continue;
                double t = (double) i / count;
                Vec3 center = curve.pointAt(t);
                Vec3 tangent = curve.derivativeAt(t).normalize();
                Vec3 right = previousRight == null ? Vec3.ZERO
                        : previousRight.subtract(tangent.scale(previousRight.dot(tangent)));
                if (right.lengthSqr() < 1.0E-8) {
                    right = tangent.cross(Math.abs(tangent.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
                }
                right = right.normalize();
                Vec3 up = right.cross(tangent).normalize();
                if (previous != null) distance += center.distanceTo(previous.center());
                Frame frame = new Frame(center, right, up, distance, spanIndex, t);
                samples.add(frame);
                if (previous != null) {
                    // AABB uses a half-open upper bound. Include the end face as well,
                    // so picking and collision remain continuous at joins and open ends.
                    AABB box = previous.bounds().minmax(frame.bounds()).inflate(1.0E-7);
                    collision.add(box);
                    total = total == null ? box : total.minmax(box);
                }
                previous = frame;
                previousRight = right;
            }
        }
        if (distance < 1.0E-6 || total == null) throw new IllegalArgumentException("Empty tube geometry");
        frames = List.copyOf(samples);
        boxes = List.copyOf(collision);
        bounds = total;
        length = distance;
    }

    public List<Frame> frames() { return frames; }
    public List<AABB> boxes() { return boxes; }
    public AABB bounds() { return bounds; }
    public double length() { return length; }

    /** Distance, rather than Bezier parameter, keeps cargo speed constant through bends. */
    public Vec3 pointAtDistance(double distance) {
        if (distance <= 0) return frames.getFirst().center();
        if (distance >= length) return frames.getLast().center();
        int lo = 0, hi = frames.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (frames.get(mid).distance() < distance) lo = mid; else hi = mid;
        }
        Frame a = frames.get(lo), b = frames.get(hi);
        return a.center().lerp(b.center(), (distance - a.distance()) / (b.distance() - a.distance()));
    }

    public Optional<Vec3> clip(Vec3 start, Vec3 end) {
        Vec3 nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (AABB box : boxes) {
            Optional<Vec3> hit = box.contains(start) ? Optional.of(start) : box.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < best) {
                nearest = hit.get();
                best = start.distanceToSqr(nearest);
            }
        }
        return Optional.ofNullable(nearest);
    }

    public record Frame(Vec3 center, Vec3 right, Vec3 up, double distance, int span, double parameter) {
        public Vec3 corner(int index) {
            return center.add(right.scale(index == 0 || index == 3 ? HALF_WIDTH : -HALF_WIDTH))
                    .add(up.scale(index < 2 ? HALF_WIDTH : -HALF_WIDTH));
        }
        public AABB bounds() {
            Vec3 extent = new Vec3(Math.abs(right.x) + Math.abs(up.x), Math.abs(right.y) + Math.abs(up.y),
                    Math.abs(right.z) + Math.abs(up.z)).scale(HALF_WIDTH);
            return new AABB(center.subtract(extent), center.add(extent));
        }
    }
}
