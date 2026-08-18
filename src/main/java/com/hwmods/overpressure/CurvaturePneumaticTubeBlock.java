package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CurvaturePneumaticTubeBlock extends PneumaticTubeBlock {
    public static final MapCodec<CurvaturePneumaticTubeBlock> CODEC = simpleCodec(CurvaturePneumaticTubeBlock::new);
    private static final Set<BlockPos> DESTROYING_SECTION_BLOCKS = new HashSet<>();
    private static final VoxelShape FULL_BLOCK_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public CurvaturePneumaticTubeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState createDefaultState() {
        return this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(WATERLOGGED, false)
                .setValue(HAS_RIM, false)
                .setValue(HAS_SECOND_RIM, false)
                .setValue(HAS_CONNECTION, false)
                .setValue(RIM, Direction.NORTH);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                NORTH,
                SOUTH,
                EAST,
                WEST,
                UP,
                DOWN,
                WATERLOGGED,
                HAS_RIM,
                HAS_SECOND_RIM,
                HAS_CONNECTION,
                RIM
        );
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CurvaturePneumaticTubeEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.PNEUMATIC_TUBE_ITEM.get());
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            net.minecraft.core.Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return applyPreferredRim(level, pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        destroySection(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PneumaticTubeBlockEntity.invalidateTransportTopologyAt(level, pos);

            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PneumaticTubeBlockEntity tube) {
                tube.ejectMovingItem(level, Vec3.atCenterOf(pos));
            }

            destroySection(level, pos, null);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void destroySection(Level level, BlockPos origin, @Nullable Player player) {
        if (level.isClientSide || DESTROYING_SECTION_BLOCKS.contains(origin)) {
            return;
        }

        UUID sectionId = getSectionId(level, origin);

        if (sectionId == null) {
            return;
        }

        Set<BlockPos> sectionBlocks = collectSectionBlocks(level, origin, sectionId);

        if (sectionBlocks.size() <= 1) {
            return;
        }

        DESTROYING_SECTION_BLOCKS.addAll(sectionBlocks);

        try {
            for (BlockPos sectionPos : sectionBlocks) {
                if (sectionPos.equals(origin)) {
                    continue;
                }

                if (level.getBlockState(sectionPos).getBlock() instanceof CurvaturePneumaticTubeBlock) {
                    level.destroyBlock(sectionPos, true, player);
                }
            }
        } finally {
            DESTROYING_SECTION_BLOCKS.removeAll(sectionBlocks);
        }
    }

    @Nullable
    private UUID getSectionId(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof CurvaturePneumaticTubeEntity tube) {
            return tube.getSectionId();
        }

        return null;
    }

    private Set<BlockPos> collectSectionBlocks(Level level, BlockPos origin, UUID sectionId) {
        Set<BlockPos> found = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        found.add(origin);
        queue.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);

                if (found.contains(next)) {
                    continue;
                }

                BlockEntity blockEntity = level.getBlockEntity(next);

                if (!(blockEntity instanceof CurvaturePneumaticTubeEntity tube)) {
                    continue;
                }

                if (!tube.getSectionId().equals(sectionId)) {
                    continue;
                }

                found.add(next);
                queue.add(next);
            }
        }

        return found;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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

        return createTickerHelper(
                type,
                ModBlockEntities.CURVATURE_PNEUMATIC_TUBE.get(),
                CurvaturePneumaticTubeEntity::serverTick
        );
    }
}
