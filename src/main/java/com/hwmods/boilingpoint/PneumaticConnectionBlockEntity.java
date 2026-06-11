package com.hwmods.boilingpoint;

import com.simibubi.create.content.logistics.filter.FilterItemStack;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class PneumaticConnectionBlockEntity extends BlockEntity {
    private static final int TRANSFER_COOLDOWN = 5;
    private ItemStack filter = ItemStack.EMPTY;

    public PneumaticConnectionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PNEUMATIC_CONNECTION.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PneumaticConnectionBlockEntity connector) {
        if (level.getGameTime() % TRANSFER_COOLDOWN != 0) {
            return;
        }

        if (state.getValue(PneumaticConnectionBlock.MODE) == PneumaticConnectionBlock.ConnectionMode.EXTRACT) {
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
        if (!(level.getBlockState(firstTubePos).getBlock() instanceof PneumaticTubeBlock
                || level.getBlockState(firstTubePos).getBlock() instanceof ItemPumpBlock)) {
            return;
        }

        TubePath path = TubeNetworkPathfinder.findPathToInsertConnector(level, worldPosition, firstTubePos);

        if (path.isEmpty()) {
            return;
        }

        PneumaticTubeBlockEntity firstTube = findFirstTubeOnPath(level, path);

        if (firstTube == null) {
            return;
        }

        ItemStack extracted = extractOneItem(level, source.handler());

        if (extracted.isEmpty()) {
            return;
        }

        if (!firstTube.acceptItem(extracted, path)) {
            insertItem(source.handler(), extracted);
            return;
        }

        setChanged();
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
        if (getBlockState().getValue(PneumaticConnectionBlock.MODE) != PneumaticConnectionBlock.ConnectionMode.INSERT) {
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
        return filter;
    }

    public void setFilter(ItemStack filter) {
        this.filter = filter.copy();
        this.filter.setCount(Math.min(this.filter.getCount(), 1));
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public ItemStack removeFilter() {
        ItemStack removed = filter;
        filter = ItemStack.EMPTY;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        return removed;
    }

    public void clearFilterClientSide() {
        filter = ItemStack.EMPTY;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private ItemStack extractOneItem(Level level, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack simulated = handler.extractItem(slot, 1, true);

            if (!simulated.isEmpty() && filterAllows(level, simulated)) {
                return handler.extractItem(slot, 1, false);
            }
        }

        return ItemStack.EMPTY;
    }

    private boolean filterAllows(Level level, ItemStack stack) {
        return filter.isEmpty() || FilterItemStack.of(filter.copy()).test(level, stack);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (!filter.isEmpty()) {
            tag.put("filter", filter.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter = tag.contains("filter") ? ItemStack.parseOptional(registries, tag.getCompound("filter")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);

        if (!filter.isEmpty()) {
            tag.put("filter", filter.save(registries));
        }

        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
