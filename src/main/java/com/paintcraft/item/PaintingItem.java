package com.paintcraft.item;

import com.paintcraft.data.PaintingData;
import com.paintcraft.entity.ModEntityTypes;
import com.paintcraft.entity.WallPaintingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Tableau peint — resultat d'une session de peinture.
 *
 * MC 1.21 — Stockage via DataComponent (ModDataComponents.PAINTING_DATA)
 * Le record PaintingData encapsule les pixels (int[256] ARGB) avec son propre
 * Codec et StreamCodec, ce qui remplace l'ancien stockage CustomData/NBT.
 *
 * Pose murale (clic droit sur une face verticale d'un bloc) :
 *  1. Verifie que la face est verticale et que le tableau est peint
 *  2. Cree un WallPaintingEntity avec les pixels
 *  3. Valide via entity.survives() et ajoute au monde
 */
public class PaintingItem extends Item {

    public static final int CANVAS_SIZE = PaintingData.CANVAS_SIZE;
    public static final int PIXEL_COUNT = PaintingData.PIXEL_COUNT;

    public PaintingItem(Properties properties) {
        super(properties);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Cree un ItemStack "tableau peint" avec les pixels embarques dans le
     * DataComponent PAINTING_DATA.
     */
    public static ItemStack createPainting(int[] pixels) {
        ItemStack stack = new ItemStack(ModItems.PAINTING_ITEM.get());
        stack.set(ModDataComponents.PAINTING_DATA.get(), new PaintingData(pixels.clone()));
        return stack;
    }

    // ── Lecture du composant ──────────────────────────────────────────────────

    /** Retourne la PaintingData ou EMPTY si absente. */
    public static PaintingData getPaintingData(ItemStack stack) {
        PaintingData data = stack.get(ModDataComponents.PAINTING_DATA.get());
        return data != null ? data : PaintingData.EMPTY;
    }

    /** Extrait les pixels (copie defensive). */
    public static int[] getPixels(ItemStack stack) {
        return getPaintingData(stack).copyPixels();
    }

    /** Retourne true si le tableau a au moins un pixel peint. */
    public static boolean hasPaintingData(ItemStack stack) {
        PaintingData data = stack.get(ModDataComponents.PAINTING_DATA.get());
        return data != null && data.isPainted();
    }

    // ── Pose sur mur ──────────────────────────────────────────────────────────

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction clickedFace = context.getClickedFace();

        // 1. On ne pose pas sur une face horizontale (haut/bas d'un bloc)
        if (clickedFace.getAxis() == Direction.Axis.Y) {
            return InteractionResult.PASS;
        }

        // 2. Le tableau doit etre peint
        ItemStack stack = context.getItemInHand();
        if (!hasPaintingData(stack)) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        // 3. Cote client : succes visuel uniquement (vraie logique cote serveur)
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 4. (Serveur) Espace en avant du mur libre ?
        BlockPos inFrontPos = clickedPos.relative(clickedFace);
        BlockState inFrontState = level.getBlockState(inFrontPos);
        if (!inFrontState.canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        // 5. (Serveur) Creation et validation de l'entite
        WallPaintingEntity painting = new WallPaintingEntity(
                ModEntityTypes.WALL_PAINTING.get(),
                level,
                clickedPos,
                clickedFace
        );
        painting.setPixels(getPixels(stack));

        if (!painting.survives()) {
            return InteractionResult.FAIL;
        }

        // 6. (Serveur) Ajout au monde + son + consommation
        level.addFreshEntity(painting);
        painting.playPlacementSound();

        if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    // ── Comportement item ─────────────────────────────────────────────────────

    /** Effet de brillance si le tableau a ete peint. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return hasPaintingData(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        if (hasPaintingData(stack)) {
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.painted")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.place_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.paintcraft.painting_item.tooltip.blank")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
