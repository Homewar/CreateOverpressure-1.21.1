package com.hwmods.overpressure;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

public class CreativePumpSpeedBehaviour extends ScrollValueBehaviour {
    private static final String NBT_KEY = "CreativePumpSpeed";

    public CreativePumpSpeedBehaviour(ItemPumpBlockEntity pump) {
        super(Component.translatable("overpressure.creative_pump.speed"), pump, new ValueBoxTransform.Sided() {
            @Override
            protected Vec3 getSouthLocation() {
                return new Vec3(0.5, 0.5, 14.1 / 16.0);
            }

            @Override
            public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
                // Place the control on the outlet collar, beyond the rotating gear.
                return super.getLocalOffset(level, pos, state).add(
                        Vec3.atLowerCornerOf(state.getValue(ItemPumpBlock.FACING).getNormal()).scale(6.5 / 16.0));
            }

            @Override
            public float getScale() {
                return 0.3f;
            }

            @Override
            protected boolean isSideActive(BlockState state, Direction side) {
                return side.getAxis() != state.getValue(ItemPumpBlock.FACING).getAxis();
            }
        });
        between(1, 256);
        value = 256;
        withCallback(speed -> PneumaticTubeBlockEntity.invalidateTransportTopologyAt(pump.getLevel(), pump.getBlockPos()));
        withClientCallback(speed -> PneumaticTubeBlockEntity.invalidateClientPathAt(pump.getLevel(), pump.getBlockPos()));
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt(NBT_KEY, value);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        int previous = value;
        // Existing creative pumps retain their former maximum speed.
        value = tag.contains(NBT_KEY) ? Math.max(1, Math.min(256, tag.getInt(NBT_KEY))) : 256;
        if (clientPacket && previous != value && blockEntity.getLevel() != null) {
            PneumaticTubeBlockEntity.invalidateClientPathAt(blockEntity.getLevel(), blockEntity.getBlockPos());
        }
    }
}
