package com.hwmods.overpressure;

import java.util.function.Supplier;

import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EncasedPneumaticTubeBlock extends PneumaticTubeBlock implements EncasedBlock, IWrenchable {
    private final Supplier<Block> casing;

    public EncasedPneumaticTubeBlock(Properties properties, Supplier<Block> casing) {
        super(properties);
        this.casing = casing;
    }

    @Override
    public Block getCasing() {
        return casing.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public void handleEncasing(
            BlockState state,
            Level level,
            BlockPos pos,
            ItemStack heldItem,
            Player player,
            InteractionHand hand,
        BlockHitResult ray
    ) {
        level.setBlock(pos, PneumaticTubeBlock.transferTubeProperties(state, defaultBlockState()), Block.UPDATE_ALL);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        level.setBlock(context.getClickedPos(), PneumaticTubeBlock.transferTubeProperties(
                state,
                ModBlocks.PNEUMATIC_TUBE.get().defaultBlockState()
        ), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
