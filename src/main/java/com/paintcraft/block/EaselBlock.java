package com.paintcraft.block;

import com.paintcraft.blockentity.EaselBlockEntity;
import com.paintcraft.item.ModItems;
import com.paintcraft.menu.PaintingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Chevalet double-hauteur (2 blocs de haut, comme une porte).
 *
 * - Bloc inferieur (LOWER) : pieds du trepied + etagere. Contient le BlockEntity.
 * - Bloc superieur (UPPER) : montants + barre haute + zone toile.
 * - Destruction d'un des deux blocs detruit l'autre.
 * - Le BlockEntity est UNIQUEMENT sur le bloc inferieur.
 */
public class EaselBlock extends Block implements EntityBlock {

    // ── BlockState properties ────────────────────────────────────────────────

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty HAS_CANVAS = BooleanProperty.create("has_canvas");

    // ── VoxelShapes (simplifiees, couvrent l'ensemble du chevalet) ────────────

    private static final VoxelShape SHAPE_LOWER = Shapes.or(
            Block.box(2, 0, 3, 4, 16, 5),     // pied avant gauche
            Block.box(12, 0, 3, 14, 16, 5),    // pied avant droit
            Block.box(7, 0, 10, 9, 16, 12),    // pied arriere
            Block.box(2, 14, 2, 14, 16, 6)     // etagere / support
    );

    private static final VoxelShape SHAPE_UPPER = Shapes.or(
            Block.box(3, 0, 3, 5, 14, 5),      // montant gauche
            Block.box(11, 0, 3, 13, 14, 5),    // montant droit
            Block.box(3, 12, 3, 13, 14, 5)     // barre haute
    );

    // ── Constructeur ─────────────────────────────────────────────────────────

    public EaselBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(HAS_CANVAS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, HAS_CANVAS);
    }

    // ── Forme & rendu ────────────────────────────────────────────────────────

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? SHAPE_LOWER : SHAPE_UPPER;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ── Placement double-hauteur ─────────────────────────────────────────────

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        // Verifie qu'il y a de la place au-dessus
        if (pos.getY() < level.getMaxBuildHeight() - 1
                && level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return this.defaultBlockState()
                    .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER)
                    .setValue(HAS_CANVAS, false);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        // Place le bloc superieur avec les memes proprietes
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    // ── EntityBlock — BE uniquement sur le bloc inferieur ─────────────────────

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new EaselBlockEntity(pos, state)
                : null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Retourne la position du bloc inferieur (celui qui a le BE). */
    private BlockPos getLowerPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** Recupere le EaselBlockEntity depuis n'importe quelle moitie. */
    @Nullable
    private EaselBlockEntity getEaselBE(Level level, BlockPos pos, BlockState state) {
        BlockPos lowerPos = getLowerPos(pos, state);
        return level.getBlockEntity(lowerPos) instanceof EaselBlockEntity easel ? easel : null;
    }

    /** Met a jour HAS_CANVAS sur les DEUX moities. */
    private void setCanvasOnBothHalves(Level level, BlockPos lowerPos, boolean hasCanvas) {
        BlockPos upperPos = lowerPos.above();
        BlockState lowerState = level.getBlockState(lowerPos);
        BlockState upperState = level.getBlockState(upperPos);

        if (lowerState.is(this)) {
            level.setBlock(lowerPos, lowerState.setValue(HAS_CANVAS, hasCanvas), 3);
        }
        if (upperState.is(this)) {
            level.setBlock(upperPos, upperState.setValue(HAS_CANVAS, hasCanvas), 3);
        }
    }

    // ── Interactions ─────────────────────────────────────────────────────────

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {

        EaselBlockEntity easel = getEaselBE(level, pos, state);
        if (easel == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        BlockPos lowerPos = getLowerPos(pos, state);

        // CAS 1 : Pose de la toile
        if (stack.is(ModItems.CANVAS.get()) && !easel.hasCanvas()) {
            if (!level.isClientSide()) {
                easel.setCanvas(true);
                easel.clearPixels();
                easel.setChanged();
                setCanvasOnBothHalves(level, lowerPos, true);
                level.sendBlockUpdated(lowerPos,
                        level.getBlockState(lowerPos), level.getBlockState(lowerPos), 3);
                if (!player.isCreative()) stack.shrink(1);
                player.displayClientMessage(
                        Component.translatable("message.paintcraft.canvas_placed"), true);
            }
            return ItemInteractionResult.CONSUME;
        }

        // CAS 2 : Ouverture de la GUI avec la palette
        if (stack.is(ModItems.PALETTE.get()) && easel.hasCanvas()) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                sp.openMenu(
                        new SimpleMenuProvider(
                                (id, inv, p) -> new PaintingMenu(id, inv, lowerPos),
                                Component.translatable("screen.paintcraft.painting")
                        ),
                        buf -> buf.writeBlockPos(lowerPos)
                );
            }
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {

        EaselBlockEntity easel = getEaselBE(level, pos, state);
        if (easel == null) return InteractionResult.PASS;

        if (easel.hasCanvas()) {
            if (!level.isClientSide()) {
                ItemStack drop = easel.hasPainting()
                        ? easel.getPaintingItemStack()
                        : new ItemStack(ModItems.CANVAS.get());

                easel.clearCanvas();
                easel.setChanged();

                BlockPos lowerPos = getLowerPos(pos, state);
                setCanvasOnBothHalves(level, lowerPos, false);
                level.sendBlockUpdated(lowerPos,
                        level.getBlockState(lowerPos), level.getBlockState(lowerPos), 3);

                if (!player.addItem(drop)) {
                    Containers.dropItemStack(level,
                            lowerPos.getX() + 0.5, lowerPos.getY() + 1.5,
                            lowerPos.getZ() + 0.5, drop);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ── Destruction double-bloc ──────────────────────────────────────────────

    /**
     * Quand le joueur casse une moitie, l'autre est detruite aussi.
     * Flag 35 = UPDATE_NEIGHBORS | UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS
     * Empeche le double drop du bloc (seule la moitie cassee directement droppe).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);

        if (otherState.is(this) && otherState.getValue(HALF) != half) {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Drop la toile/le tableau peint quand le chevalet est detruit.
     * Uniquement depuis le bloc inferieur (qui a le BE) pour eviter le double drop.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof EaselBlockEntity easel && easel.hasCanvas()) {
                    ItemStack drop = easel.hasPainting()
                            ? easel.getPaintingItemStack()
                            : new ItemStack(ModItems.CANVAS.get());
                    Containers.dropItemStack(level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Si l'autre moitie disparait (eau, piston, etc.), cette moitie se casse aussi.
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction requiredDir = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;

        if (direction == requiredDir) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
