package com.hwmods.overpressure;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GrindRailBlock extends BaseEntityBlock {
    public static final MapCodec<GrindRailBlock> CODEC = simpleCodec(GrindRailBlock::new);
    private static final VoxelShape SELECTION_SHAPE = Block.box(4, 4, 4, 12, 12, 12);
    private static final Set<BlockPos> DESTROYING = new HashSet<>();

    public GrindRailBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SELECTION_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrindRailBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.GRIND_RAIL_ITEM.get());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        destroySection(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            destroySection(level, pos, null);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void destroySection(Level level, BlockPos origin, @Nullable Player player) {
        if (level.isClientSide || DESTROYING.contains(origin)) {
            return;
        }
        if (!(level.getBlockEntity(origin) instanceof GrindRailBlockEntity originRail)) {
            return;
        }

        UUID sectionId = originRail.getSectionId();
        Set<BlockPos> section = collectSection(level, origin, sectionId);
        if (section.size() <= 1) {
            return;
        }

        DESTROYING.addAll(section);
        try {
            for (BlockPos railPos : section) {
                if (!railPos.equals(origin) && level.getBlockState(railPos).is(this)) {
                    level.destroyBlock(railPos, true, player);
                }
            }
        } finally {
            DESTROYING.removeAll(section);
        }
    }

    private Set<BlockPos> collectSection(Level level, BlockPos origin, UUID sectionId) {
        Set<BlockPos> found = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        found.add(origin);
        queue.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(x, y, z);
                        if (found.contains(next)) {
                            continue;
                        }
                        if (level.getBlockEntity(next) instanceof GrindRailBlockEntity rail
                                && rail.getSectionId().equals(sectionId)) {
                            found.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return found;
    }
}
