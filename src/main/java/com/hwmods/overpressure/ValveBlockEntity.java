package com.hwmods.overpressure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ValveBlockEntity extends PneumaticTubeBlockEntity {
    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VALVE.get(), pos, state);
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return direction.getAxis() == getBlockState().getValue(BlockStateProperties.FACING).getAxis();
    }
}
