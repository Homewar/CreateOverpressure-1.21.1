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
    private boolean preferPositiveBranch;
    private boolean preferPositiveMergeInput;
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
        return direction == getInputDirection() || isBranchDirectionEnabled(direction);
    }

    public List<BlockPos> getForwardPositions(BlockPos previousPos) {
        if (isStraightPosition(previousPos)) {
            return getOrderedBranchPositions();
        }
        if (isBranchPositionEnabled(previousPos)) {
            return List.of(getStraightPosition());
        }
        return List.of();
    }

    public List<BlockPos> getConnectedPortPositions() {
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>(3);
        positions.add(getStraightPosition());
        positions.addAll(getOrderedBranchPositions());
        return positions;
    }

    public List<BlockPos> getOrderedBranchPositions() {
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        Direction negative = positive.getOpposite();

        if (getRoutingMode() == RoutingMode.REDSTONE) {
            Direction selected = getBlockState().getValue(DeviderBlock.POWERED)
                    ? getRightOutputDirection()
                    : getLeftOutputDirection();
            return List.of(worldPosition.relative(selected));
        }

        Direction first = preferPositiveBranch ? positive : negative;
        return List.of(worldPosition.relative(first), worldPosition.relative(first.getOpposite()));
    }

    public BlockPos getStraightPosition() {
        return worldPosition.relative(getInputDirection());
    }

    public static Vec3 getLocalOutputPoint(Direction side, Direction input) {
        Direction main = input.getOpposite();
        return new Vec3(0.5, 0.5, 0.5)
                .add(Vec3.atLowerCornerOf(side.getNormal()).scale(OUTPUT_SIDE_OFFSET))
                .add(Vec3.atLowerCornerOf(main.getNormal()).scale(OUTPUT_Y - 0.5));
    }

    public boolean isStraightPosition(BlockPos pos) {
        return pos.equals(getStraightPosition());
    }

    public boolean isBranchPosition(BlockPos pos) {
        return pos.equals(worldPosition.relative(getLeftOutputDirection()))
                || pos.equals(worldPosition.relative(getRightOutputDirection()));
    }

    public boolean isBranchPositionEnabled(BlockPos pos) {
        Direction direction = Direction.getNearest(
                pos.getX() - worldPosition.getX(),
                pos.getY() - worldPosition.getY(),
                pos.getZ() - worldPosition.getZ()
        );
        return worldPosition.relative(direction).equals(pos) && isBranchDirectionEnabled(direction);
    }

    private boolean isBranchDirectionEnabled(Direction direction) {
        if (direction != getLeftOutputDirection() && direction != getRightOutputDirection()) {
            return false;
        }

        if (getRoutingMode() != RoutingMode.REDSTONE) {
            return true;
        }

        Direction selected = getBlockState().getValue(DeviderBlock.POWERED)
                ? getRightOutputDirection()
                : getLeftOutputDirection();
        return direction == selected;
    }

    public void markBranchUsed(BlockPos branchPos) {
        if (getRoutingMode() != RoutingMode.DISTRIBUTE) {
            return;
        }

        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        preferPositiveBranch = !branchPos.equals(worldPosition.relative(positive));
        setChanged();
    }

    public boolean canMergeFrom(net.minecraft.world.level.Level level, BlockPos branchPos) {
        if (!isBranchPositionEnabled(branchPos)) {
            return false;
        }
        if (getRoutingMode() == RoutingMode.REDSTONE) {
            return true;
        }

        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        BlockPos preferredInput = worldPosition.relative(
                preferPositiveMergeInput ? positive : positive.getOpposite()
        );
        return branchPos.equals(preferredInput) || !hasPendingMergeItem(level, preferredInput);
    }

    public void markMergeInputUsed(BlockPos branchPos) {
        if (getRoutingMode() != RoutingMode.DISTRIBUTE) {
            return;
        }

        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, getOutputAxis());
        preferPositiveMergeInput = !branchPos.equals(worldPosition.relative(positive));
        setChanged();
    }

    private boolean hasPendingMergeItem(net.minecraft.world.level.Level level, BlockPos branchPos) {
        BlockPos itemTubePos = branchPos;
        int dividerOffset = 1;
        if (level.getBlockEntity(branchPos) instanceof ItemPumpBlockEntity) {
            Direction outward = Direction.getNearest(
                    branchPos.getX() - worldPosition.getX(),
                    branchPos.getY() - worldPosition.getY(),
                    branchPos.getZ() - worldPosition.getZ()
            );
            itemTubePos = branchPos.relative(outward);
            dividerOffset = 2;
        }

        if (!(level.getBlockEntity(itemTubePos) instanceof PneumaticTubeBlockEntity tube)) {
            return false;
        }

        MovingTubeItem item = tube.getMovingItem();
        int dividerIndex = item == null ? -1 : item.pathIndex + dividerOffset;
        return item != null
                && dividerIndex < item.path.size()
                && item.path.get(dividerIndex).equals(worldPosition);
    }

    public Direction.Axis getOutputAxis() {
        return getBlockState().getValue(DeviderBlock.AXIS);
    }

    public Direction getLeftOutputDirection() {
        return DeviderBlock.getLeftOutputDirection(getBlockState());
    }

    public Direction getRightOutputDirection() {
        return DeviderBlock.getRightOutputDirection(getBlockState());
    }

    public Direction getInputDirection() {
        return getBlockState().getValue(DeviderBlock.INPUT);
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
        tag.putBoolean("prefer_positive_branch", preferPositiveBranch);
        tag.putBoolean("prefer_positive_merge_input", preferPositiveMergeInput);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        preferPositiveBranch = tag.contains("prefer_positive_branch")
                ? tag.getBoolean("prefer_positive_branch")
                : tag.getBoolean("prefer_positive_output");
        preferPositiveMergeInput = tag.getBoolean("prefer_positive_merge_input");
    }

}
