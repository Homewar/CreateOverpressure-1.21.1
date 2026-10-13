package com.hwmods.overpressure.tube;

import java.util.*;
import com.hwmods.overpressure.Overpressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Short-lived client state used only to visualize fluid crossing virtual curves. */
public record FluidTubeFlowPayload(ResourceLocation dimension, CompoundTag data) implements CustomPacketPayload {
    public static final Type<FluidTubeFlowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Overpressure.MODID, "fluid_tube_flow"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidTubeFlowPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeResourceLocation(p.dimension); b.writeNbt(p.data); },
            b -> new FluidTubeFlowPayload(b.readResourceLocation(), b.readNbt()));

    public record Visual(FluidStack fluid, long expires, int faces) {}
    private static final Map<Level, Map<UUID, Visual>> SECTIONS = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Visual>> PIPES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<String, Long>> LAST_SENT = new WeakHashMap<>();

    public static void send(ServerLevel level, FluidStack fluid, Collection<UUID> sections,
                            Map<BlockPos, EnumSet<Direction>> pipes) {
        if (fluid.isEmpty() || level.players().isEmpty()) return;
        String key = fluid.getFluid().toString() + sections.hashCode() + pipes.hashCode();
        long tick = level.getGameTime();
        Long previous = LAST_SENT.computeIfAbsent(level, ignored -> new HashMap<>()).put(key, tick);
        if (previous != null && previous == tick) return;

        CompoundTag data = new CompoundTag();
        data.putLong("Tick", tick);
        data.put("Fluid", fluid.copyWithAmount(1).saveOptional(level.registryAccess()));
        ListTag sectionTags = new ListTag();
        for (UUID id : sections) {
            CompoundTag entry = new CompoundTag(); entry.putUUID("Id", id); sectionTags.add(entry);
        }
        data.put("Sections", sectionTags);
        ListTag pipeTags = new ListTag();
        pipes.forEach((pos, faces) -> {
            CompoundTag entry = new CompoundTag(); entry.putLong("Pos", pos.asLong());
            int mask = 0; for (Direction direction : faces) mask |= 1 << direction.ordinal();
            entry.putByte("Faces", (byte) mask); pipeTags.add(entry);
        });
        data.put("Pipes", pipeTags);
        PacketDistributor.sendToPlayersInDimension(level, new FluidTubeFlowPayload(level.dimension().location(), data));
    }

    public static FluidStack sectionFluid(Level level, UUID id) {
        Visual visual = SECTIONS.getOrDefault(level, Map.of()).get(id);
        return active(level, visual, 0) ? visual.fluid() : FluidStack.EMPTY;
    }

    public static FluidStack pipeFluid(Level level, BlockPos pos, Direction face) {
        Visual visual = PIPES.getOrDefault(level, Map.of()).get(pos);
        return active(level, visual, face == null ? 0 : 1 << face.ordinal()) ? visual.fluid() : FluidStack.EMPTY;
    }

    private static boolean active(Level level, Visual visual, int face) {
        return visual != null && visual.expires() >= level.getGameTime()
                && (face == 0 || (visual.faces() & face) != 0);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
            Level level = context.player().level();
            if (!level.dimension().location().equals(payload.dimension())) return;
            FluidStack fluid = FluidStack.parseOptional(level.registryAccess(), payload.data().getCompound("Fluid"));
            long expires = level.getGameTime() + 8;
            Map<UUID, Visual> sections = SECTIONS.computeIfAbsent(level, ignored -> new HashMap<>());
            ListTag sectionTags = payload.data().getList("Sections", Tag.TAG_COMPOUND);
            for (int i = 0; i < sectionTags.size(); i++)
                sections.put(sectionTags.getCompound(i).getUUID("Id"), new Visual(fluid, expires, 0));
            Map<BlockPos, Visual> pipes = PIPES.computeIfAbsent(level, ignored -> new HashMap<>());
            ListTag pipeTags = payload.data().getList("Pipes", Tag.TAG_COMPOUND);
            for (int i = 0; i < pipeTags.size(); i++) {
                CompoundTag entry = pipeTags.getCompound(i);
                pipes.put(BlockPos.of(entry.getLong("Pos")), new Visual(fluid, expires, entry.getByte("Faces") & 0xff));
            }
        }));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
