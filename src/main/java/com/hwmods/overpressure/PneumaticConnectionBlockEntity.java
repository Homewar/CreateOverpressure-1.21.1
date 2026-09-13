package com.hwmods.overpressure;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.hwmods.overpressure.transport.TransportEndpoint;
import com.hwmods.overpressure.transport.TubeGraphRoute;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class PneumaticConnectionBlockEntity extends SmartBlockEntity implements TransportEndpoint {
    private static final int TRANSFER_COOLDOWN = 5;
    private FilteringBehaviour filtering;

    public PneumaticConnectionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PNEUMATIC_CONNECTION.get(), pos, state);
    }

    @Override
    public void addBehaviours(java.util.List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new PneumaticConnectionFilterSlot())
                .onlyActiveWhen(() -> getBlockState().getValue(PneumaticConnectionBlock.MODE)
                        != PneumaticConnectionBlock.ConnectionMode.INSERT);
        behaviours.add(filtering);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PneumaticConnectionBlockEntity connector) {
        if (level.getGameTime() % TRANSFER_COOLDOWN != 0) {
            return;
        }

        if (state.getValue(PneumaticConnectionBlock.MODE) == PneumaticConnectionBlock.ConnectionMode.EXTRACT
                && !state.getValue(PneumaticConnectionBlock.POWERED)) {
            connector.tryStartTransfer(level);
        }
    }

    private void tryStartTransfer(Level level) {
        InventorySide source = findAttachedInventory(level);

        if (source == null) {
            return;
        }

        Direction facing = getBlockState().getValue(PneumaticConnectionBlock.FACING);
        BlockPos firstTubePos = worldPosition.relative(facing);
        ItemStack sectionCandidate = extractItems(level, source.handler(), true);
        var sectionRoute = com.hwmods.overpressure.tube.SectionTransport.find(level, worldPosition, facing, sectionCandidate);
        if (sectionRoute != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            var transport = com.hwmods.overpressure.tube.SectionTransport.get(server);
            if (sectionCandidate.isEmpty() || !transport.canAccept(server, sectionRoute)) return;
            ItemStack extracted = extractItems(level, source.handler(), false);
            if (!extracted.isEmpty() && !transport.accept(server, extracted, sectionRoute)) insertItem(source.handler(), extracted);
            setChanged();
            return;
        }
        if (!PneumaticLine.isPathNode(level, firstTubePos)) {
            return;
        }

        ItemStack simulated = extractItems(level, source.handler(), true);

        if (simulated.isEmpty()) {
            return;
        }

        TubePath path = TubeNetworkPathfinder.findPathToInsertConnector(
                level,
                worldPosition,
                firstTubePos,
                simulated
        );

        if (path.isEmpty()) {
            return;
        }

        PneumaticTubeBlockEntity firstTube = findFirstTubeOnPath(level, path);

        if (firstTube == null) {
            return;
        }

        if (!firstTube.canAcceptItem(simulated, path)) {
            return;
        }

        ItemStack extracted = extractItems(level, source.handler(), false);

        if (extracted.isEmpty()) {
            return;
        }

        if (!firstTube.acceptItem(extracted, path, worldPosition)) {
            insertItem(source.handler(), extracted);
            return;
        }

        TubeNetworkPathfinder.commitDeviderChoices(level, path);
        setChanged();
    }

    /**
     * Create arm output. Package filters use Create's own address matcher, so
     * wildcard and blank-address behavior stays identical to other logistics blocks.
     */
    public ItemStack insertFromArm(ItemStack stack, boolean simulate) {
        if (level == null
                || level.isClientSide
                || stack.isEmpty()
                || !PackageItem.isPackage(stack)
                || getBlockState().getValue(PneumaticConnectionBlock.MODE)
                != PneumaticConnectionBlock.ConnectionMode.EXTRACT
                || getBlockState().getValue(PneumaticConnectionBlock.POWERED)
                || filtering == null
                || !filtering.test(stack)) {
            return stack;
        }

        Direction facing = getBlockState().getValue(PneumaticConnectionBlock.FACING);
        BlockPos firstTubePos = worldPosition.relative(facing);
        var sectionRoute = com.hwmods.overpressure.tube.SectionTransport.find(level, worldPosition, facing, stack.copyWithCount(1));
        if (sectionRoute != null && level instanceof net.minecraft.server.level.ServerLevel server) {
            var transport = com.hwmods.overpressure.tube.SectionTransport.get(server);
            if (!transport.canAccept(server, sectionRoute) || !simulate && !transport.accept(server, stack.copyWithCount(1), sectionRoute)) return stack;
            ItemStack remainder = stack.copy(); remainder.shrink(1); return remainder;
        }
        if (!PneumaticLine.isPathNode(level, firstTubePos)) {
            return stack;
        }

        ItemStack capsule = stack.copyWithCount(1);
        TubePath path = TubeNetworkPathfinder.findPathToInsertConnector(
                level,
                worldPosition,
                firstTubePos,
                capsule
        );
        if (path.isEmpty()) {
            return stack;
        }

        PneumaticTubeBlockEntity firstTube = findFirstTubeOnPath(level, path);
        if (firstTube == null || !firstTube.canAcceptItem(capsule, path)) {
            return stack;
        }

        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        if (simulate) {
            return remainder;
        }

        if (!firstTube.acceptItem(capsule, path, worldPosition)) {
            return stack;
        }

        TubeNetworkPathfinder.commitDeviderChoices(level, path);
        setChanged();
        return remainder;
    }

    @Nullable
    private PneumaticTubeBlockEntity findFirstTubeOnPath(Level level, TubePath path) {
        for (BlockPos pathPos : path.tubePositions()) {
            BlockEntity blockEntity = level.getBlockEntity(pathPos);

            if (blockEntity instanceof PneumaticTubeBlockEntity tube) {
                return tube;
            }
        }

        return null;
    }

    public ItemStack insertIntoAttachedInventory(Level level, ItemStack stack) {
        if (getBlockState().getValue(PneumaticConnectionBlock.MODE)
                != PneumaticConnectionBlock.ConnectionMode.INSERT) {
            return stack;
        }

        InventorySide target = findAttachedInventory(level);

        if (target == null) {
            return stack;
        }

        ItemStack remaining = insertItem(target.handler(), stack);
        setChanged();
        return remaining;
    }

    @Override
    public ItemStack insertCargo(Level level, ItemStack stack) {
        return insertIntoAttachedInventory(level, stack);
    }

    @Override
    public boolean canReceiveFrom(Level level, BlockPos sourcePos) {
        BlockState state = getBlockState();
        if (state.getValue(PneumaticConnectionBlock.MODE)
                != PneumaticConnectionBlock.ConnectionMode.INSERT) {
            return false;
        }
        Direction facing = state.getValue(PneumaticConnectionBlock.FACING);
        return worldPosition.relative(facing.getOpposite()).equals(sourcePos)
                && PneumaticLine.isPathNode(level, sourcePos)
                && PneumaticLine.isRouteAllowed(level, sourcePos, worldPosition);
    }

    @Override
    public boolean allowsRoute(Level level, Direction travelDirection) {
        BlockState state = getBlockState();
        if (state.getValue(PneumaticConnectionBlock.MODE)
                == PneumaticConnectionBlock.ConnectionMode.DISABLED) {
            return false;
        }
        return state.getValue(PneumaticConnectionBlock.FACING) == travelDirection;
    }

    @Nullable
    private InventorySide findAttachedInventory(Level level) {
        BlockState state = getBlockState();
        Direction facing = state.getValue(PneumaticConnectionBlock.FACING);
        PneumaticConnectionBlock.ConnectionMode mode = state.getValue(PneumaticConnectionBlock.MODE);
        Direction inventorySide = switch (mode) {
            case EXTRACT -> facing.getOpposite();
            case INSERT -> facing;
            case DISABLED -> null;
        };

        if (inventorySide == null) {
            return null;
        }

        BlockPos inventoryPos = worldPosition.relative(inventorySide);
        if (Config.PACKAGERS_ONLY.get()
                && !(level.getBlockEntity(inventoryPos) instanceof PackagerBlockEntity)) {
            return null;
        }
        IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                inventoryPos,
                inventorySide.getOpposite()
        );

        if (handler == null) {
            return null;
        }

        return new InventorySide(inventoryPos, handler);
    }

    public ItemStack getFilter() {
        return filtering.getFilter();
    }

    public boolean hasRoutingFilter() {
        return filtering != null && !filtering.getFilter().isEmpty();
    }

    public boolean routingFilterMatches(ItemStack stack) {
        return filtering != null && filtering.test(stack);
    }

    public void setFilter(ItemStack filter) {
        filtering.setFilter(filter);
    }

    public ItemStack removeFilter() {
        ItemStack removed = filtering.getFilter().copy();
        filtering.setFilter(ItemStack.EMPTY);

        return removed;
    }

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.CONNECTOR;
    }


    private ItemStack extractItems(Level level, IItemHandler handler, boolean simulate) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack simulated = handler.extractItem(slot, 1, true);

            if (!simulated.isEmpty() && Config.canEnterTube(simulated) && filtering.test(simulated)) {
                return simulate ? simulated : handler.extractItem(slot, 1, false);
            }
        }

        return ItemStack.EMPTY;
    }

    private ItemStack insertItem(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack;

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);

            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remaining;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        if (filtering.getFilter().isEmpty() && tag.contains("filter")) {
            filtering.setFilter(ItemStack.parseOptional(registries, tag.getCompound("filter")));
        }
    }
}
