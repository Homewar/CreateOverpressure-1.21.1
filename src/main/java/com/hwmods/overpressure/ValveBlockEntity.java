package com.hwmods.overpressure;

import com.hwmods.overpressure.transport.TransportGate;
import com.hwmods.overpressure.transport.TubeGraphRoute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ValveBlockEntity extends PneumaticTubeBlockEntity implements TransportGate {
    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VALVE.get(), pos, state);
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return direction.getAxis() == getBlockState().getValue(BlockStateProperties.FACING).getAxis();
    }

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.VALVE;
    }

    @Override
    public boolean allowsRoute(Level level, Direction travelDirection) {
        return canTravelTo(level, travelDirection);
    }

    @Override
    public boolean allowsTravelNow(Level level, Direction travelDirection) {
        return getBlockState().getBlock() instanceof ValveBlock valve
                && valve.allowsTravel(getBlockState(), travelDirection);
    }
}
