package com.hwmods.overpressure;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CapsulePortBlock extends BaseEntityBlock implements IWrenchable {
    public static final MapCodec<CapsulePortBlock> CODEC = simpleCodec(CapsulePortBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape VERTICAL_SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape X_AXIS_SHAPE = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape Z_AXIS_SHAPE = Block.box(3, 3, 0, 13, 13, 16);

    public CapsulePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(OPEN, false)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CapsulePortBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CAPSULE_PORT.get(), CapsulePortBlockEntity::serverTick);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        Direction adjacentLine = findAdjacentPneumaticLine(context);
        if (adjacentLine != null) {
            facing = adjacentLine.getOpposite();
        }

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Nullable
    private static Direction findAdjacentPneumaticLine(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedNeighbor = context.getClickedFace().getOpposite();

        if (PneumaticLine.isPathNode(level, pos.relative(clickedNeighbor))) {
            return clickedNeighbor;
        }

        Player player = context.getPlayer();
        Direction[] directions = player == null
                ? Direction.values()
                : Direction.orderedByNearest(player);
        for (Direction direction : directions) {
            if (PneumaticLine.isPathNode(level, pos.relative(direction))) {
                return direction;
            }
        }

        return null;
    }

    public static Direction getOutputDirection(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP, DOWN -> VERTICAL_SHAPE;
            case EAST, WEST -> X_AXIS_SHAPE;
            case SOUTH, NORTH -> Z_AXIS_SHAPE;
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof PneumaticTubeBlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!CapsulePortBlockEntity.isSupportedCargo(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof CapsulePortBlockEntity port)
                || !port.canLoadItem(stack)) {
            return ItemInteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        int inserted = port.insertFromPlayer(stack);
        if (inserted <= 0) {
            return ItemInteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(inserted);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.55f, 1.15f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof CapsulePortBlockEntity port)
                || !port.canTakeItems()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack extracted = port.takeLastItem();
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.addItem(extracted)) {
            player.drop(extracted, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.55f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);
        if (powered != wasPowered) {
            BlockState updatedState = state.setValue(POWERED, powered);
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
            if (!level.isClientSide && powered
                    && level.getBlockEntity(pos) instanceof CapsulePortBlockEntity port) {
                port.sealAndDispatch();
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getOpposite());
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        PneumaticTubeBlockEntity.invalidateTransportTopologyAt(context.getLevel(), context.getClickedPos());
        return newState;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof CapsulePortBlockEntity port) {
                port.dropContents();
                port.destroy();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
