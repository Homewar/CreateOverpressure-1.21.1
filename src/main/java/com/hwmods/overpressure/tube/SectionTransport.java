package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.*;
import com.hwmods.overpressure.transport.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Cargo on mixed block/section routes. Sections are addressed by UUID, never by fictitious cells. */
@EventBusSubscriber(modid = Overpressure.MODID)
public final class SectionTransport extends SavedData {
    private static final Factory<SectionTransport> FACTORY = new Factory<>(SectionTransport::new, SectionTransport::load, null);
    private final Map<UUID, Cargo> cargo = new LinkedHashMap<>();
    private final Set<BlockPos> notifiedObservers = new HashSet<>();
    public static SectionTransport get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, "overpressure_section_cargo"); }
    public static boolean isOccupied(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && get(server).cargo.values().stream()
                .anyMatch(c -> c.route.get(c.index).location instanceof TransportLocation.Block block && block.pos().equals(pos));
    }
    public record Hop(TransportLocation location, Direction incoming, Vec3 entry, Vec3 exit) {}
    public record Route(List<Hop> hops, BlockPos target) {}
    public static Route find(Level level, BlockPos source, Direction output, ItemStack stack) {
        if (!(level instanceof ServerLevel) || stack.isEmpty() || !Config.canEnterTube(stack) || TubeSections.get(level).sections().isEmpty()) return null;
        var initial = resolve(level, source, output, portPoint(level, source, output), portVector(level, source, output), stack);
        return search(level, initial, stack);
    }
    private record SearchStep(List<Hop> path, int branchPenalty, long order) {}
    private static Route search(Level level, List<Hop> initial, ItemStack stack) {
        return search(level, initial, stack, false);
    }
    private static Route search(Level level, List<Hop> initial, ItemStack stack, boolean alreadyTraversedSection) {
        Queue<SearchStep> queue = new PriorityQueue<>(Comparator.comparingInt(SearchStep::branchPenalty)
                .thenComparingInt(step -> step.path.size()).thenComparingLong(SearchStep::order));
        long order = 0;
        for (Hop hop : initial) queue.add(new SearchStep(List.of(hop), 0, order++));
        Set<String> visited = new HashSet<>();
        Route spill = null, destination = null;
        int destinationPenalty = Integer.MAX_VALUE;
        int examined = 0;
        while (!queue.isEmpty() && examined++ < 8192) {
            SearchStep step = queue.remove();
            if (step.branchPenalty > destinationPenalty) break;
            List<Hop> path = step.path;
            Hop current = path.getLast();
            if (!visited.add(current.location + ":" + current.incoming)) continue;
            boolean containsSection = alreadyTraversedSection || path.stream().anyMatch(h -> h.location instanceof TransportLocation.Section);
            if (current.location instanceof TransportLocation.Section ref) {
                TubeSection section = TubeSections.get(level).get(ref.id());
                if (section == null) continue;
                var end = section.port(!ref.start());
                List<Hop> next = new ArrayList<>();
                for (var port : TubeSections.portsAt(level, end.position(), end.outward())) {
                    if (!port.section().equals(section.id())) next.add(sectionHop(level, port));
                }
                BlockPos adjacent = BlockPos.containing(end.position().add(end.outward().scale(0.05)));
                if (level.isLoaded(adjacent)) {
                    Direction incoming = incomingAt(level, adjacent, end.position(), end.direction());
                    if (receives(level, adjacent, incoming)) {
                        if (destination == null || step.branchPenalty < destinationPenalty
                                || path.size() < destination.hops.size()) {
                            destination = new Route(path, adjacent); destinationPenalty = step.branchPenalty;
                        }
                        continue;
                    }
                    if (allows(level, adjacent, incoming, true, false)) next.add(blockHop(adjacent, incoming, end.position()));
                }
                if (next.isEmpty() && level.getBlockState(adjacent).isAir()) spill = new Route(path, null);
                for (Hop hop : next) enqueue(queue, path, hop, step.branchPenalty, order++);
            } else if (current.location instanceof TransportLocation.Block block) {
                BlockPos pos = block.pos();
                if (!level.isLoaded(pos)) continue;
                var be = level.getBlockEntity(pos);
                List<Direction> directions = new ArrayList<>();
                if (be instanceof TransportJunction junction) {
                    BlockPos previous = pos.relative(current.incoming.getOpposite());
                    for (BlockPos next : junction.forwardPorts(previous, stack)) {
                        directions.add(Direction.getNearest(next.getX() - pos.getX(), next.getY() - pos.getY(), next.getZ() - pos.getZ()));
                    }
                } else {
                    for (Direction direction : Direction.values()) if (direction != current.incoming.getOpposite()
                            && allows(level, pos, direction, false, false)) directions.add(direction);
                    // A straight tube has no connection flag on its open face.
                    // As in the block route finder, cargo may still leave along its axis.
                    if (directions.isEmpty() && be instanceof PneumaticTubeBlockEntity
                            && allows(level, pos, current.incoming, true, false)) {
                        BlockPos forward = pos.relative(current.incoming);
                        if (!PneumaticLine.isPathNode(level, forward)
                                && !(level.getBlockEntity(forward) instanceof TransportEndpoint)
                                && !TubeSections.connects(level, pos, current.incoming)) directions.add(current.incoming);
                    }
                }
                for (Direction direction : directions) {
                    int penalty = step.branchPenalty;
                    if (be instanceof TransportJunction junction && junction.isStraightPort(pos.relative(current.incoming.getOpposite()))) {
                        List<BlockPos> branches = junction.orderedBranchPorts(stack);
                        if (branches.isEmpty() || !branches.getFirst().equals(pos.relative(direction))) penalty++;
                    }
                    Vec3 exit = portPoint(level, pos, direction);
                    List<Hop> adjusted = new ArrayList<>(path);
                    adjusted.set(adjusted.size() - 1, new Hop(current.location, current.incoming, current.entry, exit));
                    BlockPos neighbor = pos.relative(direction);
                    if (receives(level, neighbor, direction) && containsSection) {
                        if (destination == null || penalty < destinationPenalty
                                || penalty == destinationPenalty && adjusted.size() < destination.hops.size()) {
                            destination = new Route(List.copyOf(adjusted), neighbor); destinationPenalty = penalty;
                        }
                        continue;
                    }
                    List<Hop> next = resolve(level, pos, direction, exit, portVector(level, pos, direction), stack);
                    if (next.isEmpty() && containsSection && level.getBlockState(neighbor).isAir()) spill = new Route(List.copyOf(adjusted), null);
                    for (Hop hop : next) enqueue(queue, adjusted, hop, penalty, order++);
                }
            }
        }
        return destination != null ? destination : spill;
    }
    private static void enqueue(Queue<SearchStep> queue, List<Hop> path, Hop next, int penalty, long order) {
        if (path.stream().anyMatch(h -> h.location.equals(next.location))) return;
        List<Hop> extended = new ArrayList<>(path); extended.add(next); queue.add(new SearchStep(List.copyOf(extended), penalty, order));
    }
    private static List<Hop> resolve(Level level, BlockPos from, Direction direction, Vec3 point, Vec3 outward, ItemStack stack) {
        List<Hop> next = new ArrayList<>();
        for (var port : TubeSections.portsAt(level, point, outward)) next.add(sectionHop(level, port));
        BlockPos adjacent = from.relative(direction);
        if (level.isLoaded(adjacent) && allows(level, adjacent, direction, true, false)) next.add(blockHop(adjacent, direction, point));
        return next;
    }
    private static Hop sectionHop(Level level, TubeSection.Port port) {
        var section = TubeSections.get(level).get(port.section());
        return new Hop(new TransportLocation.Section(port.section(), port.start()), port.direction().getOpposite(),
                port.position(), section.port(!port.start()).position());
    }
    private static Hop blockHop(BlockPos pos, Direction incoming, Vec3 entry) {
        return new Hop(new TransportLocation.Block(pos), incoming, entry,
                Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(incoming.getNormal()).scale(0.5)));
    }
    private static Direction incomingAt(Level level, BlockPos pos, Vec3 point, Direction fallback) {
        if (level.getBlockEntity(pos) instanceof TransportJunction) {
            for (Direction face : Direction.values()) if (portPoint(level, pos, face).distanceToSqr(point) < 1.0E-8) return face.getOpposite();
        }
        return fallback;
    }
    public static Vec3 portPoint(Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof TransportJunction junction && junction.isBranchPort(pos.relative(direction))) {
            Direction input = junctionInput(pos, junction);
            if (input.getAxis() != direction.getAxis())
                return Vec3.atLowerCornerOf(pos).add(DeviderBlockEntity.getLocalOutputPoint(direction, input));
        }
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5));
    }
    public static Vec3 portVector(Level level, BlockPos pos, Direction direction) {
        Vec3 vector = Vec3.atLowerCornerOf(direction.getNormal());
        if (level.getBlockEntity(pos) instanceof TransportJunction junction && junction.isBranchPort(pos.relative(direction))) {
            Direction input = junctionInput(pos, junction);
            if (input.getAxis() != direction.getAxis())
                vector = vector.add(Vec3.atLowerCornerOf(input.getOpposite().getNormal())).normalize();
        }
        return vector;
    }
    private static Direction junctionInput(BlockPos pos, TransportJunction junction) {
        // FilterPipeBlock and DeviderBlock have distinct INPUT property instances.
        // Resolve the shared port geometry through the junction contract instead.
        BlockPos input = junction.straightPort();
        return Direction.getNearest(input.getX() - pos.getX(), input.getY() - pos.getY(), input.getZ() - pos.getZ());
    }
    private static boolean allows(Level level, BlockPos pos, Direction travel, boolean entering, boolean runtime) {
        var be = level.getBlockEntity(pos);
        if (be instanceof TransportEndpoint) return false;
        if (!PneumaticLine.isPathNode(level, pos)) return false;
        if (be instanceof TransportGate gate && !(runtime ? gate.allowsTravelNow(level, travel) : gate.allowsRoute(level, travel))) return false;
        Direction face = entering ? travel.getOpposite() : travel;
        if (be instanceof TransportJunction junction) {
            BlockPos port = pos.relative(face);
            return entering ? junction.isStraightPort(port) ? !junction.isMerger() : junction.isBranchInputEnabled(port)
                    : junction.isStraightPort(port) ? junction.isMerger() : junction.isBranchOutputEnabled(port);
        }
        return !(be instanceof PneumaticTubeBlockEntity tube) || tube.canTravelTo(level, face);
    }
    private static boolean receives(Level level, BlockPos pos, Direction direction) {
        if (!level.isLoaded(pos)) return false;
        return level.getBlockEntity(pos) instanceof PneumaticConnectionBlockEntity connector
                && connector.getBlockState().getValue(PneumaticConnectionBlock.MODE) == PneumaticConnectionBlock.ConnectionMode.INSERT
                && connector.allowsRoute(level, direction);
    }
    public boolean canAccept(ServerLevel level, Route route) {
        return route != null && !route.hops.isEmpty() && moveTime(level, route) > 0 && canEnter(level, route.hops.getFirst(), null);
    }
    public boolean accept(ServerLevel level, ItemStack stack, Route route) {
        if (stack.isEmpty() || !canAccept(level, route)) return false;
        Cargo entry = new Cargo(UUID.randomUUID(), stack.copy(), route.hops, route.target);
        for (Hop hop : route.hops) if (hop.location instanceof TransportLocation.Block block
                && level.getBlockEntity(block.pos()) instanceof TransportJunction junction
                && junction.isStraightPort(block.pos().relative(hop.incoming.getOpposite()))) {
            Direction output = incomingAt(level, block.pos(), hop.exit, hop.incoming.getOpposite()).getOpposite();
            junction.markBranchUsed(block.pos().relative(output));
        }
        cargo.put(entry.id, entry); setDirty(); return true;
    }
    public void restoreLegacy(ServerLevel level, TubeSection section, MovingTubeItem snapshot, double offset, boolean start) {
        Route route = null;
        if (snapshot.sourceConnector != null) {
            var state = level.getBlockState(snapshot.sourceConnector);
            Direction facing = state.hasProperty(PneumaticConnectionBlock.FACING) ? state.getValue(PneumaticConnectionBlock.FACING) : Direction.NORTH;
            route = find(level, snapshot.sourceConnector, facing, snapshot.stack);
        }
        int index = -1;
        if (route != null) for (int i = 0; i < route.hops.size(); i++) {
            if (route.hops.get(i).location instanceof TransportLocation.Section s && s.id().equals(section.id())) { index = i; start = s.start(); break; }
        }
        boolean recovering = index < 0;
        if (recovering) { route = new Route(List.of(sectionHop(level, section.port(start))), null); index = 0; }
        Cargo recovered = new Cargo(UUID.randomUUID(), snapshot.stack.copy(), route.hops, route.target);
        recovered.index = index;
        recovered.recovering = recovering;
        recovered.allowSpill = snapshot.spillsAtEnd;
        recovered.controllers = snapshot.path.stream().filter(pos -> level.getBlockEntity(pos) instanceof TransportFlowSource).toList();
        recovered.distance = Math.min(section.geometry().length(), start ? offset : Math.max(0, section.geometry().length() - offset));
        cargo.put(recovered.id, recovered); setDirty();
    }
    private boolean canEnter(ServerLevel level, Hop hop, Cargo self) {
        if (hop.location instanceof TransportLocation.Block block) {
            if (TubeTransportManager.get(level).getSnapshot(block.pos()) != null) return false;
            return cargo.values().stream().noneMatch(c -> c != self && c.route.get(c.index).location.equals(hop.location));
        }
        var section = (TransportLocation.Section) hop.location;
        for (Cargo c : cargo.values()) {
            if (c == self || !(c.route.get(c.index).location instanceof TransportLocation.Section other) || !other.id().equals(section.id())) continue;
            if (other.start() != section.start() || c.distance < 1.0) return false;
        }
        return TubeSections.get(level).get(section.id()) != null;
    }
    private static int moveTime(Level level, Route route) {
        int speed = 0;
        for (Hop hop : route.hops) if (hop.location instanceof TransportLocation.Block block
                && level.getBlockEntity(block.pos()) instanceof TransportFlowSource source && source.isRunning()) {
            speed = speed == 0 ? source.getMoveTime() : Math.min(speed, source.getMoveTime());
        }
        return speed;
    }
    private static double length(Level level, Hop hop) {
        if (hop.location instanceof TransportLocation.Section ref) {
            var section = TubeSections.get(level).get(ref.id());
            return section == null ? 0 : section.geometry().length();
        }
        Vec3 center = Vec3.atCenterOf(((TransportLocation.Block) hop.location).pos());
        return hop.entry.distanceTo(center) + center.distanceTo(hop.exit);
    }
    public static Vec3 position(Level level, Hop hop, double distance) {
        if (hop.location instanceof TransportLocation.Section ref) {
            var section = TubeSections.get(level).get(ref.id());
            if (section == null) return hop.entry;
            return section.geometry().pointAtDistance(ref.start() ? distance : section.geometry().length() - distance);
        }
        Vec3 center = Vec3.atCenterOf(((TransportLocation.Block) hop.location).pos());
        double first = hop.entry.distanceTo(center), second = center.distanceTo(hop.exit);
        return distance < first ? hop.entry.lerp(center, Math.min(1, distance / Math.max(first, 1.0E-6)))
                : center.lerp(hop.exit, Math.min(1, (distance - first) / Math.max(second, 1.0E-6)));
    }
    public void tick(ServerLevel level) {
        for (Cargo c : List.copyOf(cargo.values())) {
            Hop hop = c.route.get(c.index);
            Vec3 point = position(level, hop, c.distance);
            if (!level.isLoaded(BlockPos.containing(point))) continue;
            boolean exists = hop.location instanceof TransportLocation.Section s ? TubeSections.get(level).get(s.id()) != null
                    : PneumaticLine.isPathNode(level, ((TransportLocation.Block) hop.location).pos());
            if (!exists) { spill(level, c, point); continue; }
            if (c.recovering) {
                if (level.getGameTime() % 20 == 0) {
                    Route replacement = search(level, List.of(hop), c.stack, true);
                    if (replacement != null && (replacement.target != null || c.allowSpill)) {
                        c.route = replacement.hops; c.target = replacement.target; c.index = 0; c.recovering = false; setDirty();
                    }
                }
                if (c.recovering) continue;
            }
            int speed = moveTime(level, new Route(c.route, c.target));
            for (BlockPos controller : c.controllers) if (level.getBlockEntity(controller) instanceof TransportFlowSource source && source.isRunning()) {
                speed = speed == 0 ? source.getMoveTime() : Math.min(speed, source.getMoveTime());
            }
            if (speed <= 0) continue;
            if (hop.location instanceof TransportLocation.Block b && !allows(level, b.pos(), hop.incoming, true, true)) continue;
            double end = length(level, hop);
            double next = Math.min(end, c.distance + 1.0 / speed);
            for (Cargo other : cargo.values()) if (other != c && other.route.get(other.index).location.equals(hop.location)
                    && other.distance > c.distance) next = Math.min(next, Math.max(c.distance, other.distance - 1));
            c.distance = next; setDirty();
            if (c.distance + 1.0E-6 < end) continue;
            if (hop.location instanceof TransportLocation.Block block && level.getBlockEntity(block.pos()) instanceof TransportJunction junction) {
                Direction output = incomingAt(level, block.pos(), hop.exit, hop.incoming.getOpposite()).getOpposite();
                if (!junction.forwardPorts(block.pos().relative(hop.incoming.getOpposite()), c.stack).contains(block.pos().relative(output))) {
                    Route replacement = search(level, List.of(hop), c.stack, true);
                    if (replacement == null) continue;
                    Set<BlockPos> controllers = new HashSet<>(c.controllers);
                    for (Hop old : c.route) if (old.location instanceof TransportLocation.Block b && level.getBlockEntity(b.pos()) instanceof TransportFlowSource) controllers.add(b.pos());
                    c.controllers = List.copyOf(controllers);
                    c.route = replacement.hops; c.target = replacement.target; c.index = 0;
                    c.distance = Math.min(c.distance, length(level, c.route.getFirst()));
                    continue;
                }
            }
            if (c.index + 1 < c.route.size()) {
                Hop following = c.route.get(c.index + 1);
                boolean missing = following.location instanceof TransportLocation.Section s ? TubeSections.get(level).get(s.id()) == null
                        : level.isLoaded(((TransportLocation.Block) following.location).pos()) && !PneumaticLine.isPathNode(level, ((TransportLocation.Block) following.location).pos());
                if (missing) { spill(level, c, hop.exit); continue; }
                if (!level.isLoaded(BlockPos.containing(following.entry)) || !canEnter(level, following, c)) continue;
                if (following.location instanceof TransportLocation.Block b && !allows(level, b.pos(), following.incoming, true, true)) continue;
                if (following.location instanceof TransportLocation.Block b && level.getBlockEntity(b.pos()) instanceof TransportJunction junction) {
                    BlockPos previous = b.pos().relative(following.incoming.getOpposite());
                    if (junction.isBranchPort(previous)) {
                        if (!junction.canMergeFrom(level, previous)) continue;
                        junction.markMergeInputUsed(previous);
                    }
                }
                c.index++; c.distance = 0;
            } else if (c.target == null) {
                // An open end may have been extended after this cargo was dispatched.
                // Keep the pump upstream when replacing the remaining route.
                Route replacement = search(level, List.of(hop), c.stack, true);
                if (replacement != null && (replacement.target != null || !replacement.hops.equals(List.of(hop)))) {
                    Set<BlockPos> controllers = new HashSet<>(c.controllers);
                    for (Hop old : c.route) if (old.location instanceof TransportLocation.Block b
                            && level.getBlockEntity(b.pos()) instanceof TransportFlowSource) controllers.add(b.pos());
                    c.controllers = List.copyOf(controllers);
                    c.route = replacement.hops; c.target = replacement.target; c.index = 0;
                    c.distance = Math.min(c.distance, length(level, c.route.getFirst()));
                    setDirty();
                    continue;
                }
                Vec3 outward = hop.location instanceof TransportLocation.Section s
                        ? TubeSections.get(level).get(s.id()).port(!s.start()).outward()
                        : hop.exit.subtract(Vec3.atCenterOf(((TransportLocation.Block) hop.location).pos())).normalize();
                if (level.getBlockState(BlockPos.containing(hop.exit.add(outward.scale(0.05)))).isAir()) spill(level, c, hop.exit);
            } else if (receives(level, c.target, hop.location instanceof TransportLocation.Section s
                    ? TubeSections.get(level).get(s.id()).port(!s.start()).direction()
                    : Direction.getNearest(hop.exit.x - Vec3.atCenterOf(((TransportLocation.Block) hop.location).pos()).x,
                    hop.exit.y - Vec3.atCenterOf(((TransportLocation.Block) hop.location).pos()).y,
                    hop.exit.z - Vec3.atCenterOf(((TransportLocation.Block) hop.location).pos()).z))) {
                var endpoint = (TransportEndpoint) level.getBlockEntity(c.target);
                ItemStack displayedStack = c.stack.copy();
                c.stack = endpoint.insertCargo(level, c.stack);
                if (c.stack.isEmpty()) {
                    SectionCargoPayload.finish(level, c, displayedStack, hop.exit);
                    cargo.remove(c.id);
                }
            } else if (level.isLoaded(c.target) && level.getBlockState(c.target).isAir()) {
                spill(level, c, hop.exit);
            }
        }
        Set<BlockPos> observers = new HashSet<>();
        for (Cargo c : cargo.values()) {
            Hop hop = c.route.get(c.index);
            if (hop.location instanceof TransportLocation.Block b && level.getBlockEntity(b.pos()) instanceof TransportQueueObserver observer) {
                observers.add(b.pos()); observer.onQueueStateChanged(level, c.distance + 1.0E-6 >= length(level, hop));
            }
        }
        for (BlockPos pos : notifiedObservers) if (!observers.contains(pos) && level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof TransportQueueObserver observer) observer.onQueueStateChanged(level, false);
        notifiedObservers.clear(); notifiedObservers.addAll(observers);
        if (level.getGameTime() % 2 == 0) SectionCargoPayload.send(level, cargo.values());
    }
    private void spill(ServerLevel level, Cargo c, Vec3 position) {
        SectionCargoPayload.finish(level, c, c.stack, position);
        level.addFreshEntity(new ItemEntity(level, position.x, position.y, position.z, c.stack));
        cargo.remove(c.id); setDirty();
    }
    public void ejectSection(ServerLevel level, UUID id) {
        for (Cargo c : List.copyOf(cargo.values())) {
            Hop hop = c.route.get(c.index);
            if (hop.location instanceof TransportLocation.Section section && section.id().equals(id)) spill(level, c, position(level, hop, c.distance));
        }
    }
    public static boolean hasPendingMerge(Level level, BlockPos junction, BlockPos branch) {
        if (!(level instanceof ServerLevel server)) return false;
        for (Cargo c : get(server).cargo.values()) {
            if (c.index + 1 >= c.route.size()) continue;
            Hop next = c.route.get(c.index + 1);
            if (next.location instanceof TransportLocation.Block b && b.pos().equals(junction)
                    && junction.relative(next.incoming.getOpposite()).equals(branch)) return true;
        }
        return false;
    }
    @SubscribeEvent public static void tickLevel(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) get(level).tick(level);
    }
    public static final class Cargo {
        public final UUID id;
        public ItemStack stack;
        public List<Hop> route;
        public BlockPos target;
        public List<BlockPos> controllers = List.of();
        public boolean recovering;
        public boolean allowSpill;
        public int index;
        public double distance;
        Cargo(UUID id, ItemStack stack, List<Hop> route, BlockPos target) { this.id = id; this.stack = stack; this.route = List.copyOf(route); this.target = target; }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Cargo c : cargo.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", c.id); entry.put("Stack", c.stack.save(registries));
            entry.putInt("Index", c.index); entry.putDouble("Distance", c.distance);
            entry.putBoolean("Recovering", c.recovering); entry.putBoolean("AllowSpill", c.allowSpill);
            entry.putLongArray("Controllers", c.controllers.stream().mapToLong(BlockPos::asLong).toArray());
            if (c.target != null) entry.putLong("Target", c.target.asLong());
            ListTag hops = new ListTag();
            for (Hop hop : c.route) {
                CompoundTag h = hop.location.save(); h.putString("Incoming", hop.incoming.getName());
                point(h, "Entry", hop.entry); point(h, "Exit", hop.exit); hops.add(h);
            }
            entry.put("Route", hops); entries.add(entry);
        }
        tag.put("Cargo", entries); return tag;
    }
    public static SectionTransport load(CompoundTag tag, HolderLookup.Provider registries) {
        SectionTransport result = new SectionTransport();
        ListTag entries = tag.getList("Cargo", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.getCompound(i); List<Hop> route = new ArrayList<>();
            var hops = e.getList("Route", Tag.TAG_COMPOUND);
            for (int j = 0; j < hops.size(); j++) {
                var h = hops.getCompound(j); route.add(new Hop(TransportLocation.load(h), Direction.byName(h.getString("Incoming")), point(h, "Entry"), point(h, "Exit")));
            }
            Cargo c = new Cargo(e.getUUID("Id"), ItemStack.parseOptional(registries, e.getCompound("Stack")), route,
                    e.contains("Target") ? BlockPos.of(e.getLong("Target")) : null);
            c.index = e.getInt("Index"); c.distance = e.getDouble("Distance"); result.cargo.put(c.id, c);
            c.recovering = e.getBoolean("Recovering"); c.allowSpill = e.getBoolean("AllowSpill");
            c.controllers = java.util.Arrays.stream(e.getLongArray("Controllers")).mapToObj(BlockPos::of).toList();
        }
        return result;
    }
    static void point(CompoundTag tag, String key, Vec3 point) { tag.putDouble(key + "X", point.x); tag.putDouble(key + "Y", point.y); tag.putDouble(key + "Z", point.z); }
    static Vec3 point(CompoundTag tag, String key) { return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z")); }
}
