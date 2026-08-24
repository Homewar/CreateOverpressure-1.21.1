package com.hwmods.overpressure;

import java.util.List;

import com.hwmods.overpressure.transport.TransportJunction;
import com.hwmods.overpressure.transport.TransportNodeComponent;
import com.hwmods.overpressure.transport.TubeGraphRoute;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FilterPipeBlockEntity extends PneumaticTubeBlockEntity implements TransportJunction {
    private FilteringBehaviour filtering;

    public FilterPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FILTER_PIPE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        filtering = new FilteringBehaviour(this, new FilterPipeFilterSlot())
                .withCallback(ignored -> onFilterChanged());
        behaviours.add(filtering);
    }

    private void onFilterChanged() {
        if (level != null) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, worldPosition);
        }
    }

    public boolean routesToBranch(ItemStack cargo) {
        return filtering != null
                && !filtering.getFilter().isEmpty()
                && cargo.getItem() instanceof BlockItem
                && filtering.test(cargo);
    }

    public void setFilter(ItemStack filter) {
        if (filtering != null) {
            filtering.setFilter(filter);
        }
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return direction == getInputDirection()
                || direction == getStraightOutputDirection()
                || direction == getBranchDirection();
    }

    @Override
    public List<BlockPos> connectedPorts() {
        return List.of(getInputPosition(), getStraightOutputPosition(), getBranchPosition());
    }

    @Override
    public List<BlockPos> forwardPorts(BlockPos previous) {
        return forwardPorts(previous, ItemStack.EMPTY);
    }

    @Override
    public List<BlockPos> forwardPorts(BlockPos previous, ItemStack cargo) {
        if (!isStraightPort(previous)) {
            return List.of();
        }
        return List.of(routesToBranch(cargo) ? getBranchPosition() : getStraightOutputPosition());
    }

    @Override
    public List<BlockPos> orderedBranchPorts() {
        return List.of(getStraightOutputPosition());
    }

    @Override
    public List<BlockPos> orderedBranchPorts(ItemStack cargo) {
        return List.of(routesToBranch(cargo) ? getBranchPosition() : getStraightOutputPosition());
    }

    @Override
    public BlockPos straightPort() {
        return getInputPosition();
    }

    @Override
    public boolean isStraightPort(BlockPos pos) {
        return pos != null && pos.equals(getInputPosition());
    }

    @Override
    public boolean isBranchPort(BlockPos pos) {
        return pos != null && (pos.equals(getStraightOutputPosition()) || pos.equals(getBranchPosition()));
    }

    @Override
    public boolean isBranchOutputEnabled(BlockPos pos) {
        return isBranchPort(pos);
    }

    @Override
    public boolean isBranchInputEnabled(BlockPos pos) {
        return false;
    }

    @Override
    public boolean isMerger() {
        return false;
    }

    @Override
    public boolean isMergerPassage(BlockPos branchPos, BlockPos straightPos) {
        return false;
    }

    @Override
    public boolean canMergeFrom(Level level, BlockPos branchPos) {
        return false;
    }

    @Override
    public void markBranchUsed(BlockPos branchPos) {
    }

    @Override
    public void markMergeInputUsed(BlockPos branchPos) {
    }

    @Override
    public boolean isPortUsable(Level level, BlockPos portPos) {
        BlockEntity blockEntity = level.getBlockEntity(portPos);
        if (portPos.equals(getBranchPosition())) {
            return level.getBlockState(portPos).getBlock() instanceof CurvaturePneumaticTubeBlock
                    && blockEntity instanceof PneumaticTubeBlockEntity;
        }
        if (portPos.equals(getInputPosition()) || portPos.equals(getStraightOutputPosition())) {
            return blockEntity instanceof PneumaticTubeBlockEntity
                    || (blockEntity instanceof TransportNodeComponent node && !node.hasCargoSlot());
        }
        return false;
    }

    public BlockPos getInputPosition() {
        return worldPosition.relative(getInputDirection());
    }

    public BlockPos getStraightOutputPosition() {
        return worldPosition.relative(getStraightOutputDirection());
    }

    public BlockPos getBranchPosition() {
        return worldPosition.relative(getBranchDirection());
    }

    public Direction getInputDirection() {
        return getBlockState().getValue(FilterPipeBlock.INPUT);
    }

    public Direction getStraightOutputDirection() {
        return FilterPipeBlock.getStraightOutputDirection(getBlockState());
    }

    public Direction getBranchDirection() {
        return FilterPipeBlock.getBranchDirection(getBlockState());
    }

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.FILTER_ROUTER;
    }
}
