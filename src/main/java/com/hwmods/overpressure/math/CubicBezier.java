package com.hwmods.overpressure.math;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable cubic Bézier curve in world space.
 */
public record CubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
    public Vec3 pointAt(double t) {
        double u = 1.0 - t;
        double uu = u * u;
        double tt = t * t;

        return p0.scale(uu * u)
                .add(p1.scale(3.0 * uu * t))
                .add(p2.scale(3.0 * u * tt))
                .add(p3.scale(tt * t));
    }

    public Vec3 derivativeAt(double t) {
        double u = 1.0 - t;
        return p1.subtract(p0).scale(3.0 * u * u)
                .add(p2.subtract(p1).scale(6.0 * u * t))
                .add(p3.subtract(p2).scale(3.0 * t * t));
    }

    public Vec3 secondDerivativeAt(double t) {
        double u = 1.0 - t;
        return p2.subtract(p1.scale(2.0)).add(p0).scale(6.0 * u)
                .add(p3.subtract(p2.scale(2.0)).add(p1).scale(6.0 * t));
    }

    public CubicBezier offset(Vec3 offset) {
        return new CubicBezier(
                p0.subtract(offset),
                p1.subtract(offset),
                p2.subtract(offset),
                p3.subtract(offset)
        );
    }

    /**
     * Converts a curve drawn between block faces into one sampled through block
     * centers while retaining its endpoint tangents.
     */
    public CubicBezier toBlockCenterCurve() {
        Vec3 startInset = normalizedToLength(p1.subtract(p0), 0.5);
        Vec3 endInset = normalizedToLength(p3.subtract(p2), 0.5);

        return new CubicBezier(
                p0.add(startInset),
                p1.add(startInset),
                p2.subtract(endInset),
                p3.subtract(endInset)
        );
    }

    public List<CubicBezier> splitEqually(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Curve segment count must be positive");
        }

        List<CubicBezier> segments = new ArrayList<>(count);
        CubicBezier remaining = this;

        for (int i = 0; i < count - 1; i++) {
            Split split = remaining.splitAt(1.0 / (count - i));
            segments.add(split.left());
            remaining = split.right();
        }

        segments.add(remaining);
        return segments;
    }

    public boolean satisfiesCurvatureLimits(
            int samples,
            double minimumRadius,
            double maximumSecondDerivative
    ) {
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 derivative = derivativeAt(t);
            Vec3 secondDerivative = secondDerivativeAt(t);
            double speedSquared = derivative.lengthSqr();

            if (secondDerivative.length() > maximumSecondDerivative || speedSquared <= 1.0E-8) {
                return false;
            }

            double curvature = derivative.cross(secondDerivative).length() / Math.pow(speedSquared, 1.5);
            if (curvature > 0.0 && 1.0 / curvature < minimumRadius) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks the accumulated change of the tangent along this curve segment.
     * This catches turns concentrated in the middle of a segment as well as
     * excessive differences between its endpoint tangents.
     */
    public boolean satisfiesTurnAngleLimit(int samples, double maximumAngleDegrees) {
        if (samples < 1) {
            throw new IllegalArgumentException("Turn angle sample count must be positive");
        }

        Vec3 previousTangent = derivativeAt(0.0);
        if (previousTangent.lengthSqr() <= 1.0E-8) {
            return false;
        }
        previousTangent = previousTangent.normalize();

        double accumulatedAngle = 0.0;
        double maximumAngle = Math.toRadians(maximumAngleDegrees);
        for (int i = 1; i <= samples; i++) {
            Vec3 tangent = derivativeAt((double) i / samples);
            if (tangent.lengthSqr() <= 1.0E-8) {
                return false;
            }
            tangent = tangent.normalize();

            double dot = Math.max(-1.0, Math.min(1.0, previousTangent.dot(tangent)));
            accumulatedAngle += Math.acos(dot);
            if (accumulatedAngle > maximumAngle + 1.0E-6) {
                return false;
            }
            previousTangent = tangent;
        }

        return true;
    }

    private Split splitAt(double t) {
        Vec3 p01 = p0.lerp(p1, t);
        Vec3 p12 = p1.lerp(p2, t);
        Vec3 p23 = p2.lerp(p3, t);
        Vec3 p012 = p01.lerp(p12, t);
        Vec3 p123 = p12.lerp(p23, t);
        Vec3 midpoint = p012.lerp(p123, t);

        return new Split(
                new CubicBezier(p0, p01, p012, midpoint),
                new CubicBezier(midpoint, p123, p23, p3)
        );
    }

    private static Vec3 normalizedToLength(Vec3 vector, double length) {
        return vector.lengthSqr() > 1.0E-6 ? vector.normalize().scale(length) : Vec3.ZERO;
    }

    private record Split(CubicBezier left, CubicBezier right) {
    }
}
