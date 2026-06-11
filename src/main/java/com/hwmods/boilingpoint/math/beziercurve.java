package com.hwmods.boilingpoint.math;

import org.joml.Vector2f;

public class beziercurve {
    private final Vector2f p1;
    private final Vector2f p2;
    private final Vector2f p3;
    private final Vector2f p4;

    public beziercurve(Vector2f p1, Vector2f p2, Vector2f p3, Vector2f p4) {
        this.p1 = new Vector2f(p1);
        this.p2 = new Vector2f(p2);
        this.p3 = new Vector2f(p3);
        this.p4 = new Vector2f(p4);
    }

    public Vector2f getP1() {
        return new Vector2f(p1);
    }

    public Vector2f getP2() {
        return new Vector2f(p2);
    }

    public Vector2f getP3() {
        return new Vector2f(p3);
    }

    public Vector2f getP4() {
        return new Vector2f(p4);
    }

    public Vector2f getPoint(float t) {
        float u = 1.0f - t;

        Vector2f term1 = new Vector2f(p1).mul(u * u * u);
        Vector2f term2 = new Vector2f(p2).mul(3.0f * u * u * t);
        Vector2f term3 = new Vector2f(p3).mul(3.0f * u * t * t);
        Vector2f term4 = new Vector2f(p4).mul(t * t * t);

        return term1.add(term2).add(term3).add(term4);
    }

    public Vector2f getDerivative(float t) {
        float u = 1.0f - t;

        Vector2f term1 = new Vector2f(p2).sub(p1).mul(3.0f * u * u);
        Vector2f term2 = new Vector2f(p3).sub(p2).mul(6.0f * u * t);
        Vector2f term3 = new Vector2f(p4).sub(p3).mul(3.0f * t * t);

        return term1.add(term2).add(term3);
    }

    public Vector2f getSecondDerivative(float t) {
        float u = 1.0f - t;

        Vector2f firstPart = new Vector2f(p3)
                .sub(new Vector2f(p2).mul(2.0f))
                .add(p1)
                .mul(6.0f * u);

        Vector2f secondPart = new Vector2f(p4)
                .sub(new Vector2f(p3).mul(2.0f))
                .add(p2)
                .mul(6.0f * t);

        return firstPart.add(secondPart);
    }

    public float getCurvature(float t) {
        Vector2f derivative = getDerivative(t);
        Vector2f secondDerivative = getSecondDerivative(t);

        float cross = derivative.x * secondDerivative.y - derivative.y * secondDerivative.x;
        float speedSquared = derivative.lengthSquared();

        if (speedSquared == 0.0f) {
            return 0.0f;
        }

        return Math.abs(cross) / (float) Math.pow(speedSquared, 1.5f);
    }

    public float getCurvatureRadius(float t) {
        float curvature = getCurvature(t);

        if (curvature == 0.0f) {
            return Float.POSITIVE_INFINITY;
        }

        return 1.0f / curvature;
    }
}
