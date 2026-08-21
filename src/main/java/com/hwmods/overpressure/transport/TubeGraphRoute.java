package com.hwmods.overpressure.transport;

import java.util.ArrayList;
import java.util.List;

import com.hwmods.overpressure.PneumaticTubeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Immutable graph view of a selected route. Passive tube runs are edges;
 * devices and endpoints are nodes. The physical cells are retained on edges
 * so the transport engine can preserve the old one-item-per-block capacity.
 */
public record TubeGraphRoute(List<Step> steps) {
    public TubeGraphRoute {
        steps = List.copyOf(steps);
    }

    public static TubeGraphRoute compile(
            Level level,
            List<BlockPos> blockPath,
            BlockPos sourceConnector,
            BlockPos targetConnector
    ) {
        List<Step> steps = new ArrayList<>();
        if (sourceConnector != null) {
            steps.add(new NodeStep(sourceConnector.immutable(), endpointNodeKind(level, sourceConnector)));
        }

        List<BlockPos> edgeCells = new ArrayList<>();
        for (BlockPos pos : blockPath) {
            NodeKind nodeKind = nodeKind(level, pos);
            if (nodeKind == null) {
                edgeCells.add(pos.immutable());
                continue;
            }

            flushEdge(steps, edgeCells);
            steps.add(new NodeStep(pos.immutable(), nodeKind));
        }
        flushEdge(steps, edgeCells);

        if (targetConnector != null) {
            NodeKind kind = level.getBlockEntity(targetConnector) instanceof TransportEndpoint
                    ? endpointNodeKind(level, targetConnector)
                    : NodeKind.OPEN_END;
            steps.add(new NodeStep(targetConnector.immutable(), kind));
        }
        return new TubeGraphRoute(steps);
    }

    private static NodeKind endpointNodeKind(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof TransportNodeComponent node
                ? node.graphNodeKind()
                : NodeKind.CONNECTOR;
    }

    private static void flushEdge(List<Step> steps, List<BlockPos> edgeCells) {
        if (edgeCells.isEmpty()) {
            return;
        }
        steps.add(new EdgeStep(List.copyOf(edgeCells)));
        edgeCells.clear();
    }

    private static NodeKind nodeKind(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TransportNodeComponent node) {
            return node.graphNodeKind();
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof PneumaticTubeBlock) {
            return null;
        }
        return NodeKind.VIRTUAL;
    }

    public sealed interface Step permits NodeStep, EdgeStep {
    }

    public record NodeStep(BlockPos pos, NodeKind kind) implements Step {
    }

    public record EdgeStep(List<BlockPos> cells) implements Step {
        public EdgeStep {
            cells = List.copyOf(cells);
        }
    }

    public enum NodeKind {
        CONNECTOR,
        CAPSULE_PORT,
        PUMP,
        JUNCTION,
        VALVE,
        SENSOR,
        OPEN_END,
        VIRTUAL
    }
}
