package com.hwmods.overpressure;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.hwmods.overpressure.transport.TransportJunction;
import com.hwmods.overpressure.transport.TubeGraphRoute;
import com.hwmods.overpressure.transport.TubeTransportManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class DeviderBlockEntity extends PneumaticTubeBlockEntity implements TransportJunction {
    public static final double OUTPUT_SIDE_OFFSET = 14.0606601718 / 16.0 - 0.5;
    public static final double OUTPUT_Y = 10.9393398282 / 16.0;

    private boolean preferLeftBranch = true;
    private boolean preferLeftMergeInput = true;
    private DeviderGearBehaviour gearSelector;

    public DeviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEVIDER.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        gearSelector = new DeviderGearBehaviour(
                Component.translatable("overpressure.devider.gear_selector"),
                this,
                new DeviderModeSlot(0)
        );
        gearSelector.withCallback(this::onConfigurationChanged);
        behaviours.add(gearSelector);
    }

    private void onConfigurationChanged(int ignoredValue) {
        if (level != null) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, worldPosition);
        }
    }

    @Override
    public boolean canTravelTo(Level level, Direction direction) {
        return direction == getInputDirection() || isBranchDirection(direction);
    }

    public List<BlockPos> getForwardPositions(BlockPos previousPos) {
        if (getJunctionRole() == JunctionRole.DIVIDER && isStraightPosition(previousPos)) {
            return getOrderedBranchPositions();
        }
        if (getJunctionRole() == JunctionRole.MERGER && isBranchInputEnabled(previousPos)) {
            return List.of(getStraightPosition());
        }
        return List.of();
    }

    @Override
    public List<BlockPos> forwardPorts(BlockPos previousPos) {
        return getForwardPositions(previousPos);
    }

    public List<BlockPos> getConnectedPortPositions() {
        return List.of(
                getStraightPosition(),
                worldPosition.relative(getLeftOutputDirection()),
                worldPosition.relative(getRightOutputDirection())
        );
    }

    @Override
    public List<BlockPos> connectedPorts() {
        return getConnectedPortPositions();
    }

    public List<BlockPos> getOrderedBranchPositions() {
        if (getRoutingMode() == RoutingMode.REDSTONE) {
            return List.of(worldPosition.relative(getRedstoneSelectedDirection()));
        }

        Direction first = switch (getAutomaticBranchMode()) {
            case LEFT_PRIORITY -> getLeftOutputDirection();
            case BALANCED -> preferLeftBranch ? getLeftOutputDirection() : getRightOutputDirection();
            case RIGHT_PRIORITY -> getRightOutputDirection();
        };
        return List.of(worldPosition.relative(first), worldPosition.relative(first.getOpposite()));
    }

    @Override
    public List<BlockPos> orderedBranchPorts() {
        return getOrderedBranchPositions();
    }

    public BlockPos getStraightPosition() {
        return worldPosition.relative(getInputDirection());
    }

    @Override
    public BlockPos straightPort() {
        return getStraightPosition();
    }

    public static Vec3 getLocalOutputPoint(Direction side, Direction input) {
        Direction main = input.getOpposite();
        return new Vec3(0.5, 0.5, 0.5)
                .add(Vec3.atLowerCornerOf(side.getNormal()).scale(OUTPUT_SIDE_OFFSET))
                .add(Vec3.atLowerCornerOf(main.getNormal()).scale(OUTPUT_Y - 0.5));
    }

    public boolean isStraightPosition(BlockPos pos) {
        return pos != null && pos.equals(getStraightPosition());
    }

    @Override
    public boolean isStraightPort(BlockPos pos) {
        return isStraightPosition(pos);
    }

    public boolean isBranchPosition(BlockPos pos) {
        return pos != null && (pos.equals(worldPosition.relative(getLeftOutputDirection()))
                || pos.equals(worldPosition.relative(getRightOutputDirection())));
    }

    @Override
    public boolean isBranchPort(BlockPos pos) {
        return isBranchPosition(pos);
    }

    public boolean isBranchOutputEnabled(BlockPos pos) {
        return getJunctionRole() == JunctionRole.DIVIDER && isSelectedBranch(pos);
    }

    public boolean isBranchInputEnabled(BlockPos pos) {
        return getJunctionRole() == JunctionRole.MERGER && isSelectedBranch(pos);
    }

    public boolean isRedstoneMergerPassage(BlockPos branchPos, BlockPos straightPos) {
        return getJunctionRole() == JunctionRole.MERGER
                && getRoutingMode() == RoutingMode.REDSTONE
                && isMergerPassage(branchPos, straightPos);
    }

    public boolean isMergerPassage(BlockPos branchPos, BlockPos straightPos) {
        return isBranchPosition(branchPos) && isStraightPosition(straightPos);
    }

    @Override
    public boolean isMerger() {
        return getJunctionRole() == JunctionRole.MERGER;
    }

    private boolean isSelectedBranch(BlockPos pos) {
        return isBranchPosition(pos)
                && (getRoutingMode() != RoutingMode.REDSTONE
                || pos.equals(worldPosition.relative(getRedstoneSelectedDirection())));
    }

    private boolean isBranchDirection(Direction direction) {
        return direction == getLeftOutputDirection() || direction == getRightOutputDirection();
    }

    private Direction getRedstoneSelectedDirection() {
        boolean defaultLeft = getRedstoneDefaultBranch() == RedstoneDefaultBranch.LEFT;
        boolean selectLeft = getBlockState().getValue(DeviderBlock.POWERED) ? !defaultLeft : defaultLeft;
        return selectLeft ? getLeftOutputDirection() : getRightOutputDirection();
    }

    public void markBranchUsed(BlockPos branchPos) {
        if (getRoutingMode() != RoutingMode.AUTOMATIC
                || getAutomaticBranchMode() != AutomaticBranchMode.BALANCED) {
            return;
        }
        preferLeftBranch = !branchPos.equals(worldPosition.relative(getLeftOutputDirection()));
        setChanged();
    }

    public boolean canMergeFrom(Level level, BlockPos branchPos) {
        if (!isBranchInputEnabled(branchPos)) {
            return false;
        }
        if (getRoutingMode() == RoutingMode.REDSTONE) {
            return true;
        }

        BlockPos preferredInput;
        if (getAutomaticBranchMode() == AutomaticBranchMode.BALANCED) {
            Direction preferredDirection = preferLeftMergeInput
                    ? getLeftOutputDirection()
                    : getRightOutputDirection();
            preferredInput = worldPosition.relative(preferredDirection);
        } else {
            preferredInput = getOrderedBranchPositions().get(0);
        }
        return branchPos.equals(preferredInput) || !hasPendingMergeItem(level, preferredInput);
    }

    public void markMergeInputUsed(BlockPos branchPos) {
        if (getRoutingMode() != RoutingMode.AUTOMATIC
                || getAutomaticBranchMode() != AutomaticBranchMode.BALANCED) {
            return;
        }
        preferLeftMergeInput = !branchPos.equals(worldPosition.relative(getLeftOutputDirection()));
        setChanged();
    }

    private boolean hasPendingMergeItem(Level level, BlockPos branchPos) {
        if (com.hwmods.overpressure.tube.SectionTransport.hasPendingMerge(level, worldPosition, branchPos)) return true;
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

        return TubeTransportManager.get(level).isHeadingTo(itemTubePos, dividerOffset, worldPosition);
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

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.JUNCTION;
    }

    public JunctionRole getJunctionRole() {
        return gearSelector == null ? JunctionRole.DIVIDER : gearSelector.getRole();
    }

    public RoutingMode getRoutingMode() {
        return getRoutingSelection().isRedstone() ? RoutingMode.REDSTONE : RoutingMode.AUTOMATIC;
    }

    public AutomaticBranchMode getAutomaticBranchMode() {
        return switch (getRoutingSelection()) {
            case LEFT_PRIORITY -> AutomaticBranchMode.LEFT_PRIORITY;
            case RIGHT_PRIORITY -> AutomaticBranchMode.RIGHT_PRIORITY;
            default -> AutomaticBranchMode.BALANCED;
        };
    }

    public RedstoneDefaultBranch getRedstoneDefaultBranch() {
        return getRoutingSelection() == RoutingSelection.REDSTONE_INVERTED
                ? RedstoneDefaultBranch.RIGHT
                : RedstoneDefaultBranch.LEFT;
    }

    public RoutingSelection getRoutingSelection() {
        return gearSelector == null ? RoutingSelection.BALANCED : gearSelector.getRoutingSelection();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("PreferLeftBranch", preferLeftBranch);
        tag.putBoolean("PreferLeftMergeInput", preferLeftMergeInput);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("PreferLeftBranch")) {
            preferLeftBranch = tag.getBoolean("PreferLeftBranch");
        }
        if (tag.contains("PreferLeftMergeInput")) {
            preferLeftMergeInput = tag.getBoolean("PreferLeftMergeInput");
        }
    }

    public enum JunctionRole {
        DIVIDER,
        MERGER
    }

    public enum RoutingMode {
        AUTOMATIC,
        REDSTONE
    }

    public enum AutomaticBranchMode {
        LEFT_PRIORITY,
        BALANCED,
        RIGHT_PRIORITY
    }

    public enum RedstoneDefaultBranch {
        LEFT,
        RIGHT
    }

    public enum RoutingSelection {
        LEFT_PRIORITY,
        BALANCED,
        RIGHT_PRIORITY,
        REDSTONE,
        REDSTONE_INVERTED;

        public boolean isRedstone() {
            return this == REDSTONE || this == REDSTONE_INVERTED;
        }
    }
}
