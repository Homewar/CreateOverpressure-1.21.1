package com.hwmods.overpressure;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DeviderBlockEntity extends PneumaticTubeBlockEntity {
    public static final double OUTPUT_SIDE_OFFSET = 14.0606601718 / 16.0 - 0.5;
    public static final double OUTPUT_Y = 10.9393398282 / 16.0;
    private boolean preferPositiveOutput;
    private ScrollOptionBehaviour<RoutingMode> routingMode;

    public DeviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEVIDER.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        routingMode = new ScrollOptionBehaviour<>(
                RoutingMode.class,
                Component.translatable("overpressure.devider.routing_mode"),
                this,
                new DeviderModeSlot()
        );
        behaviours.add(routingMode);
    }

    @Override
    public boolean canTravelTo(net.minecraft.world.level.Level level, Direction direction) {
        return direction == Direction.DOWN;
    }

    public List<BlockPos> getOrderedOutputPositions() {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        Direction negative = positive.getOpposite();

        if (getRoutingMode() == RoutingMode.REDSTONE) {
            Direction selected = getBlockState().getValue(DeviderBlock.POWERED)
                    ? getRightOutputDirection()
                    : getLeftOutputDirection();
            return List.of(getOutputPosition(selected));
        }

        Direction first = preferPositiveOutput ? positive : negative;
        Direction second = first.getOpposite();
        return List.of(getOutputPosition(first), getOutputPosition(second));
    }

    public BlockPos getOutputPosition(Direction side) {
        return worldPosition.relative(side);
    }

    public static Vec3 getLocalOutputPoint(Direction side) {
        return new Vec3(
                0.5 + side.getStepX() * OUTPUT_SIDE_OFFSET,
                OUTPUT_Y,
                0.5 + side.getStepZ() * OUTPUT_SIDE_OFFSET
        );
    }

    public boolean isOutputPosition(BlockPos pos) {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        return pos.equals(getOutputPosition(positive))
                || pos.equals(getOutputPosition(positive.getOpposite()));
    }

    public boolean isOutputEnabled(BlockPos pos) {
        return getOrderedOutputPositions().contains(pos);
    }

    public void markOutputUsed(BlockPos outputPos) {
        if (getRoutingMode() != RoutingMode.DISTRIBUTE) {
            return;
        }

        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        preferPositiveOutput = !outputPos.equals(getOutputPosition(positive));
        setChanged();
    }

    public Direction.Axis getOutputAxis() {
        return getBlockState().getValue(DeviderBlock.AXIS);
    }

    public Direction getLeftOutputDirection() {
        return getFrontDirection().getClockWise();
    }

    public Direction getRightOutputDirection() {
        return getLeftOutputDirection().getOpposite();
    }

    private Direction getFrontDirection() {
        Direction facing = getBlockState().getValue(DeviderBlock.FACING);
        if (facing.getAxis() != getOutputAxis()) {
            return facing;
        }

        return getOutputAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.WEST;
    }

    public RoutingMode getRoutingMode() {
        return routingMode == null ? RoutingMode.DISTRIBUTE : routingMode.get();
    }

    public enum RoutingMode implements INamedIconOptions {
        DISTRIBUTE(AllIcons.I_TUNNEL_ROUND_ROBIN, "overpressure.devider.routing_mode.distribute"),
        REDSTONE(AllIcons.I_ACTIVE, "overpressure.devider.routing_mode.redstone");

        private final AllIcons icon;
        private final String translationKey;

        RoutingMode(AllIcons icon, String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("prefer_positive_output", preferPositiveOutput);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        preferPositiveOutput = tag.getBoolean("prefer_positive_output");
    }
}
