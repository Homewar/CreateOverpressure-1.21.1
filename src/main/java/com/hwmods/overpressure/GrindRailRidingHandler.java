package com.hwmods.overpressure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Overpressure.MODID)
public final class GrindRailRidingHandler {
    private static final double HOOK_HEIGHT = 1.9;
    private static final double HANG_DISTANCE = 1.82;
    private static final double ATTACH_DISTANCE = 0.9;
    private static final double KEEP_DISTANCE = 1.45;
    private static final double MIN_SPEED = 0.16;
    private static final double MAX_SPEED = 0.58;
    private static final double ACCELERATION = 0.012;
    private static final Map<UUID, RideState> SERVER_RIDERS = new HashMap<>();
    private static final Map<UUID, RideState> CLIENT_RIDERS = new HashMap<>();

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Map<UUID, RideState> riders = player.level().isClientSide ? CLIENT_RIDERS : SERVER_RIDERS;
        UUID playerId = player.getUUID();
        RideState state = riders.get(playerId);

        if (!canRide(player)) {
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
            state = new RideState(Math.max(MIN_SPEED, initialSpeed), tangent);
            riders.put(playerId, state);
        } else if (state.direction().dot(tangent) < 0.0) {
            tangent = tangent.scale(-1.0);
        }

        double speed = Math.min(MAX_SPEED, state.speed() + ACCELERATION);
        Vec3 desiredPlayerPosition = contact.point().subtract(0.0, HANG_DISTANCE, 0.0);
        Vec3 correction = desiredPlayerPosition.subtract(player.position()).scale(0.34);
        if (correction.lengthSqr() > 0.09) {
            correction = correction.normalize().scale(0.3);
        }

        Vec3 motion = tangent.scale(speed).add(correction);
        player.setDeltaMovement(motion);
        player.fallDistance = 0.0f;
        player.setOnGround(false);
        player.hurtMarked = true;
        riders.put(playerId, new RideState(speed, tangent));
    }

    private static boolean canRide(Player player) {
        if (player.isSpectator() || player.getAbilities().flying || player.isShiftKeyDown()) {
            return false;
        }
        return AllItems.WRENCH.isIn(player.getMainHandItem())
                || AllItems.WRENCH.isIn(player.getOffhandItem());
    }

    private static RailContact findNearestRail(Player player, Vec3 hookPosition) {
        BlockPos center = BlockPos.containing(hookPosition);
        RailContact nearest = null;
        Set<UUID> checkedSections = new HashSet<>();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (!(player.level().getBlockEntity(pos) instanceof GrindRailBlockEntity rail)
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
        int samples = 32;
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
        return new RailContact(point, rail.getWorldTangent(t), Math.sqrt(point.distanceToSqr(target)));
    }

    private record RideState(double speed, Vec3 direction) {
    }

    private record RailContact(Vec3 point, Vec3 tangent, double distance) {
    }

    private GrindRailRidingHandler() {
    }
}
