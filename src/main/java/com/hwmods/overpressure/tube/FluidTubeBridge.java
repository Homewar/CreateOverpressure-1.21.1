package com.hwmods.overpressure.tube;

import java.util.*;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A virtual endpoint for Create's pump-driven fluid networks. Stores no fluid or hidden blocks. */
public final class FluidTubeBridge {
    public static boolean canConnect(Level level, BlockPos pos, Direction face) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof PumpBlock) return state.getValue(PumpBlock.FACING).getAxis() == face.getAxis();
        var pipe = FluidPropagator.getPipe(level, pos);
        return pipe != null || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face) != null;
    }
    private static List<TubeSection.Port> ports(Level level, BlockPos pos, Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        return TubeSections.portsAt(level, Vec3.atCenterOf(pos).add(normal.scale(0.5)), normal.scale(-1), true);
    }
    public static IFluidHandler at(Level level, BlockPos pos, Direction face) {
        if (face == null || !level.isLoaded(pos) || ports(level, pos, face).isEmpty()) return null;
        return new Bridge(level, pos.immutable(), face);
    }
    public static void changed(Level level, TubeSection section) {
        // Sections are also mirrored to the client for rendering. Create's pipe
        // propagation is server-side state management and must never run while a
        // sync packet is being applied on the client.
        if (level.isClientSide) return;
        for (boolean start : new boolean[]{true, false}) {
            var port = section.port(start);
            BlockPos node = BlockPos.containing(port.position().add(port.outward().scale(0.05)));
            if (!level.isLoaded(node)) continue;
            var state = level.getBlockState(node);
            if (state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock pipe)
                level.setBlock(node, pipe.updateBlockState(state, port.direction().getOpposite(), null, level, node), 3);
            level.updateNeighborsAt(node.relative(port.direction().getOpposite()), com.simibubi.create.AllBlocks.FLUID_PIPE.get());
            FluidPropagator.propagateChangedPipe(level, node, level.getBlockState(node));
            if (level.getBlockEntity(node) instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity pump)
                pump.updatePipesOnSide(port.direction().getOpposite());
        }
    }
    private record Step(TubeSection.Port port, int distance, List<Gate> gates, List<UUID> sections,
                        Map<BlockPos, EnumSet<Direction>> pipes) {}
    private record PipeStep(BlockPos pos, Direction entry, int distance, List<Gate> gates, List<UUID> sections,
                            Map<BlockPos, EnumSet<Direction>> pipes) {}
    private record Gate(FluidTransportBehaviour pipe, Direction face) {
        boolean allows(FluidStack fluid) { return pipe.canPullFluidFrom(fluid, pipe.blockEntity.getBlockState(), face); }
    }
    private record Sink(IFluidHandler handler, List<Gate> gates, List<UUID> sections,
                        Map<BlockPos, EnumSet<Direction>> pipes) {
        boolean allows(FluidStack fluid) { return gates.stream().allMatch(g -> g.allows(fluid)); }
        void show(Level level, FluidStack fluid) {
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                FluidTubeFlowPayload.send(server, fluid, sections, pipes);
        }
    }
    private static List<Sink> targets(Level level, BlockPos origin, Direction face, boolean draining) {
        List<Sink> result = new ArrayList<>();
        Set<UUID> sections = new HashSet<>(); Set<BlockPos> visited = new HashSet<>();
        visited.add(origin.relative(face)); // Never route back through the calling pump/network.
        ArrayDeque<PipeStep> blocks = new ArrayDeque<>();
        ArrayDeque<Step> curves = new ArrayDeque<>();
        for (var port : ports(level, origin, face)) curves.add(new Step(port, 0, List.of(), List.of(), Map.of()));
        int range = FluidPropagator.getPumpRange();
        while (!curves.isEmpty() || !blocks.isEmpty()) {
            while (!curves.isEmpty()) {
                Step step = curves.remove(); var section = TubeSections.get(level).get(step.port.section());
                if (section == null || !section.isFluid() || !sections.add(section.id())) continue;
                int distance = step.distance + (int) Math.ceil(section.geometry().length());
                if (distance > range) continue;
                var end = section.port(!step.port.start());
                if (section.geometry().frames().stream().anyMatch(f -> !level.isLoaded(BlockPos.containing(f.center())))) continue;
                List<UUID> pathSections = new ArrayList<>(step.sections); pathSections.add(section.id());
                for (var next : TubeSections.portsAt(level, end.position(), end.outward(), true))
                    curves.add(new Step(next, distance, step.gates, pathSections, step.pipes));
                blocks.add(new PipeStep(BlockPos.containing(end.position().add(end.outward().scale(0.05))),
                        end.direction().getOpposite(), distance, step.gates, pathSections, step.pipes));
            }
            if (blocks.isEmpty()) continue;
            PipeStep step = blocks.remove(); BlockPos pos = step.pos;
            if (step.distance > range || !level.isLoaded(pos) || !visited.add(pos)) continue;
            var state = level.getBlockState(pos);
            int distance = step.distance;
            if (state.getBlock() instanceof PumpBlock) {
                if (!(level.getBlockEntity(pos) instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity pump)
                        || pump.getSpeed() == 0 || step.entry != (draining ? state.getValue(PumpBlock.FACING) : state.getValue(PumpBlock.FACING).getOpposite())) continue;
                distance = 0; // A correctly oriented running pump renews the pressure range.
            }
            var pipe = FluidPropagator.getPipe(level, pos);
            if (pipe == null) {
                var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, step.entry);
                if (handler != null) result.add(new Sink(handler, step.gates, step.sections, step.pipes));
                continue;
            }
            if (!pipe.canHaveFlowToward(state, step.entry)) continue;
            for (Direction output : Direction.values()) {
                if (output == step.entry || !pipe.canHaveFlowToward(state, output)) continue;
                List<Gate> gates = new ArrayList<>(step.gates);
                gates.add(new Gate(pipe, draining ? output : step.entry));
                Map<BlockPos, EnumSet<Direction>> pathPipes = new HashMap<>();
                step.pipes.forEach((p, faces) -> pathPipes.put(p, EnumSet.copyOf(faces)));
                pathPipes.computeIfAbsent(pos, ignored -> EnumSet.noneOf(Direction.class)).addAll(List.of(step.entry, output));
                Vec3 normal = Vec3.atLowerCornerOf(output.getNormal());
                for (var port : TubeSections.portsAt(level, Vec3.atCenterOf(pos).add(normal.scale(0.5)), normal, true))
                    curves.add(new Step(port, distance + 1, gates, step.sections, pathPipes));
                blocks.add(new PipeStep(pos.relative(output), output.getOpposite(), distance + 1, gates,
                        step.sections, pathPipes));
            }
        }
        return result;
    }
    private record Bridge(Level level, BlockPos pos, Direction face) implements IFluidHandler {
        private List<Sink> sinks(boolean drain) { return targets(level, pos, face, drain); }
        public int getTanks() { return sinks(true).stream().mapToInt(s -> s.handler.getTanks()).sum(); }
        public FluidStack getFluidInTank(int tank) {
            for (Sink s : sinks(true)) { if (tank < s.handler.getTanks()) { var f = s.handler.getFluidInTank(tank); return s.allows(f) ? f : FluidStack.EMPTY; } tank -= s.handler.getTanks(); }
            return FluidStack.EMPTY;
        }
        public int getTankCapacity(int tank) {
            for (Sink s : sinks(true)) { if (tank < s.handler.getTanks()) return s.handler.getTankCapacity(tank); tank -= s.handler.getTanks(); } return 0;
        }
        public boolean isFluidValid(int tank, FluidStack fluid) {
            for (Sink s : sinks(false)) { if (tank < s.handler.getTanks()) return s.allows(fluid) && s.handler.isFluidValid(tank, fluid); tank -= s.handler.getTanks(); } return false;
        }
        public int fill(FluidStack fluid, FluidAction action) {
            if (fluid.isEmpty()) return 0;
            int amount = 0;
            for (Sink s : sinks(false)) if (s.allows(fluid)) {
                int filled = s.handler.fill(fluid.copyWithAmount(fluid.getAmount() - amount), action);
                if (filled > 0 && action.execute()) s.show(level, fluid.copyWithAmount(filled));
                amount += filled;
                if (amount >= fluid.getAmount()) break;
            }
            return amount;
        }
        public FluidStack drain(FluidStack fluid, FluidAction action) {
            if (fluid.isEmpty()) return FluidStack.EMPTY;
            int amount = 0;
            for (Sink s : sinks(true)) if (s.allows(fluid)) {
                FluidStack drained = s.handler.drain(fluid.copyWithAmount(fluid.getAmount() - amount), action);
                if (!drained.isEmpty() && action.execute()) s.show(level, drained);
                amount += drained.getAmount();
                if (amount >= fluid.getAmount()) break;
            }
            return fluid.copyWithAmount(amount);
        }
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) return FluidStack.EMPTY;
            for (Sink s : sinks(true)) {
                FluidStack candidate = s.handler.drain(maxDrain, FluidAction.SIMULATE);
                if (!candidate.isEmpty() && s.allows(candidate)) return drain(candidate, action);
            }
            return FluidStack.EMPTY;
        }
    }
}
