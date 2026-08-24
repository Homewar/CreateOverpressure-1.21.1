package com.hwmods.overpressure.transport;

import java.util.List;

import com.hwmods.overpressure.CurvaturePneumaticTubeBlock;
import com.hwmods.overpressure.PneumaticTubeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Routing and merge arbitration contract used by the graph instead of a concrete block class. */
public interface TransportJunction extends TransportNodeComponent {
    List<BlockPos> connectedPorts();

    List<BlockPos> forwardPorts(BlockPos previous);

    default List<BlockPos> forwardPorts(BlockPos previous, ItemStack cargo) {
        return forwardPorts(previous);
    }

    List<BlockPos> orderedBranchPorts();

    default List<BlockPos> orderedBranchPorts(ItemStack cargo) {
        return orderedBranchPorts();
    }

    BlockPos straightPort();

    boolean isStraightPort(BlockPos pos);

    boolean isBranchPort(BlockPos pos);

    boolean isBranchOutputEnabled(BlockPos pos);

    boolean isBranchInputEnabled(BlockPos pos);

    boolean isMerger();

    boolean isMergerPassage(BlockPos branchPos, BlockPos straightPos);

    boolean canMergeFrom(Level level, BlockPos branchPos);

    void markBranchUsed(BlockPos branchPos);

    void markMergeInputUsed(BlockPos branchPos);

    default boolean isPortUsable(Level level, BlockPos portPos) {
        BlockEntity blockEntity = level.getBlockEntity(portPos);
        if (isStraightPort(portPos)) {
            return blockEntity instanceof PneumaticTubeBlockEntity
                    || (blockEntity instanceof TransportNodeComponent node && !node.hasCargoSlot());
        }
        return isBranchPort(portPos)
                && level.getBlockState(portPos).getBlock() instanceof CurvaturePneumaticTubeBlock
                && blockEntity instanceof PneumaticTubeBlockEntity;
    }
}
