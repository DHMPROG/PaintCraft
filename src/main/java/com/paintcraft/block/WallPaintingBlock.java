package com.paintcraft.block;

import com.paintcraft.blockentity.WallPaintingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Bloc "Tableau Mural" — version posée du tableau peint.
 *
 * Comportement :
 *  - Fin (2/16 d'épaisseur), accroché contre un mur solide
 *  - Se casse si le mur porteur est détruit
 *  - FACING = direction vers laquelle le tableau fait face (hors du mur)
 *  - Rendu des pixels délégué à WallPaintingRenderer (BlockEntityRenderer)
 */
public class WallPaintingBlock extends Block implements EntityBlock {

    // ── BlockState ────────────────────────────────────────────────────────────

    /** Direction vers laquelle le tableau fait face (hors du mur). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // ── VoxelShapes (2/16 d'épaisseur contre chaque mur) ─────────────────────

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box( 0, 0, 14, 16, 16, 16), // contre le mur sud
            Direction.SOUTH, Block.box( 0, 0,  0, 16, 16,  2), // contre le mur nord
            Direction.EAST,  Block.box( 0, 0,  0,  2, 16, 16), // contre le mur ouest
            Direction.WEST,  Block.box(14, 0,  0, 16, 16, 16)  // contre le mur est
    );

    // ── Constructeur ──────────────────────────────────────────────────────────

    public WallPaintingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ── BlockState ────────────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * FACING = face cliquée par le joueur (direction hors du mur).
     * Seules les faces horizontales sont valides.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis() == Direction.Axis.Y) return null; // pas de placement sur sol/plafond
        return this.defaultBlockState().setValue(FACING, face);
    }

    // ── Forme ─────────────────────────────────────────────────────────────────

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    // ── Survie du bloc ────────────────────────────────────────────────────────

    /**
     * Le tableau ne peut exister que s'il y a un bloc solide derrière lui.
     * "Derrière" = direction opposée à FACING.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction behind = state.getValue(FACING).getOpposite();
        BlockPos wallPos = pos.relative(behind);
        return level.getBlockState(wallPos).isFaceSturdy(level, wallPos, state.getValue(FACING));
    }

    /**
     * Recalcule l'état quand un voisin change.
     * Si le mur porteur disparaît, le tableau se casse (retourne AIR).
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ── Rendu ─────────────────────────────────────────────────────────────────

    /**
     * RenderShape.ENTITYBLOCK_ANIMATED → indique à Forge d'appeler le BlockEntityRenderer.
     * Le modèle JSON du bloc sera ignoré au profit du rendu custom.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // ── EntityBlock ───────────────────────────────────────────────────────────

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallPaintingBlockEntity(pos, state);
    }
}
