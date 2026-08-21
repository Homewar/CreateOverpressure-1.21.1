package com.hwmods.overpressure;

import com.hwmods.overpressure.transport.TransportQueueObserver;
import com.hwmods.overpressure.transport.TubeGraphRoute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ClogSensorBlockEntity extends PneumaticTubeBlockEntity implements TransportQueueObserver {
    public ClogSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOG_SENSOR.get(), pos, state);
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return direction.getAxis() == getBlockState().getValue(BlockStateProperties.FACING).getAxis();
    }

    @Override
    public void onQueueStateChanged(Level level, boolean clogged) {
        BlockState state = getBlockState();
        if (state.getValue(BlockStateProperties.POWERED) == clogged) {
            return;
        }

        level.setBlock(
                worldPosition,
                state.setValue(BlockStateProperties.POWERED, clogged),
                Block.UPDATE_CLIENTS
        );
        level.updateNeighborsAt(worldPosition, state.getBlock());
    }

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.SENSOR;
    }
}
