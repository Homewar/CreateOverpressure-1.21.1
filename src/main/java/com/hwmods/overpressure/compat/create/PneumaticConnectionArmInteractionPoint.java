package com.hwmods.overpressure.compat.create;

import java.util.List;

import com.hwmods.overpressure.Overpressure;
import com.hwmods.overpressure.PneumaticConnectionBlock;
import com.hwmods.overpressure.PneumaticConnectionBlockEntity;
import com.hwmods.overpressure.mixin.ArmBlockEntityAccessor;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes.DepositOnlyArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Makes an extracting pneumatic connector a native deposit target for Create arms. */
public final class PneumaticConnectionArmInteractionPoint extends DepositOnlyArmInteractionPoint {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            Overpressure.MODID,
            "pneumatic_connection"
    );

    public static void register(RegisterEvent event) {
        event.register(CreateRegistries.ARM_INTERACTION_POINT_TYPE, ID, Type::new);
    }

    private PneumaticConnectionArmInteractionPoint(
            ArmInteractionPointType type,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        super(type, level, pos, state);
    }

    @Override
    protected Vec3 getInteractionPositionVector() {
        Direction inputSide = getInteractionDirection();
        return Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(inputSide.getNormal()).scale(0.35));
    }

    @Override
    protected Direction getInteractionDirection() {
        return cachedState.getValue(PneumaticConnectionBlock.FACING).getOpposite();
    }

    @Override
    public void updateCachedState() {
        Direction previousFacing = cachedState.getValue(PneumaticConnectionBlock.FACING);
        super.updateCachedState();
        if (previousFacing != cachedState.getValue(PneumaticConnectionBlock.FACING)) {
            cachedAngles = null;
        }
    }

    @Override
    public ItemStack insert(ArmBlockEntity arm, ItemStack stack, boolean simulate) {
        if (!(level.getBlockEntity(pos) instanceof PneumaticConnectionBlockEntity connector)) {
            return stack;
        }
        if (!connector.hasRoutingFilter() && hasMatchingFilteredOutput(arm, stack)) {
            return stack;
        }
        return connector.insertFromArm(stack, simulate);
    }

    private boolean hasMatchingFilteredOutput(ArmBlockEntity arm, ItemStack stack) {
        List<ArmInteractionPoint> outputs = ((ArmBlockEntityAccessor) arm).overpressure$getOutputs();
        for (ArmInteractionPoint output : outputs) {
            if (output == this
                    || !(output instanceof PneumaticConnectionArmInteractionPoint)
                    || !output.isValid()) {
                continue;
            }
            if (level.getBlockEntity(output.getPos()) instanceof PneumaticConnectionBlockEntity connector
                    && connector.hasRoutingFilter()
                    && connector.routingFilterMatches(stack)) {
                return true;
            }
        }
        return false;
    }

    private static final class Type extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return state.getBlock() instanceof PneumaticConnectionBlock
                    && state.getValue(PneumaticConnectionBlock.MODE)
                    == PneumaticConnectionBlock.ConnectionMode.EXTRACT;
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new PneumaticConnectionArmInteractionPoint(this, level, pos, state);
        }
    }
}
