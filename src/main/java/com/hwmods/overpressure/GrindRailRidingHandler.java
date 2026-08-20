package com.hwmods.overpressure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class GrindRailRidingHandler {
    // Keep the rail above the raised hand/wrench instead of clipping it through
    // the player's head. The old 2/16 offset only cleared the top of the skin.
    private static final double RIDER_DROP = 10.0 / 16.0;
    private static final double HOOK_HEIGHT = 1.9 + RIDER_DROP;
    private static final double HANG_DISTANCE = 1.82 + RIDER_DROP;
    private static final double ATTACH_DISTANCE = 0.9;
    private static final double KEEP_DISTANCE = 1.45;
    private static final double MIN_SPEED = 0.16;
    private static final double MAX_SPEED = 0.58;
    private static final double ACCELERATION = 0.012;
    private static final int INITIAL_SPARK_DELAY = 8;
    private static final int MIN_SPARK_INTERVAL = 1;
    private static final int SPARK_INTERVAL_VARIATION = 2;
    private static final double SPARK_HEIGHT_OFFSET = 1.0 / 16.0;
    private static final Map<UUID, RideState> SERVER_RIDERS = new HashMap<>();
    private static final Map<UUID, RideState> CLIENT_RIDERS = new HashMap<>();

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Map<UUID, RideState> riders = player.level().isClientSide ? CLIENT_RIDERS : SERVER_RIDERS;
        UUID playerId = player.getUUID();
        RideState state = riders.get(playerId);

        if (!canHoldRail(player) || player.isShiftKeyDown()) {
            riders.remove(playerId);
            return;
        }

        Vec3 hookPosition = player.position().add(0.0, HOOK_HEIGHT, 0.0);
        RailContact contact = findNearestRail(player, hookPosition);
        double allowedDistance = state == null ? ATTACH_DISTANCE : KEEP_DISTANCE;
        if (contact == null || contact.distance() > allowedDistance) {
            riders.remove(playerId);
            return;
        }

        Vec3 tangent = contact.tangent();
        if (tangent.lengthSqr() < 1.0E-8) {
            riders.remove(playerId);
            return;
        }

        if (state == null) {
            Vec3 look = player.getLookAngle();
            Vec3 currentMotion = player.getDeltaMovement();
            double lookAlignment = look.dot(tangent);
            double motionAlignment = currentMotion.dot(tangent);
            double preferredAlignment = Math.abs(lookAlignment) >= 0.1
                    ? lookAlignment
                    : motionAlignment;
            if (preferredAlignment < 0.0) {
                tangent = tangent.scale(-1.0);
            }
            double initialSpeed = Math.abs(currentMotion.dot(tangent));
            state = new RideState(
                    Math.max(MIN_SPEED, initialSpeed),
                    tangent,
                    contact.point(),
                    contact.point(),
                    INITIAL_SPARK_DELAY
            );
            riders.put(playerId, state);
        } else if (state.direction().dot(tangent) < 0.0) {
            tangent = tangent.scale(-1.0);
        }

        double speed = Math.min(MAX_SPEED, state.speed() + ACCELERATION);
        double parameterDirection = tangent.dot(contact.tangent()) < 0.0 ? -1.0 : 1.0;
        RailAdvance advance = advanceAlongRail(contact.rail(), contact.t(), parameterDirection, speed);
        Vec3 railMotion = advance.point().subtract(contact.point());
        Vec3 desiredPlayerPosition = contact.point().subtract(0.0, HANG_DISTANCE, 0.0);
        Vec3 correction = desiredPlayerPosition.subtract(player.position()).scale(0.34);
        if (correction.lengthSqr() > 0.09) {
            correction = correction.normalize().scale(0.3);
        }

        Vec3 motion = railMotion.add(correction);
        player.setDeltaMovement(motion);
        player.fallDistance = 0.0f;
        player.setOnGround(false);
        player.hurtMarked = true;
        int sparkCooldown = state.sparkCooldown() - 1;
        if (player.level().isClientSide && sparkCooldown <= 0) {
            spawnSparks(player, contact.point(), advance.direction(), speed);
            sparkCooldown = MIN_SPARK_INTERVAL + player.getRandom().nextInt(SPARK_INTERVAL_VARIATION);
        }

        // Rendering must use the rail point above the player's current tick
        // position. advance.point() is the destination for the next tick and
        // made the locked wrench appear up to one movement step ahead.
        riders.put(playerId, new RideState(
                speed,
                advance.direction(),
                contact.point(),
                state.railPoint(),
                sparkCooldown
        ));
    }

    private static void spawnSparks(Player player, Vec3 railPoint, Vec3 direction, double speed) {
        RandomSource random = player.getRandom();
        Vec3 travelDirection = direction.lengthSqr() < 1.0E-8 ? Vec3.ZERO : direction.normalize();
        Vec3 sideways = new Vec3(-travelDirection.z, 0.0, travelDirection.x);
        int particleCount = 4;
        if (speed > 0.38) {
            particleCount += 1 + random.nextInt(2);
        }
        for (int index = 0; index < particleCount; index++) {
            double sideVelocity = (random.nextDouble() - 0.5) * 0.11;
            double backwardVelocity = -(0.025 + speed * (0.055 + random.nextDouble() * 0.035));
            double sideOffset = (random.nextDouble() - 0.5) * 0.09;
            Vec3 velocity = travelDirection.scale(backwardVelocity)
                    .add(sideways.scale(sideVelocity))
                    .add(0.0, -0.008 - random.nextDouble() * 0.03, 0.0);

            player.level().addParticle(
                    ModParticleTypes.WHITE_SPLASH.get(),
                    railPoint.x + sideways.x * sideOffset,
                    railPoint.y + 0.03 + SPARK_HEIGHT_OFFSET + random.nextDouble() * 0.035,
                    railPoint.z + sideways.z * sideOffset,
                    velocity.x,
                    velocity.y,
                    velocity.z
            );
        }
    }

    private static boolean canHoldRail(Player player) {
        if (player.isSpectator() || player.getAbilities().flying) {
            return false;
        }
        return AllItems.WRENCH.isIn(player.getMainHandItem())
                || AllItems.WRENCH.isIn(player.getOffhandItem());
    }

    public static boolean isClientRiding(Player player) {
        return CLIENT_RIDERS.containsKey(player.getUUID());
    }

    static double maxSpeed() {
        return MAX_SPEED;
    }

    static double clientSpeed(Player player) {
        RideState state = CLIENT_RIDERS.get(player.getUUID());
        return state == null ? 0.0 : state.speed();
    }

    @Nullable
    public static Vec3 clientRailPoint(Player player, float partialTick) {
        RideState state = CLIENT_RIDERS.get(player.getUUID());
        return state == null ? null : state.previousRailPoint().lerp(state.railPoint(), partialTick);
    }

    @Nullable
    public static Vec3 clientRailDirection(Player player) {
        RideState state = CLIENT_RIDERS.get(player.getUUID());
        return state == null ? null : state.direction();
    }

    private static RailContact findNearestRail(Player player, Vec3 hookPosition) {
        BlockPos center = BlockPos.containing(hookPosition);
        RailContact nearest = null;
        Set<UUID> checkedSections = new HashSet<>();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (!(player.level().getBlockEntity(pos) instanceof GrindRailBlockEntity rail)
                    || !rail.shouldRenderCurve()
                    || !checkedSections.add(rail.getSectionId())) {
                continue;
            }

            RailContact contact = projectOntoRail(rail, hookPosition);
            if (nearest == null || contact.distance() < nearest.distance()) {
                nearest = contact;
            }
        }
        return nearest;
    }

    private static RailContact projectOntoRail(GrindRailBlockEntity rail, Vec3 target) {
        double controlLength = getControlLength(rail);
        int samples = Math.max(32, Math.min(512, (int) Math.ceil(controlLength * 8.0)));
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double distance = rail.getWorldPoint(t).distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }

        double low = Math.max(0.0, (bestIndex - 1) / (double) samples);
        double high = Math.min(1.0, (bestIndex + 1) / (double) samples);
        for (int iteration = 0; iteration < 10; iteration++) {
            double left = low + (high - low) / 3.0;
            double right = high - (high - low) / 3.0;
            if (rail.getWorldPoint(left).distanceToSqr(target)
                    <= rail.getWorldPoint(right).distanceToSqr(target)) {
                high = right;
            } else {
                low = left;
            }
        }

        double t = (low + high) * 0.5;
        Vec3 point = rail.getWorldPoint(t);
        return new RailContact(rail, t, point, rail.getWorldTangent(t), Math.sqrt(point.distanceToSqr(target)));
    }

    private static RailAdvance advanceAlongRail(
            GrindRailBlockEntity rail,
            double startT,
            double direction,
            double distance
    ) {
        double controlLength = getControlLength(rail);
        int subdivisions = Math.max(64, Math.min(2048, (int) Math.ceil(controlLength * 16.0)));
        double parameterStep = direction / subdivisions;
        double currentT = startT;
        Vec3 currentPoint = rail.getWorldPoint(currentT);
        double remaining = distance;

        for (int step = 0; step < subdivisions && remaining > 1.0E-6; step++) {
            double nextT = Math.max(0.0, Math.min(1.0, currentT + parameterStep));
            if (nextT == currentT) {
                Vec3 endpointDirection = rail.getWorldTangent(currentT).scale(direction);
                if (endpointDirection.lengthSqr() < 1.0E-8) {
                    endpointDirection = Vec3.ZERO;
                }
                return new RailAdvance(
                        currentPoint.add(endpointDirection.scale(remaining)),
                        endpointDirection,
                        currentT
                );
            }

            Vec3 nextPoint = rail.getWorldPoint(nextT);
            double segmentLength = currentPoint.distanceTo(nextPoint);
            if (segmentLength >= remaining && segmentLength > 1.0E-8) {
                double fraction = remaining / segmentLength;
                double resultT = currentT + (nextT - currentT) * fraction;
                Vec3 resultPoint = currentPoint.add(nextPoint.subtract(currentPoint).scale(fraction));
                Vec3 resultDirection = rail.getWorldTangent(resultT).scale(direction);
                return new RailAdvance(resultPoint, resultDirection, resultT);
            }

            remaining -= segmentLength;
            currentT = nextT;
            currentPoint = nextPoint;
        }

        Vec3 resultDirection = rail.getWorldTangent(currentT).scale(direction);
        return new RailAdvance(currentPoint, resultDirection, currentT);
    }

    private static double getControlLength(GrindRailBlockEntity rail) {
        return rail.getWorldP0().distanceTo(rail.getWorldP1())
                + rail.getWorldP1().distanceTo(rail.getWorldP2())
                + rail.getWorldP2().distanceTo(rail.getWorldP3());
    }

    private record RideState(
            double speed,
            Vec3 direction,
            Vec3 railPoint,
            Vec3 previousRailPoint,
            int sparkCooldown
    ) {
    }

    private record RailContact(
            GrindRailBlockEntity rail,
            double t,
            Vec3 point,
            Vec3 tangent,
            double distance
    ) {
    }

    private record RailAdvance(Vec3 point, Vec3 direction, double t) {
    }

    private GrindRailRidingHandler() {
    }
}
