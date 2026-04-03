package com.paintcraft.block;

import com.paintcraft.blockentity.EaselBlockEntity;
import com.paintcraft.blockentity.ModBlockEntities;
import com.paintcraft.item.ModItems;
import com.paintcraft.item.PaintingItem;
import com.paintcraft.menu.PaintingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
// NetworkHooks n'existe plus dans Forge 51 — on passe par IForgeServerPlayer
import org.jetbrains.annotations.Nullable;

/**
 * Bloc Chevalet — pièce centrale du mod.
 *
 * Implémente EntityBlock pour stocker les données de peinture
 * dans un EaselBlockEntity (persisté en NBT).
 *
 * Interactions :
 *  - Clic droit + Canvas (toile vierge) → pose la toile si vide
 *  - Clic droit + Palette              → ouvre la GUI de peinture si toile posée
 *  - Clic droit + main vide            → récupère toile/tableau peint
 */
public class EaselBlock extends Block implements EntityBlock {

    // ── BlockState ────────────────────────────────────────────────────────────

    /** Direction horizontale vers laquelle le chevalet fait face. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // ── Hitbox (VoxelShape) ───────────────────────────────────────────────────
    // Le chevalet est plus petit qu'un bloc complet (ne prend pas toute la place).
    // Valeurs : minX, minY, minZ, maxX, maxY, maxZ (en 1/16ème de bloc)
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    // ── Constructeur ──────────────────────────────────────────────────────────

    public EaselBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // État par défaut : face au NORD
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ── BlockState ────────────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Quand le joueur pose le bloc, le chevalet fait face au joueur
     * (direction opposée à celle du regard horizontal).
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ── Forme & rendu ─────────────────────────────────────────────────────────

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * RenderShape.MODEL → utilise le JSON model pour le rendu (pas une entité).
     * On gère le rendu côté ressources (JSON model).
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ── EntityBlock ───────────────────────────────────────────────────────────

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EaselBlockEntity(pos, state);
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    /**
     * Appelé quand le joueur clique droit sur le chevalet EN TENANT un item.
     *
     * Retourne ItemInteractionResult (MC 1.21+) :
     *  - CONSUME                         → action effectuée, consomme l'interaction
     *  - PASS_TO_DEFAULT_BLOCK_INTERACTION → passe à useWithoutItem()
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof EaselBlockEntity easel)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // ── CAS 1 : Pose de la toile ──────────────────────────────────────────
        if (stack.is(ModItems.CANVAS.get()) && !easel.hasCanvas()) {
            if (!level.isClientSide()) {
                easel.setCanvas(true);
                easel.clearPixels();
                easel.setChanged();
                // Notifie les clients du changement d'état (pour le rendu)
                level.sendBlockUpdated(pos, state, state, 3);
                // Consomme une toile de la main du joueur (sauf en créatif)
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                player.displayClientMessage(
                        Component.translatable("message.paintcraft.canvas_placed"), true);
            }
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }

        // ── CAS 2 : Ouverture de la GUI avec la palette ───────────────────────
        if (stack.is(ModItems.PALETTE.get()) && easel.hasCanvas()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                // Forge 51 (MC 1.21) : IForgeServerPlayer.openMenu() remplace NetworkHooks.openScreen()
                // Le 2e lambda écrit les données extra (BlockPos) dans le FriendlyByteBuf
                // qui sera lu par le constructeur client de PaintingMenu.
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                // Constructeur serveur : reçoit la BlockPos directement
                                (id, inv, p) -> new PaintingMenu(id, inv, pos),
                                Component.translatable("screen.paintcraft.painting")
                        ),
                        buf -> buf.writeBlockPos(pos)
                );
            }
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }

        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Appelé quand le joueur clique droit sur le chevalet SANS item en main.
     * Permet de récupérer la toile (vierge ou peinte).
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof EaselBlockEntity easel)) return InteractionResult.PASS;

        if (easel.hasCanvas()) {
            if (!level.isClientSide()) {
                // Si la toile a été peinte → donne un tableau peint avec NBT
                // Sinon → rend la toile vierge
                ItemStack drop = easel.hasPainting()
                        ? easel.getPaintingItemStack()
                        : new ItemStack(ModItems.CANVAS.get());

                easel.clearCanvas();
                easel.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);

                // Ajoute l'item à l'inventaire, ou le jette si plein
                if (!player.addItem(drop)) {
                    Containers.dropItemStack(level,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, drop);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ── Destruction du bloc ───────────────────────────────────────────────────

    /**
     * Quand le bloc est détruit, on récupère la toile/le tableau peint
     * pour ne pas perdre le travail du joueur.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        // Exécuté uniquement si c'est un vrai changement de bloc
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof EaselBlockEntity easel && easel.hasCanvas()) {
                ItemStack drop = easel.hasPainting()
                        ? easel.getPaintingItemStack()
                        : new ItemStack(ModItems.CANVAS.get());
                Containers.dropItemStack(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            }
        }
        // IMPORTANT : toujours appeler super pour que Forge nettoie le BlockEntity
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
