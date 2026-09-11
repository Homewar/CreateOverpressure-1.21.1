package com.hwmods.overpressure.tube;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hwmods.overpressure.math.CubicBezier;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** A complete tube section in world coordinates. It owns no block positions. */
public final class TubeSection {
    private final UUID id;
    private final List<Span> spans;
    private final int materialCost;
    private final TubeSectionGeometry geometry;

    public TubeSection(UUID id, List<Span> spans, int materialCost) {
        if (spans.isEmpty() || spans.size() > 512 || materialCost < 1) {
            throw new IllegalArgumentException("Invalid tube section size");
        }
        this.id = java.util.Objects.requireNonNull(id);
        this.spans = List.copyOf(spans);
        this.materialCost = materialCost;
        for (int i = 1; i < spans.size(); i++) {
            if (spans.get(i - 1).curve().p3().distanceToSqr(spans.get(i).curve().p0()) > 1.0E-8) {
                throw new IllegalArgumentException("Disconnected tube section spans");
            }
        }
        geometry = new TubeSectionGeometry(this.spans);
    }

    public UUID id() { return id; }
    public List<Span> spans() { return spans; }
    public int materialCost() { return materialCost; }
    public TubeSectionGeometry geometry() { return geometry; }

    public Port port(boolean start) {
        CubicBezier curve = spans.get(start ? 0 : spans.size() - 1).curve();
        Vec3 outward = curve.derivativeAt(start ? 0 : 1).normalize().scale(start ? -1 : 1);
        return new Port(id, start, start ? curve.p0() : curve.p3(), outward);
    }

    public TubeSection withAppearance(int color, boolean glowing) {
        return new TubeSection(id, spans.stream().map(span -> new Span(span.curve(), color, glowing)).toList(), materialCost);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("MaterialCost", materialCost);
        ListTag entries = new ListTag();
        for (Span span : spans) {
            CompoundTag entry = new CompoundTag();
            writePoint(entry, "P0", span.curve().p0());
            writePoint(entry, "P1", span.curve().p1());
            writePoint(entry, "P2", span.curve().p2());
            writePoint(entry, "P3", span.curve().p3());
            entry.putInt("Color", span.color());
            entry.putBoolean("Glowing", span.glowing());
            entries.add(entry);
        }
        tag.put("Spans", entries);
        return tag;
    }

    public static TubeSection load(CompoundTag tag) {
        ListTag entries = tag.getList("Spans", Tag.TAG_COMPOUND);
        List<Span> spans = new ArrayList<>();
        if (entries.size() > 512) throw new IllegalArgumentException("Too many tube spans");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            spans.add(new Span(new CubicBezier(readPoint(entry, "P0"), readPoint(entry, "P1"),
                    readPoint(entry, "P2"), readPoint(entry, "P3")), entry.getInt("Color"), entry.getBoolean("Glowing")));
        }
        return new TubeSection(tag.getUUID("Id"), spans, tag.getInt("MaterialCost"));
    }

    private static void writePoint(CompoundTag tag, String key, Vec3 point) {
        CompoundTag value = new CompoundTag();
        value.putDouble("X", point.x);
        value.putDouble("Y", point.y);
        value.putDouble("Z", point.z);
        tag.put(key, value);
    }

    private static Vec3 readPoint(CompoundTag tag, String key) {
        CompoundTag value = tag.getCompound(key);
        return new Vec3(value.getDouble("X"), value.getDouble("Y"), value.getDouble("Z"));
    }

    public record Span(CubicBezier curve, int color, boolean glowing) {
        public Span {
            for (Vec3 p : List.of(curve.p0(), curve.p1(), curve.p2(), curve.p3())) {
                if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) {
                    throw new IllegalArgumentException("Non-finite tube geometry");
                }
            }
            if (curve.derivativeAt(0).lengthSqr() < 1.0E-12 || curve.derivativeAt(1).lengthSqr() < 1.0E-12) {
                throw new IllegalArgumentException("Tube endpoints need a tangent");
            }
            color &= 0xFFFFFF;
        }
    }

    /** Ports belong to the section, even when nothing is attached to them. */
    public record Port(UUID section, boolean start, Vec3 position, Vec3 outward) {
        public Direction direction() { return Direction.getNearest(outward.x, outward.y, outward.z); }
        public boolean connectsTo(Port other) {
            return !section.equals(other.section) && position.distanceToSqr(other.position) < 1.0E-8
                    && outward.dot(other.outward) < -0.999;
        }
    }
}
